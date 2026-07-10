package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.SyncEventType;
import online.pavelusanli.model.entity.SyncLogEntry;
import online.pavelusanli.repo.SyncLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncProgressService {

    private static final long SSE_TIMEOUT_MS = 60 * 60 * 1000L;

    private final SyncLogRepository syncLogRepository;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long dataSourceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(dataSourceId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        emitter.onCompletion(cleanup);
        return emitter;
    }

    public void emit(Long dataSourceId, String syncRunId, SyncEventType type, String level,
                     String message, Map<String, Object> details) {
        SyncLogEntry entry = syncLogRepository.save(SyncLogEntry.builder()
                .dataSourceId(dataSourceId)
                .syncRunId(syncRunId)
                .level(level)
                .eventType(type)
                .message(message)
                .details(details)
                .build());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entry.getId());
        payload.put("syncRunId", syncRunId);
        payload.put("type", type.name());
        payload.put("level", level);
        payload.put("message", message);
        payload.put("details", details != null ? details : Map.of());
        payload.put("createdAt", entry.getCreatedAt().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            json = "{\"type\":\"" + type.name() + "\",\"message\":\"" + message.replace("\"", "'") + "\"}";
        }

        List<SseEmitter> active = emitters.getOrDefault(dataSourceId, new CopyOnWriteArrayList<>());
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : active) {
            try {
                emitter.send(SseEmitter.event().data(json));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        active.removeAll(dead);
    }

    public void completeEmitters(Long dataSourceId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.remove(dataSourceId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    public List<SyncLogEntry> getHistory(Long dataSourceId) {
        List<SyncLogEntry> desc = syncLogRepository.findTop200ByDataSourceIdOrderByCreatedAtDesc(dataSourceId);
        List<SyncLogEntry> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }
}