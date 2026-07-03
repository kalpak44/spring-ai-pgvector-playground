package online.pavelusanli.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    @Getter
    @RequiredArgsConstructor
    public enum Section {
        PROFILE("/settings/profile"),
        USER_MANAGEMENT("/settings/users"),
        CONNECTORS("/settings/connectors");

        private final String path;
    }

    @GetMapping
    public String settingsPage(Model model, Authentication auth) {
        List<Section> sections = new ArrayList<>(List.of(Section.PROFILE, Section.CONNECTORS));
        if (isAdmin(auth)) sections.add(1, Section.USER_MANAGEMENT);
        model.addAttribute("sections", sections);
        return "settings";
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}