package online.pavelusanli.services;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.config.McpClientConfig;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleMcpService {

    private final GoogleOAuthService googleOAuthService;
    private final McpClientConfig mcpClientConfig;

    /** Lightweight check — no MCP client is created. */
    public boolean isConnected(Long userId) {
        return googleOAuthService.hasRequiredScopes(userId);
    }

    /**
     * Builds an MCP tool context for the given user.
     * The caller is responsible for calling {@link GoogleToolsContext#close()} when done.
     */
    public GoogleToolsContext buildContext(Long userId) {
        if (!googleOAuthService.hasRequiredScopes(userId)) {
            return GoogleToolsContext.disconnected();
        }
        try {
            String accessToken = googleOAuthService.getValidAccessToken(userId);
            McpSyncClient client = mcpClientConfig.createGoogleMcpClient(accessToken);
            client.initialize();
            List<ToolCallback> tools = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
            log.debug("Resolved {} Google MCP tools for user {}", tools.size(), userId);
            return GoogleToolsContext.connected(tools, client);
        } catch (Exception e) {
            log.error("Failed to initialize Google MCP tools for user {}", userId, e);
            return GoogleToolsContext.disconnected();
        }
    }

    public record GoogleToolsContext(boolean connected, List<ToolCallback> tools, McpSyncClient client) {

        static GoogleToolsContext connected(List<ToolCallback> tools, McpSyncClient client) {
            return new GoogleToolsContext(true, tools, client);
        }

        static GoogleToolsContext disconnected() {
            return new GoogleToolsContext(false, List.of(), null);
        }

        public void close() {
            if (client == null) return;
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client", e);
            }
        }
    }
}