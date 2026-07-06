package online.pavelusanli.advisors.rag;

import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static online.pavelusanli.advisors.expansion.ExpansionQueryAdvisor.ENRICHED_QUESTION;

@Slf4j
@Builder
public class RagAdvisor implements BaseAdvisor {

    private VectorStore vectorStore;

    @Builder.Default
    private SearchRequest searchRequest = SearchRequest.builder().topK(5).similarityThreshold(0.5).build();

    @Getter
    private final int order;

    public static RagAdvisorBuilder build(VectorStore vectorStore) {
        return new RagAdvisorBuilder().vectorStore(vectorStore);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String originalUserQuestion = chatClientRequest.prompt().getUserMessage().getText();
        String queryToRag = chatClientRequest.context().getOrDefault(ENRICHED_QUESTION, originalUserQuestion).toString();

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.from(searchRequest).query(queryToRag).topK(searchRequest.getTopK() * 2).build());

        if (documents.isEmpty()) {
            return chatClientRequest;
        }

        BM25RerankEngine rerankEngine = BM25RerankEngine.builder().build();
        documents = rerankEngine.rerank(documents, queryToRag, searchRequest.getTopK());

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

        String augmented = context + "User question: " + originalUserQuestion;

        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmented))
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }
}