package online.pavelusanli.advisors.expansion;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Builder
public class ExpansionQueryAdvisor implements BaseAdvisor {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String PROMPT_TEMPLATE = """
            {context}For the following question about Bulgarian law, generate 3 alternative search \
            queries optimised for a legal knowledge base. Each query must:
            - Use precise Bulgarian legal terminology
            - Resolve follow-up references ("that law", "it", "the same article") using the conversation context
            - Expand abbreviations (e.g. ЗЗД → Закон за задълженията и договорите, КТ → Кодекс на труда)
            - If an article is referenced without a law name, include the law name when context implies it
            - Approach the question from a distinct angle to maximise retrieval coverage
            - Keep all key terms; do NOT invent facts not in the question

            Return ONLY valid JSON — no other text:
            {"queries": ["query1", "query2", "query3"]}

            Question: {question}
            """;

    /** Primary expanded query (first in list) — for backward compatibility. */
    public static final String ENRICHED_QUESTION  = "ENRICHED_QUESTION";
    /** All expanded queries as {@code List<String>}. */
    public static final String ENRICHED_QUESTIONS = "ENRICHED_QUESTIONS";

    private ChatClient chatClient;
    private ObjectMapper objectMapper;

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel, ObjectMapper objectMapper) {
        return new ExpansionQueryAdvisorBuilder()
                .objectMapper(objectMapper)
                .chatClient(ChatClient.builder(chatModel)
                        .defaultOptions(OllamaChatOptions.builder()
                                .temperature(0.0).topK(1).topP(0.1).repeatPenalty(1.0))
                        .build());
    }

    @Getter
    private final int order;

    @Override
    @NonNull
    public ChatClientRequest before(@NonNull ChatClientRequest chatClientRequest,
                                    @NonNull AdvisorChain advisorChain) {
        String userQuestion = chatClientRequest.prompt().getUserMessage().getText();
        if (userQuestion == null || userQuestion.isBlank()) {
            return chatClientRequest;
        }

        String context = recentContext(chatClientRequest);
        String contextSection = context.isBlank() ? "" : "Recent conversation:\n" + context + "\n\n";
        String prompt = PROMPT_TEMPLATE
                .replace("{context}", contextSection)
                .replace("{question}", userQuestion);

        try {
            String raw = chatClient.prompt().user(prompt).call().content();
            List<String> queries = parseQueries(raw, userQuestion);
            log.debug("Expanded '{}' → {} queries", userQuestion, queries.size());
            return chatClientRequest.mutate()
                    .context(ENRICHED_QUESTIONS, queries)
                    .context(ENRICHED_QUESTION,  queries.get(0))
                    .build();
        } catch (Exception e) {
            log.warn("Query expansion failed: {}", e.getMessage());
            return chatClientRequest;
        }
    }

    @Override
    @NonNull
    public ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse,
                                    @NonNull AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseQueries(String response, String fallback) {
        try {
            int start = response.indexOf('{');
            int end   = response.lastIndexOf('}');
            if (start < 0 || end <= start) return List.of(fallback);

            Map<String, Object> root = objectMapper.readValue(
                    response.substring(start, end + 1), MAP_TYPE);
            Object raw = root.get("queries");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                List<String> queries = ((List<Object>) list).stream()
                        .filter(o -> o instanceof String s && !s.isBlank())
                        .map(Object::toString)
                        .toList();
                if (!queries.isEmpty()) return queries;
            }
        } catch (Exception e) {
            log.warn("Failed to parse multi-query expansion response: {}", e.getMessage());
        }
        return List.of(fallback);
    }

    private static String recentContext(ChatClientRequest request) {
        List<String> lines = new ArrayList<>();
        request.prompt().getInstructions().forEach(msg -> {
            if (msg.getMessageType() == MessageType.USER) {
                String text = msg.getText().strip();
                if (!text.isBlank()) lines.add("User: " + text);
            } else if (msg.getMessageType() == MessageType.ASSISTANT) {
                String text = msg.getText().strip();
                if (text.length() > 150) text = text.substring(0, 150) + "…";
                if (!text.isBlank()) lines.add("Assistant: " + text);
            }
        });
        // Drop the last entry — it's the current question, already passed as {question}
        if (!lines.isEmpty() && lines.getLast().startsWith("User:")) {
            lines.removeLast();
        }
        int start = Math.max(0, lines.size() - 4);
        return String.join("\n", lines.subList(start, lines.size()));
    }
}