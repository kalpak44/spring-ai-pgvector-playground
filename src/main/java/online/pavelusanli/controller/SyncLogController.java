package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.SyncLogEntry;
import online.pavelusanli.services.SyncProgressService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-base/data-sources/{id}")
@RequiredArgsConstructor
public class SyncLogController {

    private final SyncProgressService syncProgressService;

    @GetMapping(value = "/sync-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter syncStream(@PathVariable Long id) {
        return syncProgressService.subscribe(id);
    }

    @GetMapping("/sync-log")
    public List<SyncLogEntry> syncLog(@PathVariable Long id) {
        return syncProgressService.getHistory(id);
    }
}