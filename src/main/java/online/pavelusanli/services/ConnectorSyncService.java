package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.DataSourceStatus;
import online.pavelusanli.model.common.SyncEventType;
import online.pavelusanli.model.entity.DataSource;
import online.pavelusanli.model.entity.LoadedDocument;
import online.pavelusanli.repo.DataSourceRepository;
import online.pavelusanli.repo.DocumentRepository;
import online.pavelusanli.services.chunking.ChunkingStrategyFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorSyncService {

    private final ConnectorClient connectorClient;
    private final VectorStore vectorStore;
    private final DataSourceRepository dataSourceRepository;
    private final DocumentRepository documentRepository;
    private final ChunkingStrategyFactory chunkingStrategyFactory;
    private final JdbcTemplate jdbcTemplate;
    private final SyncProgressService syncProgressService;

    public void sync(Long dataSourceId) {
        DataSource ds = dataSourceRepository.findById(dataSourceId).orElseThrow();
        String runId = UUID.randomUUID().toString();

        if (ds.getChunkingProfile() == null) {
            info(dataSourceId, runId, SyncEventType.SYNC_ERROR, "ERROR",
                    "No chunking profile assigned — cannot sync.", null);
            syncProgressService.completeEmitters(dataSourceId);
            markError(ds, "No chunking profile assigned — cannot sync.");
            return;
        }

        try {
            TextSplitter splitter = chunkingStrategyFactory.get(ds.getChunkingProfile());
            List<ConnectorClient.ConnectorDocument> remoteList =
                    connectorClient.documents(ds.getConnectorUrl(), ds.getConfig());
            Set<String> remotePaths = remoteList.stream()
                    .map(ConnectorClient.ConnectorDocument::path)
                    .collect(Collectors.toSet());

            info(dataSourceId, runId, SyncEventType.SYNC_STARTED, "INFO",
                    "Sync started — " + remoteList.size() + " document(s) in connector",
                    Map.of("documentCount", remoteList.size()));

            int totalChunks = 0;
            int processed   = 0;
            long loopStart  = System.currentTimeMillis();

            for (ConnectorClient.ConnectorDocument remote : remoteList) {
                try {
                    totalChunks += syncDocument(ds, remote, splitter, dataSourceId, runId);
                } catch (Exception e) {
                    log.warn("Skipping document {} — {}", remote.path(), e.getMessage());
                    info(dataSourceId, runId, SyncEventType.DOC_FETCHED, "WARN",
                            "Skipped (error): " + remote.path() + " — " + e.getMessage(),
                            Map.of("path", remote.path(), "error", String.valueOf(e.getMessage())));
                }
                processed++;
                int remaining = remoteList.size() - processed;
                if (remaining > 0 && log.isDebugEnabled()) {
                    long avgMs = (System.currentTimeMillis() - loopStart) / processed;
                    log.debug("Sync {}/{} — avg {}s/doc — ETA ~{}",
                            processed, remoteList.size(),
                            avgMs / 1000,
                            formatDuration(avgMs * remaining));
                }
            }

            // Remove chunks for documents no longer present in the connector
            documentRepository.findAllByDataSourceId(ds.getId()).stream()
                    .filter(ld -> !remotePaths.contains(ld.getFilename()))
                    .forEach(ld -> {
                        deleteChunksForPath(ds.getId(), ld.getFilename());
                        documentRepository.delete(ld);
                        info(dataSourceId, runId, SyncEventType.DOC_REMOVED, "INFO",
                                "Removed stale: " + ld.getFilename(),
                                Map.of("path", ld.getFilename()));
                    });

            ds.setStatus(DataSourceStatus.IDLE);
            ds.setLastSyncedAt(LocalDateTime.now(ZoneOffset.UTC));
            ds.setChunkCount(totalChunks);
            ds.setErrorMessage(null);
            dataSourceRepository.save(ds);

            info(dataSourceId, runId, SyncEventType.SYNC_COMPLETE, "INFO",
                    "Sync complete — " + totalChunks + " chunk(s) total",
                    Map.of("totalChunks", totalChunks));
            log.info("Sync complete for data source {} — {} chunks", ds.getName(), totalChunks);

        } catch (Exception e) {
            log.error("Sync failed for data source {}: {}", ds.getName(), e.getMessage(), e);
            info(dataSourceId, runId, SyncEventType.SYNC_ERROR, "ERROR",
                    "Sync failed: " + e.getMessage(),
                    Map.of("error", String.valueOf(e.getMessage())));
            markError(ds, e.getMessage());
        } finally {
            syncProgressService.completeEmitters(dataSourceId);
        }
    }

    private int syncDocument(DataSource ds, ConnectorClient.ConnectorDocument remote,
                             TextSplitter splitter, Long dataSourceId, String runId) {
        Optional<LoadedDocument> existing =
                documentRepository.findByDataSourceIdAndFilename(ds.getId(), remote.path());

        if (existing.isPresent() && existing.get().getContentHash().equals(remote.contentHash())) {
            info(dataSourceId, runId, SyncEventType.DOC_SKIPPED, "INFO",
                    "Skipped — unchanged: " + remote.path(),
                    Map.of("path", remote.path()));
            return existing.get().getChunkCount();
        }

        ConnectorClient.ConnectorFetchResult fetched =
                connectorClient.fetch(ds.getConnectorUrl(), ds.getConfig(), remote.id());
        if (fetched == null || fetched.rawContent() == null) return 0;

        info(dataSourceId, runId, SyncEventType.DOC_FETCHED, "INFO",
                "Fetched: " + remote.path() + " (" + fetched.rawContent().length() + " chars)",
                Map.of("path", remote.path(), "contentLength", fetched.rawContent().length()));

        if (existing.isPresent()) {
            deleteChunksForPath(ds.getId(), remote.path());
            documentRepository.delete(existing.get());
        }

        Map<String, Object> docMeta = new HashMap<>();
        docMeta.put("data_source_id", String.valueOf(ds.getId()));
        docMeta.put("data_source_name", ds.getName());
        docMeta.put("source_file", remote.path());
        if (fetched.metadata() != null) {
            fetched.metadata().forEach((k, v) -> { if (v != null) docMeta.put(k, v); });
        }
        Document doc = new Document(fetched.rawContent(), Map.copyOf(docMeta));
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.accept(chunks);

        info(dataSourceId, runId, SyncEventType.DOC_CHUNKED, "INFO",
                "Chunked: " + remote.path() + " → " + chunks.size() + " chunk(s)",
                buildChunkDetails(remote.path(), chunks));

        documentRepository.save(LoadedDocument.builder()
                .dataSourceId(ds.getId())
                .filename(remote.path())
                .contentHash(remote.contentHash())
                .documentType("connector")
                .chunkCount(chunks.size())
                .build());

        log.debug("Synced {} → {} chunks", remote.path(), chunks.size());
        return chunks.size();
    }

    private Map<String, Object> buildChunkDetails(String path, List<Document> chunks) {
        List<String> keywords = chunks.stream()
                .flatMap(c -> {
                    Object kw = c.getMetadata().get("keywords");
                    if (kw instanceof List<?> list) return list.stream().map(Object::toString);
                    if (kw instanceof String s && !s.isBlank())
                        return Arrays.stream(s.split(",\\s*"));
                    return Stream.empty();
                })
                .distinct()
                .limit(10)
                .toList();

        List<String> articles = chunks.stream()
                .map(c -> (String) c.getMetadata().get("article"))
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .limit(5)
                .toList();

        Map<String, Object> details = new HashMap<>();
        details.put("path", path);
        details.put("chunkCount", chunks.size());
        if (!keywords.isEmpty()) details.put("keywords", keywords);
        if (!articles.isEmpty()) details.put("articles", articles);
        return details;
    }

    private void info(Long dataSourceId, String runId, SyncEventType type, String level,
                      String message, Map<String, Object> details) {
        try {
            syncProgressService.emit(dataSourceId, runId, type, level, message, details);
        } catch (Exception e) {
            log.warn("Failed to emit sync event {}: {}", type, e.getMessage());
        }
    }

    private void deleteChunksForPath(Long dataSourceId, String path) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'data_source_id' = ? AND metadata->>'source_file' = ?",
                String.valueOf(dataSourceId), path);
    }

    private void markError(DataSource ds, String message) {
        ds.setStatus(DataSourceStatus.ERROR);
        ds.setErrorMessage(message);
        dataSourceRepository.save(ds);
    }

    private static String formatDuration(long ms) {
        long s = ms / 1000;
        if (s < 60)   return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }
}