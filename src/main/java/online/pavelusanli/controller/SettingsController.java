package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import online.pavelusanli.config.OllamaSettingsHolder;
import online.pavelusanli.model.AppUser;
import online.pavelusanli.model.UserRole;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.SystemSettingsService;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UserRepository userRepository;
    private final SystemSettingsService settingsService;
    private final OllamaSettingsHolder ollamaSettings;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    public SettingsController(UserRepository userRepository,
                              SystemSettingsService settingsService,
                              OllamaSettingsHolder ollamaSettings,
                              PasswordEncoder passwordEncoder,
                              MessageSource messageSource) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.ollamaSettings = ollamaSettings;
        this.passwordEncoder = passwordEncoder;
        this.messageSource = messageSource;
    }

    // ── Landing ──────────────────────────────────────────

    @GetMapping
    public String settingsPage(Model model, Authentication auth) {
        model.addAttribute("isAdmin", isAdmin(auth));
        return "settings";
    }

    // ── Profile ──────────────────────────────────────────

    @GetMapping("/profile")
    public String profilePage(Model model, Authentication auth) {
        model.addAttribute("currentUser", userRepository.findByUsername(auth.getName()).orElseThrow());
        return "settings-profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam String username,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String newPasswordConfirm,
                                Authentication auth,
                                RedirectAttributes ra,
                                Locale locale) {
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            ra.addFlashAttribute("profileError", msg("settings.profile.error.username_taken", locale));
            return "redirect:/settings/profile";
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        boolean usernameChanged = !username.equals(user.getUsername());
        user.setUsername(username);

        if (newPassword != null && !newPassword.isBlank()) {
            if (!newPassword.equals(newPasswordConfirm)) {
                ra.addFlashAttribute("profileError", msg("settings.profile.error.password_mismatch", locale));
                return "redirect:/settings/profile";
            }
            if (newPassword.length() < 6) {
                ra.addFlashAttribute("profileError", msg("settings.profile.error.password_short", locale));
                return "redirect:/settings/profile";
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);

        if (usernameChanged) {
            return "redirect:/login?usernameChanged";
        }
        ra.addFlashAttribute("profileSuccess", msg("settings.profile.success", locale));
        return "redirect:/settings/profile";
    }

    // ── System ───────────────────────────────────────────

    @GetMapping("/system")
    public String systemPage(Model model, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        model.addAttribute("ollamaBaseUrl", ollamaSettings.getBaseUrl());
        model.addAttribute("ollamaModel",   ollamaSettings.getModel());
        model.addAttribute("timezone",      settingsService.getTimezone());
        model.addAttribute("language",      settingsService.getLanguage());
        return "settings-system";
    }

    @PostMapping("/system")
    public String updateSystem(@RequestParam String ollamaBaseUrl,
                               @RequestParam String ollamaModel,
                               @RequestParam String timezone,
                               @RequestParam String language,
                               RedirectAttributes ra,
                               Locale locale) {
        ollamaSettings.setBaseUrl(ollamaBaseUrl);
        ollamaSettings.setModel(ollamaModel);
        settingsService.set("timezone", timezone);
        settingsService.set("language", language);
        ra.addFlashAttribute("systemSuccess", msg("settings.system.success", locale));
        return "redirect:/settings/system";
    }

    // ── Users ────────────────────────────────────────────

    @GetMapping("/users")
    public String usersPage(Model model, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        model.addAttribute("users", userRepository.findAll(Sort.by("createdAt")));
        model.addAttribute("currentUsername", auth.getName());
        return "settings-users";
    }

    @PostMapping("/users/new")
    public String createUser(@RequestParam String username,
                             @RequestParam UserRole role,
                             RedirectAttributes ra,
                             Locale locale,
                             HttpServletRequest request) {
        if (userRepository.existsByUsername(username)) {
            ra.addFlashAttribute("usersError", msg("settings.users.error.exists", locale));
            return "redirect:/settings/users";
        }
        String token = UUID.randomUUID().toString();
        userRepository.save(AppUser.builder()
                .username(username)
                .inviteToken(token)
                .role(role)
                .enabled(false)
                .build());
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                    ? "" : ":" + request.getServerPort());
        ra.addFlashAttribute("inviteLink", base + "/invite/" + token);
        ra.addFlashAttribute("inviteUsername", username);
        return "redirect:/settings/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             Authentication auth,
                             RedirectAttributes ra,
                             Locale locale) {
        AppUser current = userRepository.findByUsername(auth.getName()).orElseThrow();
        if (current.getId().equals(id)) {
            ra.addFlashAttribute("usersError", msg("settings.users.error.self_delete", locale));
            return "redirect:/settings/users";
        }
        userRepository.deleteById(id);
        return "redirect:/settings/users";
    }

    // ── Connectors ───────────────────────────────────────

    @GetMapping("/connectors")
    public String connectorsPage(Model model, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        model.addAttribute("googleConnected", false);
        return "settings-connectors";
    }

    // ── Helpers ──────────────────────────────────────────

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}