package online.pavelusanli.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProps(Knowledgebase knowledgebase) {
    public record Knowledgebase(String path) {
    }
}