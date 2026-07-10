package online.pavelusanli.advisors.rag;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.services.HybridSearchService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static online.pavelusanli.advisors.expansion.ExpansionQueryAdvisor.ENRICHED_QUESTION;

@Slf4j
@Builder
public class RagAdvisor implements BaseAdvisor {

    private static final int DEFAULT_TOP_K = 5;

    private HybridSearchService hybridSearchService;

    @Builder.Default
    private int topK = DEFAULT_TOP_K;

    @Getter
    private final int order;

    public static RagAdvisorBuilder build(HybridSearchService hybridSearchService) {
        return new RagAdvisorBuilder().hybridSearchService(hybridSearchService);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String originalQuestion = chatClientRequest.prompt().getUserMessage().getText();
        String queryToRag = chatClientRequest.context()
                .getOrDefault(ENRICHED_QUESTION, originalQuestion).toString();

        List<Document> documents = hybridSearchService.search(queryToRag, topK);

        if (documents.isEmpty()) {
            return chatClientRequest;
        }

        AtomicInteger idx = new AtomicInteger(1);
        StringBuilder context = new StringBuilder();
        for (Document doc : documents) {
            Map<String, Object> meta = doc.getMetadata();
            String sourceName = meta.getOrDefault("data_source_name", "").toString();
            String sourceFile = meta.getOrDefault("source_file", "").toString();
            context.append("[").append(idx.getAndIncrement()).append("] ");
            if (!sourceName.isBlank()) context.append(sourceName).append(" — ");
            if (!sourceFile.isBlank()) context.append(sourceFile);
            context.append("\n    \"").append(doc.getText().strip()).append("\"\n\n");
        }

        String augmented = context + "User question: " + originalQuestion;

        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmented))
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}