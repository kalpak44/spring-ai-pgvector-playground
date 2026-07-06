package online.pavelusanli.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ConnectorClient {

    public record ConnectorInfo(String name, String version, List<FieldDescriptor> fields) {}

    public record FieldDescriptor(
            String key,
            String label,
            Boolean required,
            @JsonProperty("default") String defaultValue,
            Boolean secret
    ) {}

    public record TestResult(boolean ok, Integer documentCount, String message) {}

    public record ConnectorDocument(String id, String path, String contentHash) {}

    public record ConnectorFetchResult(String path, String rawContent) {}

    private record DocumentsResponse(List<ConnectorDocument> documents) {}

    private final RestClient restClient;

    public ConnectorClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public ConnectorInfo info(String baseUrl) {
        String url = normalize(baseUrl) + "/connector/info";
        log.debug("Fetching connector info from {}", url);
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(ConnectorInfo.class);
    }

    public TestResult test(String baseUrl, Map<String, String> config) {
        String url = normalize(baseUrl) + "/connector/test";
        log.debug("Testing connector at {}", url);
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("config", config))
                .retrieve()
                .body(TestResult.class);
    }

    public List<ConnectorDocument> documents(String baseUrl, Map<String, String> config) {
        String url = normalize(baseUrl) + "/connector/documents";
        log.debug("Fetching document list from {}", url);
        DocumentsResponse response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("config", config))
                .retrieve()
                .body(DocumentsResponse.class);
        return response != null && response.documents() != null ? response.documents() : List.of();
    }

    public ConnectorFetchResult fetch(String baseUrl, Map<String, String> config, String documentId) {
        String url = normalize(baseUrl) + "/connector/fetch";
        log.debug("Fetching document {} from {}", documentId, url);
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("config", config, "documentId", documentId))
                .retrieve()
                .body(ConnectorFetchResult.class);
    }

    private static String normalize(String url) {
        return url == null ? "" : url.stripTrailing().replaceAll("/+$", "");
    }
}