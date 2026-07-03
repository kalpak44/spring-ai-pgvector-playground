package online.pavelusanli.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.config.props.McpGoogleProps;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpClientConfig {

    private final McpGoogleProps mcpGoogleProps;

    public McpSyncClient createGoogleMcpClient(String accessToken) {
        log.debug("Creating MCP client for host: {}", mcpGoogleProps.url());
        var transport = HttpClientStreamableHttpTransport.builder(mcpGoogleProps.url())
                .openConnectionOnStartup(false)
                .connectTimeout(Duration.ofSeconds(5))
                .customizeClient(b -> b.version(HttpClient.Version.HTTP_1_1))
                .httpRequestCustomizer((req, method, uri, body, ctx) -> req.header("Authorization", "Bearer " + accessToken))
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }
}