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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.util.Map;

@Slf4j
@Builder
public class ExpansionQueryAdvisor implements BaseAdvisor {

    private static final PromptTemplate template = PromptTemplate.builder()
            .template("""
                Rewrite the user question into a precise, self-contained search query.
                Rules:
                - Resolve pronouns and follow-up references ("that law", "this endpoint", "it") into explicit terms.
                - Expand abbreviations where the meaning is clear from context.
                - Keep all key terms from the original question.
                - Do NOT add terms not implied by the question.
                - Return only the rewritten query as a plain string. No explanation, no quotes.

                Question: {question}
                Rewritten query:
                """).build();

    public static final String ENRICHED_QUESTION = "ENRICHED_QUESTION";

    private ChatClient chatClient;

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel) {
        return new ExpansionQueryAdvisorBuilder().chatClient(ChatClient.builder(chatModel)
                .defaultOptions(OllamaChatOptions.builder().temperature(0.0).topK(1).topP(0.1).repeatPenalty(1.0))
                .build());
    }

    @Getter
    private final int order;

    @Override
    @NonNull
    public ChatClientRequest before(@NonNull ChatClientRequest chatClientRequest, @NonNull AdvisorChain advisorChain) {
        String userQuestion = chatClientRequest.prompt().getUserMessage().getText();
        if (userQuestion == null || userQuestion.isBlank()) {
            return chatClientRequest;
        }

        String enriched = chatClient
                .prompt()
                .user(template.render(Map.of("question", userQuestion)))
                .call()
                .content();

        if (enriched == null || enriched.isBlank()) {
            return chatClientRequest;
        }

        return chatClientRequest.mutate()
                .context(ENRICHED_QUESTION, enriched)
                .build();
    }

    @Override
    @NonNull
    public ChatClientResponse after(@NonNull ChatClientResponse chatClientResponse, @NonNull AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}