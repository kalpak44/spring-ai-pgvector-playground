package online.pavelusanli.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Primary
@Component
public class DynamicOllamaChatModel implements ChatModel {

    private final OllamaSettingsHolder settings;
    private OllamaChatModel delegate;
    private String lastBaseUrl;
    private String lastModel;

    public DynamicOllamaChatModel(OllamaSettingsHolder settings) {
        this.settings = settings;
    }

    private synchronized OllamaChatModel getDelegate() {
        String url = settings.getBaseUrl();
        String model = settings.getModel();
        if (delegate == null || !url.equals(lastBaseUrl) || !model.equals(lastModel)) {
            lastBaseUrl = url;
            lastModel = model;
            delegate = OllamaChatModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(url).build())
                    .defaultOptions(OllamaOptions.builder().model(model).build())
                    .build();
        }
        return delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return getDelegate().call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return getDelegate().stream(prompt);
    }
}