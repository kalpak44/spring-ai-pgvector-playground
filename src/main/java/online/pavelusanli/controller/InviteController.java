package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import online.pavelusanli.model.AppUser;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;

@Controller
@RequestMapping("/invite")
public class InviteController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUserDetailsService userDetailsService;
    private final MessageSource messageSource;

    public InviteController(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            AppUserDetailsService userDetailsService,
                            MessageSource messageSource) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.messageSource = messageSource;
    }

    @GetMapping("/{token}")
    public String invitePage(@PathVariable String token, Model model) {
        if (isAuthenticated()) return "redirect:/";

        if (userRepository.findByInviteToken(token).isEmpty()) {
            return "redirect:/login";
        }

        model.addAttribute("token", token);
        return "invite";
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
        if (isAuthenticated()) return "redirect:/";

        AppUser user = userRepository.findByInviteToken(token).orElse(null);
        if (user == null) return "redirect:/login";

        if (!password.equals(passwordConfirm)) {
            model.addAttribute("token", token);
            model.addAttribute("error", messageSource.getMessage("invite.error.password_mismatch", null, locale));
            return "invite";
        }
        if (password.length() < 6) {
            model.addAttribute("token", token);
            model.addAttribute("error", messageSource.getMessage("invite.error.password_short", null, locale));
            return "invite";
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setInviteToken(null);
        user.setEnabled(true);
        userRepository.save(user);

        // Programmatic login
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

        return "redirect:/";
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}