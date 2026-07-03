package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.advisors.GoogleAwareAdvisor;
import online.pavelusanli.model.common.Role;
import online.pavelusanli.model.entity.Chat;
import online.pavelusanli.repo.ChatEntryRepository;
import online.pavelusanli.repo.ChatRepository;
import online.pavelusanli.tools.GoogleSubAgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static online.pavelusanli.model.common.Role.ASSISTANT;
import static online.pavelusanli.model.common.Role.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepo;
    private final ChatEntryRepository entryRepo;
    private final ChatClient chatClient;
    private final GoogleMcpService googleMcpService;
    private final GoogleSubAgentService googleSubAgentService;

    public List<Chat> getAllChats(Long userId) {
        return chatRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Chat createNewChat(String title, Long userId) {
        return chatRepo.save(Chat.builder().title(title).userId(userId).build());
    }

    public Chat getChat(Long chatId, Long userId) {
        return chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
    }

    public void deleteChat(Long chatId, Long userId) {
        chatRepo.findByIdAndUserId(chatId, userId).ifPresent(chatRepo::delete);
    }

    public void proceedInteraction(Long chatId, Long userId, String prompt) {
        chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
        addEntry(chatId, prompt, USER);
        String answer = chatClient.prompt().user(prompt).call().content();
        addEntry(chatId, answer, ASSISTANT);
    }

    public SseEmitter proceedInteractionWithStreaming(Long chatId, Long userId, String userPrompt) {
        SseEmitter emitter = new SseEmitter(0L);

        if (chatRepo.findByIdAndUserId(chatId, userId).isEmpty()) {
            emitter.complete();
            return emitter;
        }

        boolean googleConnected = googleMcpService.isConnected(userId);

        var spec = chatClient
                .prompt()
                .system(systemPrompt(googleConnected))
                .user(userPrompt)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param(GoogleAwareAdvisor.IS_TOOL_REQUEST, googleConnected)
                        .param(GoogleAwareAdvisor.USER_ID, userId));

        if (googleConnected) {
            spec.tools(new GoogleSubAgentTool(userId, googleSubAgentService));
        }

        spec.stream()
                .chatResponse()
                .subscribe(
                        response -> processToken(response, emitter),
                        emitter::completeWithError,
                        emitter::complete);

        return emitter;
    }

    private static String systemPrompt(boolean googleConnected) {
        if (googleConnected) {
            return """
                    You are a helpful AI assistant with access to a Google services sub-agent.
                    The user has connected their Google account (Gmail and Google Calendar are available).
                    When the user asks about emails or calendar events, use the Google sub-agent tool with a precise, self-contained task description.
                    Compose your final response using the sub-agent's result — summarize naturally rather than quoting raw output verbatim.
                    """;
        }
        return "You are a helpful AI assistant. "
                + "The user's Google account is not connected. "
                + "If asked about emails or calendar events, let them know the Google connector is not enabled or requires additional permissions.";
    }

    private void addEntry(Long chatId, String content, Role role) {
        entryRepo.insert(chatId, content, role.name());
    }

    @SneakyThrows
    private static void processToken(ChatResponse response, SseEmitter emitter) {
        Generation result = response.getResult();
        if (result == null) return;
        emitter.send(result.getOutput());
    }
}