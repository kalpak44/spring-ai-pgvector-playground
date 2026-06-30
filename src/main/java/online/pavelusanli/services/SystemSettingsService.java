package online.pavelusanli.services;

import online.pavelusanli.model.SystemSetting;
import online.pavelusanli.repo.SystemSettingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SystemSettingsService {

    private final SystemSettingRepository repo;

    public SystemSettingsService(SystemSettingRepository repo) {
        this.repo = repo;
    }

    public Optional<String> get(String key) {
        return repo.findById(key).map(SystemSetting::getValue);
    }

    public void set(String key, String value) {
        SystemSetting setting = repo.findById(key)
                .orElse(SystemSetting.builder().key(key).build());
        setting.setValue(value);
        repo.save(setting);
    }

    public String getOllamaBaseUrl()  { return get("ollama.base_url").orElse("http://localhost:11434"); }
    public String getOllamaModel()    { return get("ollama.model").orElse("gemma3:4b-it-q4_K_M"); }
    public String getTimezone()       { return get("timezone").orElse("Europe/Sofia"); }
    public String getLanguage()       { return get("language").orElse("en"); }
}