package online.pavelusanli;

import online.pavelusanli.advisors.expansion.ExpansionQueryAdvisor;
import online.pavelusanli.advisors.rag.RagAdvisor;
import online.pavelusanli.repo.ChatRepository;
import online.pavelusanli.services.PostgresChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringAiPgvectorPlaygroundApplication {

    private static final PromptTemplate SYSTEM_PROMPT = new PromptTemplate(
            """
            You are an AI assistant and expert on Spring Framework and RAG-based applications.
            Answer concisely and to the point, in first person.

            The question may be about an implication of a fact from the Context.
            ALWAYS connect: Context fact → question.

            No connection, even indirect = "I have not covered this topic."
            Connection found = answer it.
            """
    );

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatModel chatModel;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(
                        ExpansionQueryAdvisor.builder(chatModel).order(0).build(),
                        getHistoryAdvisor(1),
                        SimpleLoggerAdvisor.builder().order(2).build(),
                        RagAdvisor.build(vectorStore).order(3).build(),
                        SimpleLoggerAdvisor.builder().order(4).build())
                .defaultOptions(OllamaOptions.builder().temperature(0.3).topP(0.7).topK(20).repeatPenalty(1.1).build())
                .defaultSystem(SYSTEM_PROMPT.render())
                .build();
    }

/*  private Advisor getRagAdviser(int order) {
        return QuestionAnswerAdvisor.builder(vectorStore).promptTemplate(MY_PROMPT_TEMPLATE).searchRequest(
                SearchRequest.builder().topK(4).similarityThreshold(0.65).build()
        ).order(order).build();
    }*/

    private Advisor getHistoryAdvisor(int order) {
        return MessageChatMemoryAdvisor.builder(getChatMemory()).order(order).build();
    }

    private ChatMemory getChatMemory() {
        return PostgresChatMemory.builder()
                .maxMessages(8)
                .chatMemoryRepository(chatRepository)
                .build();
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SpringAiPgvectorPlaygroundApplication.class, args);
//        ChatClient chatClient = context.getBean(ChatClient.class);
//        System.out.println(chatClient.prompt().user("...").call().content());
    }
}