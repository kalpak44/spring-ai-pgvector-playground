package online.pavelusanli.services.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.transformer.splitter.TextSplitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class AiChunkingSplitter extends TextSplitter {

    private static final int MAX_CHARS          = 30_000;
    private static final int MIN_SEGMENT_CHARS  = MAX_CHARS / 2;
    private static final int FALLBACK_MAX_CHARS = 3_000;
    private static final int MAX_RETRIES        = 2;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // Bulgarian legal structural markers — preferred split points between segments
    private static final String[] ARTICLE_MARKERS = {
        "\n\nЧл.", "\n\n§", "\n\nРаздел", "\n\nГлава",
        "\n\nДОПЪЛНИТЕЛНИ", "\n\nПРЕХОДНИ", "\n\nЗАКЛЮЧИТЕЛНИ", "\n\nЗАБЕЛЕЖКА"
    };

    private static final String SYSTEM_PROMPT =
            "You are a legal document analyst specialised in Bulgarian legislation. " +
            "Your only job is to read a raw legal text, discard noise, and return a structured JSON " +
            "representation of its semantic chunks. " +
            "You MUST respond with a single valid JSON object and nothing else — " +
            "no explanations, no markdown fences, no preamble, no emojis.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiChunkingSplitter(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(OllamaChatOptions.builder().temperature(0.0).format("json"))
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .flatMap(doc -> chunk(doc).stream())
                .toList();
    }

    @Override
    protected List<String> splitText(String text) {
        return List.of(text); // not used — apply() is overridden
    }

    private List<Document> chunk(Document source) {
        String text = source.getText();
        if (text == null || text.isBlank()) return List.of();

        List<String> segments = text.length() > MAX_CHARS ? segmentText(text) : List.of(text);
        if (segments.size() > 1) {
            log.debug("Large document ({} chars) split into {} segments for AI chunking",
                    text.length(), segments.size());
        }

        return segments.stream()
                .flatMap(seg -> chunkSegment(seg, source.getMetadata()).stream())
                .toList();
    }

    /**
     * Splits text that exceeds MAX_CHARS into segments at Bulgarian legal structural
     * boundaries (Чл., §, Раздел, Глава, etc.) so no article is ever cut mid-text.
     */
    private static List<String> segmentText(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            if (start + MAX_CHARS >= text.length()) {
                String tail = text.substring(start).strip();
                if (!tail.isBlank()) segments.add(tail);
                break;
            }
            int end = findSplitPoint(text, start, start + MAX_CHARS);
            String seg = text.substring(start, end).strip();
            if (!seg.isBlank()) segments.add(seg);
            start = end;
        }
        return segments;
    }

    /**
     * Finds the best split point within [start, maxEnd] — prefers an article/section
     * boundary, falls back to the last paragraph break, then hard-cuts at maxEnd.
     * Refuses to make a segment smaller than MIN_SEGMENT_CHARS.
     */
    private static int findSplitPoint(String text, int start, int maxEnd) {
        int minEnd = start + MIN_SEGMENT_CHARS;
        for (String marker : ARTICLE_MARKERS) {
            int pos = text.lastIndexOf(marker, maxEnd - 1);
            if (pos >= minEnd) return pos; // split before the marker; it stays in the next segment
        }
        int paraBreak = text.lastIndexOf("\n\n", maxEnd - 1);
        if (paraBreak >= minEnd) return paraBreak;
        return maxEnd;
    }

    private List<Document> chunkSegment(String text, Map<String, Object> meta) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = chatClient.prompt()
                        .user(buildPrompt(text))
                        .call()
                        .content();
                log.debug("AI raw response (attempt {}):\n{}", attempt, response);
                List<AiChunk> chunks = parseChunks(response);
                if (!chunks.isEmpty()) {
                    log.debug("AI chunking: {} chunks (attempt {})", chunks.size(), attempt);
                    if (log.isDebugEnabled()) {
                        for (int i = 0; i < chunks.size(); i++) {
                            AiChunk c = chunks.get(i);
                            log.debug("  chunk[{}] article={} topic={} keywords={}\n---\n{}\n---",
                                    i, c.article(), c.topic(), c.keywords(), c.text());
                        }
                    }
                    return chunks.stream().map(c -> toDocument(c, meta)).toList();
                }
                log.warn("AI chunking returned no usable chunks on attempt {}{}", attempt,
                        attempt < MAX_RETRIES ? ", retrying…" : ", falling back");
            } catch (Exception e) {
                log.warn("AI chunking failed on attempt {} — {}{}", attempt, e.getMessage(),
                        attempt < MAX_RETRIES ? ", retrying…" : ", falling back");
            }
        }
        return fallbackSegment(text, meta);
    }

    private static List<Document> fallbackSegment(String text, Map<String, Object> meta) {
        return Arrays.stream(text.split("\n\n+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .flatMap(para -> para.length() > FALLBACK_MAX_CHARS
                        ? splitBySize(para, FALLBACK_MAX_CHARS).stream()
                        : Stream.of(para))
                .map(t -> new Document(t, meta))
                .toList();
    }

    private static List<String> splitBySize(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + maxChars, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > i) end = space;
            }
            String part = text.substring(i, end).trim();
            if (!part.isBlank()) parts.add(part);
            i = end;
        }
        return parts;
    }

    private static String buildPrompt(String text) {
        return """
                Analyse the following raw legal document text and produce high-quality semantic chunks \
                suitable for a RAG knowledge base.

                STEP 1 — CLEAN:
                Strip ALL of the following noise before you write any chunk text. \
                The "text" field must never contain any of these:
                • Publication / amendment history: lines or parenthetical blocks containing \
                "Обн. ДВ. бр.", "изм. ДВ. бр.", "доп. ДВ. бр.", "попр. ДВ. бр.", \
                or standalone "(ОБН. - ДВ, БР. … Г.)" / "(ИЗМ. - ДВ, …)" markers.
                • Effective-date lines ("В сила от …") unless embedded inside an article body.
                • Separator lines made of dashes: "---…" or "———…".
                • Dot rows representing omitted or classified content: ". . . . . . ." or "……".
                • Parliamentary signature / closing footer: any line matching \
                "Законът е приет от … Народно събрание", "подпечатан с държавния печат", \
                or similar ceremonial closing lines.
                • Browser / plugin error messages ("Adobe Flash Player", "In order to view …", etc.).
                • Repeated whitespace and page-navigation artefacts.
                The law title is fine to keep as context in the first chunk if it adds meaning.

                STEP 2 — SPLIT AND PRESERVE:
                Create one chunk per coherent legal provision. Good split points are:
                • Individual articles: "Чл. 1", "Чл. 2", …
                • Numbered paragraphs that stand alone: "§ 1", "§ 2", …
                • Named sections ("Раздел I", "Глава първа") when they contain introductory text.
                A chunk must be self-contained and meaningful out of context.
                Do NOT merge many articles into one chunk. Do NOT split a single article across chunks.
                If a chunk contains only noise after cleaning, omit it entirely.
                CRITICAL: the "text" field MUST contain the EXACT, VERBATIM wording of the provision \
                as it appears in the source — do NOT paraphrase, summarise, or rewrite anything. \
                Only remove the noise listed in STEP 1. Every word in "text" must appear in the original.

                STEP 3 — ENRICH:
                For every chunk fill in:
                • "article": the identifier as it appears in the text (e.g. "Чл. 5", "§ 3") or "" if none.
                • "topic": one short English phrase describing what this provision governs \
                (e.g. "definitions", "penalties", "scope of application").
                • "keywords": 3–6 Bulgarian keywords extracted from the chunk text.

                Return ONLY valid JSON — no other text, no markdown fences:
                {
                  "chunks": [
                    {
                      "text": "VERBATIM article text copied from source, cleaned of noise only",
                      "article": "Чл. 5",
                      "topic": "short English topic label",
                      "keywords": ["ключова1", "ключова2", "ключова3"]
                    }
                  ]
                }

                Document:
                """ + text;
    }

    private List<AiChunk> parseChunks(String response) {
        String json = extractJson(response);
        try {
            Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE);

            // Try expected schema first
            Object raw = root.get("chunks");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                List<AiChunk> chunks = new ArrayList<>();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) continue;
                    String chunkText = getString(map, "text");
                    if (chunkText == null || chunkText.isBlank()) continue;
                    chunks.add(new AiChunk(chunkText, getString(map, "article"),
                            getString(map, "topic"), getStringList(map, "keywords")));
                }
                if (!chunks.isEmpty()) return chunks;
            }

            // Fallback: walk the entire tree and collect any node that has a "text" field
            log.debug("Expected 'chunks' key not found — walking JSON tree for text nodes");
            List<AiChunk> collected = new ArrayList<>();
            collectTextNodes(root, collected);
            return collected;
        } catch (Exception e) {
            log.warn("Failed to parse AI chunk JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private void collectTextNodes(Object node, List<AiChunk> out) {
        if (node instanceof Map<?, ?> map) {
            String text = getString(map, "text");
            if (text != null && !text.isBlank()) {
                out.add(new AiChunk(text, getString(map, "article"),
                        getString(map, "topic"), getStringList(map, "keywords")));
            } else {
                map.values().forEach(v -> collectTextNodes(v, out));
            }
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectTextNodes(item, out));
        }
    }

    private static String extractJson(String response) {
        int start = response.indexOf('{');
        int end   = response.lastIndexOf('}');
        return (start >= 0 && end > start) ? response.substring(start, end + 1) : response;
    }

    private static Document toDocument(AiChunk chunk, Map<String, Object> parentMeta) {
        List<String> keywords = chunk.keywords() != null ? chunk.keywords() : List.of();
        String kwLine  = keywords.isEmpty() ? "" : String.join(", ", keywords);
        String content = kwLine.isBlank() ? chunk.text() : chunk.text() + "\n\nKeywords: " + kwLine;

        Map<String, Object> meta = new HashMap<>(parentMeta);
        if (chunk.article() != null && !chunk.article().isBlank()) meta.put("article",  chunk.article());
        if (chunk.topic()   != null && !chunk.topic().isBlank())   meta.put("topic",    chunk.topic());
        if (!keywords.isEmpty())                                    meta.put("keywords", keywords);

        return new Document(content, Map.copyOf(meta));
    }

    private static String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private static List<String> getStringList(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (!(val instanceof List<?> list)) return List.of();
        return list.stream().filter(o -> o instanceof String).map(o -> (String) o).toList();
    }

    private record AiChunk(String text, String article, String topic, List<String> keywords) {}
}