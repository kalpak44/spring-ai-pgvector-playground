package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.List;
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

    static final List<String> SUPPORTED_LANGUAGES = List.of("en", "bg");
    static final List<String> SUPPORTED_TIMEZONES = List.of(
            "UTC",
            "Europe/London",
            "Europe/Berlin",
            "Europe/Paris",
            "Europe/Rome",
            "Europe/Madrid",
            "Europe/Warsaw",
            "Europe/Prague",
            "Europe/Budapest",
            "Europe/Bucharest",
            "Europe/Sofia",
            "Europe/Athens",
            "Europe/Helsinki",
            "Europe/Tallinn",
            "Europe/Riga",
            "Europe/Vilnius",
            "Europe/Kyiv",
            "Europe/Moscow",
            "America/New_York",
            "America/Chicago",
            "America/Denver",
            "America/Los_Angeles",
            "Asia/Dubai",
            "Asia/Kolkata",
            "Asia/Bangkok",
            "Asia/Shanghai",
            "Asia/Tokyo",
            "Australia/Sydney"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @GetMapping
    public String profilePage(Model model, Authentication auth) {
        model.addAttribute(ATTR_CURRENT_USER, userRepository.findByUsername(auth.getName()).orElseThrow());
        model.addAttribute("languages", SUPPORTED_LANGUAGES);
        model.addAttribute("timezones", SUPPORTED_TIMEZONES);
        return VIEW_PROFILE;
    }

    @PostMapping
    public String updateProfile(@RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String newPassword,
                                @RequestParam(required = false) String newPasswordConfirm,
                                @RequestParam String language,
                                @RequestParam String timezone,
                                Authentication auth,
                                RedirectAttributes ra,
                                Locale locale,
                                HttpServletRequest request,
                                HttpServletResponse response) {
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
        if (SUPPORTED_LANGUAGES.contains(language)) {
            user.setLanguage(language);
            var localeResolver = RequestContextUtils.getLocaleResolver(request);
            if (localeResolver != null) {
                localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
            }
        }
        if (SUPPORTED_TIMEZONES.contains(timezone)) {
            user.setTimezone(timezone);
        }
        userRepository.save(user);

        return REDIRECT_SETTINGS;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}