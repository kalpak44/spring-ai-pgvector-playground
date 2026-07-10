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
import static online.pavelusanli.advisors.expansion.ExpansionQueryAdvisor.ENRICHED_QUESTIONS;

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
    @SuppressWarnings("unchecked")
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String originalQuestion = chatClientRequest.prompt().getUserMessage().getText();

        List<String> queries;
        Object enrichedList = chatClientRequest.context().get(ENRICHED_QUESTIONS);
        if (enrichedList instanceof List<?> list && !list.isEmpty()) {
            queries = (List<String>) list;
        } else {
            String single = chatClientRequest.context()
                    .getOrDefault(ENRICHED_QUESTION, originalQuestion).toString();
            queries = List.of(single);
        }

        List<Document> documents = hybridSearchService.search(queries, topK);

        if (documents.isEmpty()) {
            return chatClientRequest;
        }

        AtomicInteger idx = new AtomicInteger(1);
        StringBuilder context = new StringBuilder();
        for (Document doc : documents) {
            Map<String, Object> meta = doc.getMetadata();
            String sourceName  = meta.getOrDefault("data_source_name", "").toString();
            String sourceFile  = meta.getOrDefault("source_file",      "").toString();
            String sourceUrl   = meta.getOrDefault("sourceUrl",         "").toString();
            String crawledAt   = meta.getOrDefault("crawledAt",         "").toString();

            String article  = meta.getOrDefault("article",  "").toString();
            String topic    = meta.getOrDefault("topic",    "").toString();

            context.append("[").append(idx.getAndIncrement()).append("] ");
            if (!sourceName.isBlank()) context.append(sourceName);
            if (!crawledAt.isBlank()) {
                context.append(" (checked: ").append(crawledAt, 0, Math.min(10, crawledAt.length())).append(")");
            }
            context.append(" — ");
            context.append(sourceUrl.isBlank() ? sourceFile : sourceUrl);
            if (!article.isBlank() || !topic.isBlank()) {
                context.append("\n    ");
                if (!article.isBlank()) context.append(article);
                if (!article.isBlank() && !topic.isBlank()) context.append(" · ");
                if (!topic.isBlank()) context.append(topic);
            }
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