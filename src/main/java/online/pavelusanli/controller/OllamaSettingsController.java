package online.pavelusanli.controller;

import online.pavelusanli.config.OllamaSettingsHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/ollama")
public class OllamaSettingsController {

    private final OllamaSettingsHolder settings;

    public OllamaSettingsController(OllamaSettingsHolder settings) {
        this.settings = settings;
    }

    @GetMapping
    public OllamaSettings get() {
        return new OllamaSettings(settings.getBaseUrl(), settings.getModel());
    }

    @PostMapping
    public void update(@RequestBody OllamaSettings body) {
        settings.setBaseUrl(body.baseUrl());
        settings.setModel(body.model());
    }

    public record OllamaSettings(String baseUrl, String model) {}
}