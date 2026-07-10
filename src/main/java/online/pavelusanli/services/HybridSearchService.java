package online.pavelusanli.services;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int RRF_K = 60;
    private static final int CANDIDATE_COUNT = 50;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<Document> search(String query, int topK) {
        List<Document> dense  = denseSearch(query);
        List<Document> sparse = sparseSearch(query);
        log.debug("Hybrid search: {} dense + {} sparse candidates → topK={}", dense.size(), sparse.size(), topK);
        return reciprocalRankFusion(dense, sparse, topK);
    }

    private List<Document> denseSearch(String query) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(CANDIDATE_COUNT).build());
        } catch (Exception e) {
            log.warn("Dense search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> sparseSearch(String query) {
        try {
            return jdbcTemplate.query(
                    "SELECT id, content, metadata " +
                    "FROM vector_store " +
                    "WHERE fts @@ plainto_tsquery('english', ?) " +
                    "ORDER BY ts_rank(fts, plainto_tsquery('english', ?)) DESC " +
                    "LIMIT ?",
                    (rs, rowNum) -> {
                        String id      = rs.getString("id");
                        String content = rs.getString("content");
                        String metaRaw = rs.getString("metadata");
                        return new Document(id, content, parseMetadata(metaRaw));
                    },
                    query, query, CANDIDATE_COUNT);
        } catch (Exception e) {
            log.warn("Sparse FTS search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> reciprocalRankFusion(List<Document> dense, List<Document> sparse, int topK) {
        Map<String, Double>   scores = new LinkedHashMap<>();
        Map<String, Document> docs   = new LinkedHashMap<>();

        for (int i = 0; i < dense.size(); i++) {
            Document d = dense.get(i);
            scores.merge(d.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
            docs.put(d.getId(), d);
        }
        for (int i = 0; i < sparse.size(); i++) {
            Document d = sparse.get(i);
            scores.merge(d.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
            docs.putIfAbsent(d.getId(), d);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docs.get(e.getKey()))
                .toList();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}