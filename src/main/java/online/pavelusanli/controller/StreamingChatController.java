package online.pavelusanli.controller;

import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamingChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping(value = "/chat-stream/{chatId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter talkToModel(@PathVariable Long chatId, @RequestParam String userPrompt, Authentication auth) {
        Long userId = userRepository.findByUsername(auth.getName()).orElseThrow().getId();
        return chatService.proceedInteractionWithStreaming(chatId, userId, userPrompt);
    }
}