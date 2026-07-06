package online.pavelusanli.repo;

import online.pavelusanli.model.entity.LoadedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<LoadedDocument, Long> {
    boolean existsByFilenameAndContentHash(String filename, String contentHash);
    List<LoadedDocument> findAllByDataSourceId(Long dataSourceId);
    Optional<LoadedDocument> findByDataSourceIdAndFilename(Long dataSourceId, String filename);
    void deleteAllByDataSourceId(Long dataSourceId);
}