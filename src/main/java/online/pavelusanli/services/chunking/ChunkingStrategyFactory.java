package online.pavelusanli.services.chunking;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.ChunkingProfile;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ChunkingStrategyFactory {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public TextSplitter get(ChunkingProfile profile) {
        return switch (profile.getStrategy()) {
            case FIXED_TOKENS -> TokenTextSplitter.builder()
                    .withChunkSize(profile.getChunkSize() != null ? profile.getChunkSize() : 200)
                    .build();
            case CUSTOM_SEPARATOR -> new SeparatorTextSplitter(
                    profile.getSeparator() != null ? profile.getSeparator() : "\n\n"
            );
            case AI_CHUNKING -> new AiChunkingSplitter(chatModel, objectMapper);
        };
    }
}