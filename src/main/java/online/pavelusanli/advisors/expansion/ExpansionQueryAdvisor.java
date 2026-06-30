package online.pavelusanli.advisors.expansion;

import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaOptions;

import java.util.Map;

@Builder
public class ExpansionQueryAdvisor implements BaseAdvisor {

    private static final PromptTemplate template = PromptTemplate.builder()
            .template("""
                Instruction: Expand the search query by adding the most relevant terms.

                SPRING FRAMEWORK SPECIALIZATION:
                - Spring bean lifecycle: constructor → BeanPostProcessor → PostConstruct → proxy → ContextListener
                - Technologies: Dynamic Proxy, CGLib, reflection, annotations, XML configuration
                - Components: BeanFactory, ApplicationContext, BeanDefinition, MBean, JMX
                - Patterns: dependency injection, AOP, profiling, method interception

                RULES:
                1. Keep ALL words from the original question
                2. Add AT MOST FIVE of the most important terms
                3. Choose the most specific and relevant words
                4. Output a plain space-separated list of words

                SELECTION STRATEGY:
                - Priority: specialized terms
                - Avoid generic words
                - Focus on key concepts

                EXAMPLES:
                "what is spring" → "what is spring framework Java"
                "how to create a file" → "how to create a file document program"

                Question: {question}
                Expanded query:
                """).build();

    public static final String ENRICHED_QUESTION = "ENRICHED_QUESTION";
    public static final String ORIGINAL_QUESTION = "ORIGINAL_QUESTION";
    public static final String EXPANSION_RATIO = "EXPANSION_RATIO";

    private ChatClient chatClient;

    public static ExpansionQueryAdvisorBuilder builder(ChatModel chatModel) {
        return new ExpansionQueryAdvisorBuilder().chatClient(ChatClient.builder(chatModel)
                .defaultOptions(OllamaOptions.builder().temperature(0.0).topK(1).topP(0.1).repeatPenalty(1.0).build())
                .build());
    }

    @Getter
    private final int order;

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String userQuestion = chatClientRequest.prompt().getUserMessage().getText();
        String enrichedQuestion = chatClient
                .prompt()
                .user(template.render(Map.of("question", userQuestion)))
                .call()
                .content();

        double ratio = enrichedQuestion.length() / (double) userQuestion.length();

        return chatClientRequest.mutate()
                .context(ORIGINAL_QUESTION, userQuestion)
                .context(ENRICHED_QUESTION, enrichedQuestion)
                .context(EXPANSION_RATIO, ratio)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}