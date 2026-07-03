package online.pavelusanli.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.google")
public record McpGoogleProps(String url) {
}