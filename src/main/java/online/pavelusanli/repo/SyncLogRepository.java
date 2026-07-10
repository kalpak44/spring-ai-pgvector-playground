package online.pavelusanli.repo;

import online.pavelusanli.model.entity.SyncLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncLogRepository extends JpaRepository<SyncLogEntry, Long> {
    List<SyncLogEntry> findTop200ByDataSourceIdOrderByCreatedAtDesc(Long dataSourceId);
}