package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.common.UserRole;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class SetupController {

    private static final String PATH_LOGIN = "/login";
    private static final String PATH_SETUP = "/setup";
    private static final String VIEW_SETUP = "setup";
    private static final String VIEW_LOGIN = "login";
    private static final String REDIRECT_HOME = "redirect:/";
    private static final String REDIRECT_SETUP = "redirect:%s".formatted(PATH_SETUP);
    private static final String REDIRECT_LOGIN_DONE = "redirect:%s?setup=done".formatted(PATH_LOGIN);
    private static final String ATTR_ERROR = "error";
    private static final String MSG_PASSWORD_MISMATCH = "setup.error.password_mismatch";
    private static final String MSG_PASSWORD_SHORT = "setup.error.password_short";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @GetMapping(PATH_LOGIN)
    public String loginPage() {
        if (!userRepository.existsByRole(UserRole.ADMIN)) {
            return REDIRECT_SETUP;
        }
        return VIEW_LOGIN;
    }

    @GetMapping(PATH_SETUP)
    public String setupPage() {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return REDIRECT_HOME;
        }
        return VIEW_SETUP;
    }

    @PostMapping(PATH_SETUP)
    public String doSetup(@RequestParam String firstName,
                          @RequestParam String lastName,
                          @RequestParam String username,
                          @RequestParam String password,
                          @RequestParam String passwordConfirm,
                          Model model) {
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            return REDIRECT_HOME;
        }

        if (!password.equals(passwordConfirm)) {
            model.addAttribute(ATTR_ERROR, messageSource.getMessage(MSG_PASSWORD_MISMATCH, null, Locale.ENGLISH));
            return VIEW_SETUP;
        }
        if (password.length() < 6) {
            model.addAttribute(ATTR_ERROR, messageSource.getMessage(MSG_PASSWORD_SHORT, null, Locale.ENGLISH));
            return VIEW_SETUP;
        }

        userRepository.save(AppUser.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .enabled(true)
                .build());

        return REDIRECT_LOGIN_DONE;
    }
}