package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.services.ConnectorClient;
import online.pavelusanli.services.ConnectorClient.ConnectorInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseApiController {

    private final ConnectorClient connectorClient;

    @PostMapping("/discover")
    public ResponseEntity<?> discover(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url is required"));
        }
        try {
            ConnectorInfo info = connectorClient.info(url);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.warn("Discover failed for {}: {}", url, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Could not reach connector: " + e.getMessage()));
        }
    }
}