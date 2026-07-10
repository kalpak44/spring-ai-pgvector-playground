package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankerService {

    private static final int MIN_SCORE       = 4;
    private static final int MAX_PREVIEW     = 500;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ChatModel    chatModel;
    private final ObjectMapper objectMapper;

    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.isEmpty()) return candidates;
        if (candidates.size() <= topK) return candidates;

        String prompt = buildPrompt(query, candidates);
        try {
            String response = chatModel.call(prompt);
            List<Integer> ranked = parseRanking(response, candidates.size());
            List<Document> result = ranked.stream()
                    .limit(topK)
                    .map(candidates::get)
                    .toList();
            log.debug("Reranker: {} candidates → {} kept (topK={})", candidates.size(), result.size(), topK);
            return result;
        } catch (Exception e) {
            log.warn("Reranking failed, using RRF order: {}", e.getMessage());
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }

    private static String buildPrompt(String query, List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a relevance assessor for a Bulgarian legal knowledge base.\n");
        sb.append("Query: ").append(query).append("\n\n");
        sb.append("Score each excerpt: 0 = unrelated, 10 = directly answers or contains the referenced provision.\n");
        sb.append("Excerpts are in Bulgarian — assess semantic relevance regardless of language.\n\n");

        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> meta = docs.get(i).getMetadata();
            String article = meta.getOrDefault("article", "").toString().strip();
            String topic   = meta.getOrDefault("topic",   "").toString().strip();

            sb.append("[").append(i + 1).append("]");
            if (!article.isBlank()) sb.append(" ").append(article);
            if (!topic.isBlank())   sb.append(" (").append(topic).append(")");
            sb.append("\n");

            String preview = docs.get(i).getText().trim();
            if (preview.length() > MAX_PREVIEW) preview = preview.substring(0, MAX_PREVIEW) + "…";
            sb.append(preview).append("\n\n");
        }

        sb.append("Return ONLY valid JSON — no other text:\n");
        sb.append("{\"scores\": [{\"index\": 1, \"score\": 8}, {\"index\": 2, \"score\": 3}, ...]}");
        return sb.toString();
    }

    private List<Integer> parseRanking(String response, int count) {
        String json = extractJson(response);
        try {
            Map<String, Object> root = objectMapper.readValue(json, MAP_TYPE);
            Object raw = root.get("scores");
            if (!(raw instanceof List<?> list)) return defaultOrder(count);

            return list.stream()
                    .filter(item -> item instanceof Map<?, ?> m
                            && m.get("index") instanceof Number
                            && m.get("score") instanceof Number
                            && ((Number) m.get("score")).intValue() >= MIN_SCORE)
                    .sorted(Comparator.comparingInt(item -> -((Number) ((Map<?, ?>) item).get("score")).intValue()))
                    .map(item -> ((Number) ((Map<?, ?>) item).get("index")).intValue() - 1)
                    .filter(i -> i >= 0 && i < count)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse reranker response: {}", e.getMessage());
            return defaultOrder(count);
        }
    }

    private static List<Integer> defaultOrder(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    private static String extractJson(String response) {
        int start = response.indexOf('{');
        int end   = response.lastIndexOf('}');
        return (start >= 0 && end > start) ? response.substring(start, end + 1) : response;
    }
}