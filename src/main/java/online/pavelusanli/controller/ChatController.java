package online.pavelusanli.controller;

import online.pavelusanli.model.Chat;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/")
    public String mainPage(ModelMap model, Authentication auth) {
        Long userId = resolveUserId(auth);
        model.addAttribute("chats", chatService.getAllChats(userId));
        addAuthAttributes(model, auth);
        return "chat";
    }

    @GetMapping("/chat/{chatId}")
    public String showChat(@PathVariable Long chatId, ModelMap model, Authentication auth) {
        Long userId = resolveUserId(auth);
        model.addAttribute("chats", chatService.getAllChats(userId));
        try {
            model.addAttribute("chat", chatService.getChat(chatId, userId));
        } catch (Exception e) {
            return "redirect:/";
        }
        addAuthAttributes(model, auth);
        return "chat";
    }

    @PostMapping("/chat/new")
    public String newChat(@RequestParam String title, Authentication auth) {
        Long userId = resolveUserId(auth);
        Chat chat = chatService.createNewChat(title, userId);
        return "redirect:/chat/" + chat.getId();
    }

    @PostMapping("/chat/{chatId}/delete")
    public String deleteChat(@PathVariable Long chatId, Authentication auth) {
        Long userId = resolveUserId(auth);
        chatService.deleteChat(chatId, userId);
        return "redirect:/";
    }

    @PostMapping("/chat/{chatId}/entry")
    public String talkToModel(@PathVariable Long chatId, @RequestParam String prompt, Authentication auth) {
        Long userId = resolveUserId(auth);
        chatService.proceedInteraction(chatId, userId, prompt);
        return "redirect:/chat/" + chatId;
    }

    private Long resolveUserId(Authentication auth) {
        return userRepository.findByUsername(auth.getName()).orElseThrow().getId();
    }

    private void addAuthAttributes(ModelMap model, Authentication auth) {
        if (auth != null) {
            model.addAttribute("username", auth.getName());
            model.addAttribute("isAdmin", auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        }
    }
}