package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.common.DataSourceStatus;
import online.pavelusanli.model.entity.ChunkingProfile;
import online.pavelusanli.model.entity.DataSource;
import online.pavelusanli.repo.ChunkingProfileRepository;
import online.pavelusanli.repo.DataSourceRepository;
import online.pavelusanli.repo.DocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class DataSourceService {

    private final DataSourceRepository repo;
    private final ChunkingProfileRepository chunkingProfileRepo;
    private final DocumentRepository documentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<DataSource> findAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public DataSource findById(Long id) {
        return repo.findById(id).orElseThrow();
    }

    public DataSource create(String name, String connectorUrl, String connectorName,
                             Map<String, String> config, Long chunkingProfileId) {
        ChunkingProfile profile = chunkingProfileId != null
                ? chunkingProfileRepo.findById(chunkingProfileId).orElse(null)
                : null;
        return repo.save(DataSource.builder()
                .name(name)
                .connectorUrl(connectorUrl)
                .connectorName(connectorName)
                .config(config)
                .chunkingProfile(profile)
                .build());
    }

    public boolean markSyncing(Long id) {
        DataSource ds = repo.findById(id).orElseThrow();
        if (ds.getStatus() == DataSourceStatus.SYNCING) {
            return false;
        }
        ds.setStatus(DataSourceStatus.SYNCING);
        repo.save(ds);
        return true;
    }

    public void delete(Long id) {
        jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'data_source_id' = ?",
                String.valueOf(id));
        documentRepository.deleteAllByDataSourceId(id);
        repo.deleteById(id);
    }
}