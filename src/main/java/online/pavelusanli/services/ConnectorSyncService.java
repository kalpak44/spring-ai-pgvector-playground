package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.DataSourceStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public void sync(Long dataSourceId) {
        DataSource ds = dataSourceRepository.findById(dataSourceId).orElseThrow();

        if (ds.getChunkingProfile() == null) {
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

            int totalChunks = 0;

            for (ConnectorClient.ConnectorDocument remote : remoteList) {
                Optional<LoadedDocument> existing =
                        documentRepository.findByDataSourceIdAndFilename(ds.getId(), remote.path());

                if (existing.isPresent() && existing.get().getContentHash().equals(remote.contentHash())) {
                    totalChunks += existing.get().getChunkCount();
                    continue;
                }

                ConnectorClient.ConnectorFetchResult fetched =
                        connectorClient.fetch(ds.getConnectorUrl(), ds.getConfig(), remote.id());
                if (fetched == null || fetched.rawContent() == null) continue;

                // Remove stale chunks for this path
                if (existing.isPresent()) {
                    deleteChunksForPath(ds.getId(), remote.path());
                    documentRepository.delete(existing.get());
                }

                Document doc = new Document(fetched.rawContent(), Map.of(
                        "data_source_id", String.valueOf(ds.getId()),
                        "data_source_name", ds.getName(),
                        "source_file", remote.path()
                ));
                List<Document> chunks = splitter.apply(List.of(doc));
                vectorStore.accept(chunks);

                documentRepository.save(LoadedDocument.builder()
                        .dataSourceId(ds.getId())
                        .filename(remote.path())
                        .contentHash(remote.contentHash())
                        .documentType("connector")
                        .chunkCount(chunks.size())
                        .build());

                totalChunks += chunks.size();
                log.debug("Synced {} → {} chunks", remote.path(), chunks.size());
            }

            // Remove chunks for documents no longer present in the connector
            documentRepository.findAllByDataSourceId(ds.getId()).stream()
                    .filter(ld -> !remotePaths.contains(ld.getFilename()))
                    .forEach(ld -> {
                        deleteChunksForPath(ds.getId(), ld.getFilename());
                        documentRepository.delete(ld);
                    });

            ds.setStatus(DataSourceStatus.IDLE);
            ds.setLastSyncedAt(LocalDateTime.now(ZoneOffset.UTC));
            ds.setChunkCount(totalChunks);
            ds.setErrorMessage(null);
            dataSourceRepository.save(ds);
            log.info("Sync complete for data source {} — {} chunks", ds.getName(), totalChunks);

        } catch (Exception e) {
            log.error("Sync failed for data source {}: {}", ds.getName(), e.getMessage(), e);
            markError(ds, e.getMessage());
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
}