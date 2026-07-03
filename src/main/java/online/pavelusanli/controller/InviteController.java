package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.AppUserDetailsService;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Controller
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteController {

    private static final String VIEW_INVITE = "invite";
    private static final String REDIRECT_HOME = "redirect:/";
    private static final String ATTR_TOKEN = "token";
    private static final String ATTR_ERROR = "error";
    private static final String MSG_PASSWORD_MISMATCH = "invite.error.password_mismatch";
    private static final String MSG_PASSWORD_SHORT = "invite.error.password_short";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUserDetailsService userDetailsService;
    private final MessageSource messageSource;

    @GetMapping("/{token}")
    public String invitePage(@PathVariable String token, Model model) {
        if (isAuthenticated()) return REDIRECT_HOME;
        if (userRepository.findByInviteToken(token).isEmpty()) return REDIRECT_HOME;
        model.addAttribute(ATTR_TOKEN, token);
        return VIEW_INVITE;
    }

    @PostMapping("/{token}")
    public String completeInvite(@PathVariable String token,
                                 @RequestParam String firstName,
                                 @RequestParam String lastName,
                                 @RequestParam String password,
                                 @RequestParam String passwordConfirm,
                                 HttpServletRequest request,
                                 Model model,
                                 Locale locale) {
        if (isAuthenticated()) return REDIRECT_HOME;

        AppUser user = userRepository.findByInviteToken(token).orElse(null);
        if (user == null) return REDIRECT_HOME;

        if (password.length() < 6) {
            model.addAttribute(ATTR_TOKEN, token);
            model.addAttribute(ATTR_ERROR, msg(MSG_PASSWORD_SHORT, locale));
            return VIEW_INVITE;
        }
        if (!password.equals(passwordConfirm)) {
            model.addAttribute(ATTR_TOKEN, token);
            model.addAttribute(ATTR_ERROR, msg(MSG_PASSWORD_MISMATCH, locale));
            return VIEW_INVITE;
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setInviteToken(null);
        user.setEnabled(true);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        return REDIRECT_HOME;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}