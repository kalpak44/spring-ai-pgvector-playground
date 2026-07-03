package online.pavelusanli.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProps(String clientId, String clientSecret, String redirectUri) {
}