package online.pavelusanli.config;

import lombok.Builder;
import online.pavelusanli.model.common.Role;
import online.pavelusanli.model.entity.ChatEntry;
import online.pavelusanli.repo.ChatEntryRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
public class AiConfig {

    private static final int HISTORY_MESSAGES = 10;

    @Bean
    public PostgresChatMemory chatMemory(ChatEntryRepository entryRepository) {
        return PostgresChatMemory.builder()
                .entryRepository(entryRepository)
                .maxMessages(HISTORY_MESSAGES)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, PostgresChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Builder
    public static class PostgresChatMemory implements ChatMemory {

        private final ChatEntryRepository entryRepository;
        private final int maxMessages;

        @Override
        public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
            long chatId = parse(conversationId);
            messages.stream()
                    .filter(m -> {
                        MessageType type = m.getMessageType();
                        String text = m.getText();
                        return type == MessageType.USER ||
                                (type == MessageType.ASSISTANT && text != null && !text.isBlank());
                    })
                    .forEach(m -> {
                        Role role = m.getMessageType() == MessageType.USER ? Role.USER : Role.ASSISTANT;
                        entryRepository.insert(chatId, m.getText(), role.name());
                    });
        }

        @Override
        @NonNull
        public List<Message> get(@NonNull String conversationId) {
            var entries = entryRepository.findRecent(parse(conversationId), PageRequest.of(0, maxMessages));
            var result = new ArrayList<>(entries.stream().map(ChatEntry::toMessage).toList());
            Collections.reverse(result);
            return result;
        }

        @Override
        public void clear(@NonNull String conversationId) {
            entryRepository.deleteByChatId(parse(conversationId));
        }

        private static long parse(@NonNull String conversationId) {
            Assert.hasText(conversationId, "conversationId cannot be null or empty");
            try {
                return Long.parseLong(conversationId);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid conversation ID: " + conversationId, e);
            }
        }
    }
}