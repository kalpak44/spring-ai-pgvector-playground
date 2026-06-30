package online.pavelusanli.controller;

import online.pavelusanli.model.AppUser;
import online.pavelusanli.model.UserRole;
import online.pavelusanli.repo.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String dashboard() {
        return "admin";
    }

    @GetMapping("/users")
    public String userList(Model model) {
        model.addAttribute("users", userRepository.findAll(Sort.by("createdAt")));
        return "admin-users";
    }

    @PostMapping("/users/new")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam UserRole role,
                             Model model) {
        if (userRepository.existsByUsername(username)) {
            model.addAttribute("error", "Username already exists.");
            model.addAttribute("users", userRepository.findAll(Sort.by("createdAt")));
            return "admin-users";
        }

        userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .enabled(true)
                .build());

        return "redirect:/admin/users";
    }
}