package online.pavelusanli.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import online.pavelusanli.services.SystemSettingsService;
import org.springframework.stereotype.Component;

@Component
@Getter
public class OllamaSettingsHolder {

    private final SystemSettingsService settingsService;

    private volatile String baseUrl;
    private volatile String model;

    public OllamaSettingsHolder(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostConstruct
    void init() {
        baseUrl = settingsService.getOllamaBaseUrl();
        model   = settingsService.getOllamaModel();
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        settingsService.set("ollama.base_url", baseUrl);
    }

    public void setModel(String model) {
        this.model = model;
        settingsService.set("ollama.model", model);
    }
}