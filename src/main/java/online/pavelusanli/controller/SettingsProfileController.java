package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/settings/profile")
@RequiredArgsConstructor
public class SettingsProfileController {

    private static final String VIEW_PROFILE = "settings-profile";
    private static final String REDIRECT_PROFILE = "redirect:/settings/profile";
    private static final String REDIRECT_SETTINGS = "redirect:/settings";
    private static final String ATTR_CURRENT_USER = "currentUser";
    private static final String ATTR_PROFILE_ERROR = "profileError";
    private static final String MSG_PASSWORD_SHORT = "settings.profile.error.password_short";
    private static final String MSG_PASSWORD_MISMATCH = "settings.profile.error.password_mismatch";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @GetMapping
    public String profilePage(Model model, Authentication auth) {
        model.addAttribute(ATTR_CURRENT_USER, userRepository.findByUsername(auth.getName()).orElseThrow());
        return VIEW_PROFILE;
    }

    @PostMapping
    public String updateProfile(@RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String newPasswordConfirm,
                                Authentication auth,
                                RedirectAttributes ra,
                                Locale locale) {
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 6) {
                ra.addFlashAttribute(ATTR_PROFILE_ERROR, msg(MSG_PASSWORD_SHORT, locale));
                return REDIRECT_PROFILE;
            }
            if (!newPassword.equals(newPasswordConfirm)) {
                ra.addFlashAttribute(ATTR_PROFILE_ERROR, msg(MSG_PASSWORD_MISMATCH, locale));
                return REDIRECT_PROFILE;
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        userRepository.save(user);

        return REDIRECT_SETTINGS;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}