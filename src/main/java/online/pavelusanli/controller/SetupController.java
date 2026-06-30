package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import online.pavelusanli.model.AppUser;
import online.pavelusanli.model.UserRole;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.SystemSettingsService;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
public class SetupController {

    private final UserRepository userRepository;
    private final SystemSettingsService settingsService;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    public SetupController(UserRepository userRepository,
                           SystemSettingsService settingsService,
                           PasswordEncoder passwordEncoder,
                           MessageSource messageSource) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.passwordEncoder = passwordEncoder;
        this.messageSource = messageSource;
    }

    @GetMapping("/login")
    public String loginPage() {
        if (userRepository.count() == 0) {
            return "redirect:/setup";
        }
        return "login";
    }

    @GetMapping("/setup")
    public String setupPage(@RequestParam(required = false, defaultValue = "en") String lang,
                            HttpServletRequest request, Model model) {
        if (userRepository.count() > 0) {
            return "redirect:/login";
        }
        request.setAttribute("locale.preview", lang);
        model.addAttribute("selectedLang", lang);
        return "setup";
    }

    @PostMapping("/setup")
    public String doSetup(@RequestParam String firstName,
                          @RequestParam String lastName,
                          @RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String passwordConfirm,
                          @RequestParam String timezone,
                          @RequestParam String language,
                          @RequestParam String ollamaBaseUrl,
                          @RequestParam String ollamaModel,
                          HttpServletRequest request,
                          Model model) {
        if (userRepository.count() > 0) {
            return "redirect:/login";
        }

        // Apply selected language for this request's rendering
        request.setAttribute("locale.preview", language);
        model.addAttribute("selectedLang", language);

        Locale locale = "bg".equals(language) ? Locale.forLanguageTag("bg") : Locale.ENGLISH;

        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", messageSource.getMessage("setup.error.password_mismatch", null, locale));
            return "setup";
        }
        if (password.length() < 6) {
            model.addAttribute("error", messageSource.getMessage("setup.error.password_short", null, locale));
            return "setup";
        }

        userRepository.save(AppUser.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .enabled(true)
                .build());

        settingsService.set("ollama.base_url", ollamaBaseUrl);
        settingsService.set("ollama.model", ollamaModel);
        settingsService.set("timezone", timezone);
        settingsService.set("language", language);

        return "redirect:/login?setup=done";
    }
}