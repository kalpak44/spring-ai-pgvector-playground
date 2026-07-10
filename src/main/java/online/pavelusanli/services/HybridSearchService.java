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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private static final int    RRF_K                = 60;
    private static final int    CANDIDATE_COUNT       = 50;
    private static final int    RERANK_POOL           = 20;
    private static final double DENSE_WEIGHT          = 1.2;
    private static final double ARTICLE_WEIGHT        = 1.5;
    private static final double KEYWORD_WEIGHT        = 1.1;
    private static final double SIMILARITY_THRESHOLD  = 0.2;
    private static final int    MIN_KEYWORD_LEN       = 4;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    // Matches Bulgarian article references: "Чл. 5", "§ 3", "член 12", "параграф 2"
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?:чл\\.?|член|§|параграф)\\s*\\d+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Common Bulgarian and English stop words — too short or too generic to be useful as keyword lookups
    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "от", "за", "с", "по", "при", "към", "до", "като", "или",
            "но", "че", "се", "не", "да", "а", "е", "са", "бе", "ще", "би", "то",
            "го", "го", "ги", "му", "й", "им", "ни", "ви", "те", "си", "я", "той",
            "тя", "те", "ние", "вие", "аз", "кой", "кои", "кое", "кога", "как",
            "the", "of", "in", "and", "or", "to", "is", "are", "was", "for", "with"
    );

    private final VectorStore     vectorStore;
    private final JdbcTemplate    jdbcTemplate;
    private final ObjectMapper    objectMapper;
    private final RerankerService rerankerService;

    /**
     * Multi-query hybrid search: runs all queries through dense + sparse + article channels,
     * accumulates RRF scores across all queries, then reranks with the primary (first) query.
     */
    public List<Document> search(List<String> queries, int topK) {
        Map<String, Double>   scores = new LinkedHashMap<>();
        Map<String, Document> docs   = new LinkedHashMap<>();

        for (String query : queries) {
            accumulateRrf(denseSearch(query),   DENSE_WEIGHT,   scores, docs);
            accumulateRrf(sparseSearch(query),  1.0,            scores, docs);
            accumulateRrf(articleSearch(query), ARTICLE_WEIGHT, scores, docs);
            accumulateRrf(keywordSearch(query), KEYWORD_WEIGHT, scores, docs);
        }

        List<Document> fused = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(RERANK_POOL)
                .map(e -> docs.get(e.getKey()))
                .toList();

        String primaryQuery = queries.get(0);
        List<Document> result = rerankerService.rerank(primaryQuery, fused, topK);
        log.debug("Hybrid search: {} queries, {} candidates → {} fused → {} after rerank",
                queries.size(), scores.size(), fused.size(), result.size());
        return result;
    }

    public List<Document> search(String query, int topK) {
        return search(List.of(query), topK);
    }

    private List<Document> denseSearch(String query) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(CANDIDATE_COUNT)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build());
        } catch (Exception e) {
            log.warn("Dense search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> sparseSearch(String query) {
        try {
            return jdbcTemplate.query(
                    // websearch_to_tsquery handles phrases (quoted), OR, and implicit AND.
                    // ts_rank_cd (cover density) scores short chunks more fairly than ts_rank.
                    "SELECT id, content, metadata " +
                    "FROM vector_store " +
                    "WHERE fts @@ websearch_to_tsquery('simple', ?) " +
                    "ORDER BY ts_rank_cd(fts, websearch_to_tsquery('simple', ?)) DESC " +
                    "LIMIT ?",
                    (rs, rowNum) -> new Document(
                            rs.getString("id"),
                            rs.getString("content"),
                            parseMetadata(rs.getString("metadata"))),
                    query, query, CANDIDATE_COUNT);
        } catch (Exception e) {
            log.warn("Sparse FTS search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Targeted metadata lookup when the query contains explicit article references.
     * Bypasses embedding/FTS for high-precision article lookups.
     */
    private List<Document> articleSearch(String query) {
        Matcher m = ARTICLE_PATTERN.matcher(query);
        List<String> patterns = new ArrayList<>();
        while (m.find()) {
            String ref = m.group().trim();
            if (!patterns.contains(ref)) patterns.add(ref);
        }
        if (patterns.isEmpty()) return List.of();

        List<Document> results = new ArrayList<>();
        for (String pattern : patterns) {
            try {
                results.addAll(jdbcTemplate.query(
                        "SELECT id, content, metadata " +
                        "FROM vector_store " +
                        "WHERE metadata->>'article' ILIKE ? " +
                        "LIMIT 10",
                        (rs, rowNum) -> new Document(
                                rs.getString("id"),
                                rs.getString("content"),
                                parseMetadata(rs.getString("metadata"))),
                        pattern + "%"));
            } catch (Exception e) {
                log.warn("Article metadata search failed for '{}': {}", pattern, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Searches the AI-generated keyword metadata for terms extracted from the query.
     * Uses prefix ILIKE (термин%) to catch Bulgarian morphological variants:
     * e.g. "задълж" matches both stored "задължение" and "задължения".
     * Documents ranking higher in the result set matched more search terms.
     */
    private List<Document> keywordSearch(String query) {
        List<String> terms = extractQueryTerms(query);
        if (terms.isEmpty()) return List.of();

        List<Document> results = new ArrayList<>();
        for (String term : terms) {
            try {
                results.addAll(jdbcTemplate.query(
                        "SELECT id, content, metadata " +
                        "FROM vector_store " +
                        "WHERE EXISTS (" +
                        "    SELECT 1 FROM jsonb_array_elements_text(metadata->'keywords') kw" +
                        "    WHERE kw ILIKE ?" +
                        ") " +
                        "LIMIT 8",
                        (rs, rowNum) -> new Document(
                                rs.getString("id"),
                                rs.getString("content"),
                                parseMetadata(rs.getString("metadata"))),
                        term + "%"));
            } catch (Exception e) {
                log.warn("Keyword metadata search failed for '{}': {}", term, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Splits the query into meaningful tokens suitable for keyword prefix matching.
     * Strips punctuation, lower-cases, removes stop words and very short tokens.
     * Longer tokens are truncated to a stem prefix (first 5 chars) to improve
     * morphological recall against the stored Bulgarian keywords.
     */
    private static List<String> extractQueryTerms(String query) {
        return Arrays.stream(query.split("[\\s,;.!?\"'()\\-/\\\\]+"))
                .map(String::toLowerCase)
                .filter(t -> t.length() >= MIN_KEYWORD_LEN && !STOP_WORDS.contains(t))
                .map(t -> t.length() > 5 ? t.substring(0, 5) : t)
                .distinct()
                .limit(8)
                .toList();
    }

    private static void accumulateRrf(List<Document> ranked, double weight,
                                      Map<String, Double> scores, Map<String, Document> docs) {
        for (int i = 0; i < ranked.size(); i++) {
            Document d = ranked.get(i);
            scores.merge(d.getId(), weight / (RRF_K + i + 1), Double::sum);
            docs.putIfAbsent(d.getId(), d);
        }
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