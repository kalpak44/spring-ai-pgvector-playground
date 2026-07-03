package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.GoogleOAuthService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/settings/connectors")
@RequiredArgsConstructor
public class SettingsConnectorsController {

    private static final String VIEW_CONNECTORS        = "settings-connectors";
    private static final String ATTR_GOOGLE_CONNECTED  = "googleConnected";
    private static final String ATTR_GOOGLE_EMAIL      = "googleEmail";
    private static final String ATTR_GOOGLE_SCOPES_OK  = "googleScopesOk";

    private final UserRepository userRepository;
    private final GoogleOAuthService googleOAuthService;

    @GetMapping
    public String connectorsPage(Model model, Authentication auth) {
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();
        googleOAuthService.findToken(user.getId()).ifPresentOrElse(
                token -> {
                    model.addAttribute(ATTR_GOOGLE_CONNECTED, true);
                    model.addAttribute(ATTR_GOOGLE_EMAIL, token.getGoogleEmail());
                },
                () -> model.addAttribute(ATTR_GOOGLE_CONNECTED, false)
        );
        model.addAttribute(ATTR_GOOGLE_SCOPES_OK, googleOAuthService.hasRequiredScopes(user.getId()));
        return VIEW_CONNECTORS;
    }
}