package online.pavelusanli.services;

import lombok.SneakyThrows;
import online.pavelusanli.model.Chat;
import online.pavelusanli.model.ChatEntry;
import online.pavelusanli.model.Role;
import online.pavelusanli.repo.ChatRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static online.pavelusanli.model.Role.ASSISTANT;
import static online.pavelusanli.model.Role.USER;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepo;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatService myProxy;

    public List<Chat> getAllChats(Long userId) {
        return chatRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Chat createNewChat(String title, Long userId) {
        Chat chat = Chat.builder().title(title).userId(userId).build();
        return chatRepo.save(chat);
    }

    public Chat getChat(Long chatId, Long userId) {
        return chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
    }

    public void deleteChat(Long chatId, Long userId) {
        chatRepo.findByIdAndUserId(chatId, userId).ifPresent(chatRepo::delete);
    }

    @Transactional
    public void addChatEntry(Long chatId, String prompt, Role role) {
        Chat chat = chatRepo.findById(chatId).orElseThrow();
        chat.addChatEntry(ChatEntry.builder().content(prompt).role(role).build());
    }

    @Transactional
    public void proceedInteraction(Long chatId, Long userId, String prompt) {
        chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
        myProxy.addChatEntry(chatId, prompt, USER);
        String answer = chatClient.prompt().user(prompt).call().content();
        myProxy.addChatEntry(chatId, answer, ASSISTANT);
    }

    public SseEmitter proceedInteractionWithStreaming(Long chatId, Long userId, String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(0L);

        if (chatRepo.findByIdAndUserId(chatId, userId).isEmpty()) {
            sseEmitter.complete();
            return sseEmitter;
        }

        chatClient
                .prompt(userPrompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .subscribe(
                        (ChatResponse response) -> processToken(response, sseEmitter),
                        sseEmitter::completeWithError,
                        sseEmitter::complete);
        return sseEmitter;
    }

    @SneakyThrows
    private static void processToken(ChatResponse response, SseEmitter emitter) {
        emitter.send(response.getResult().getOutput());
    }
}