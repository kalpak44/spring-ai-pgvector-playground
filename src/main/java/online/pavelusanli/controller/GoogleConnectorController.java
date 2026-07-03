package online.pavelusanli.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.GoogleOAuthService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/settings/connectors/google")
@RequiredArgsConstructor
public class GoogleConnectorController {

    private static final String SESSION_OAUTH_STATE  = "oauth_state";
    private static final String ATTR_ERROR           = "connectorsError";
    private static final String ATTR_SUCCESS         = "connectorsSuccess";
    private static final String REDIRECT_CONNECTORS  = "redirect:/settings/connectors";

    private static final String MSG_DENIED           = "Google authorization was denied.";
    private static final String MSG_INVALID_STATE    = "Invalid OAuth state. Please try again.";
    private static final String MSG_CONNECT_FAILED   = "Failed to connect Google account. Please try again.";
    private static final String MSG_CONNECTED        = "Google account connected successfully.";
    private static final String MSG_DISCONNECTED     = "Google account disconnected.";

    private final GoogleOAuthService googleOAuthService;
    private final UserRepository userRepository;

    @GetMapping("/connect")
    public String connect(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute(SESSION_OAUTH_STATE, state);
        return "redirect:" + googleOAuthService.buildAuthorizationUrl(state);
    }

    @GetMapping("/callback")
    public String callback(@RequestParam(required = false) String code,
                           @RequestParam(required = false) String error,
                           @RequestParam(required = false) String state,
                           HttpSession session,
                           Authentication auth,
                           RedirectAttributes ra) {
        String savedState = (String) session.getAttribute(SESSION_OAUTH_STATE);
        session.removeAttribute(SESSION_OAUTH_STATE);

        if (error != null) {
            ra.addFlashAttribute(ATTR_ERROR, MSG_DENIED);
            return REDIRECT_CONNECTORS;
        }

        if (savedState == null || !savedState.equals(state)) {
            ra.addFlashAttribute(ATTR_ERROR, MSG_INVALID_STATE);
            return REDIRECT_CONNECTORS;
        }

        try {
            AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();
            googleOAuthService.exchangeCodeAndStore(code, user.getId());
            ra.addFlashAttribute(ATTR_SUCCESS, MSG_CONNECTED);
        } catch (RestClientException e) {
            ra.addFlashAttribute(ATTR_ERROR, MSG_CONNECT_FAILED);
        }
        return REDIRECT_CONNECTORS;
    }

    @PostMapping("/disconnect")
    public String disconnect(Authentication auth, RedirectAttributes ra) {
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();
        googleOAuthService.disconnect(user.getId());
        ra.addFlashAttribute(ATTR_SUCCESS, MSG_DISCONNECTED);
        return REDIRECT_CONNECTORS;
    }
}