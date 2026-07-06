package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncJobService {

    private final ConnectorSyncService connectorSyncService;

    @Async
    public void triggerSync(Long dataSourceId) {
        log.info("Starting async sync for data source {}", dataSourceId);
        connectorSyncService.sync(dataSourceId);
    }
}