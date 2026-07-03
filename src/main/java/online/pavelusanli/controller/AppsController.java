package online.pavelusanli.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/apps")
public class AppsController {

    @Getter
    @RequiredArgsConstructor
    public enum App {
        BOARDS("/apps/boards"),
        CONTACTS("/apps/contacts"),
        NOTES("/apps/notes"),
        KNOWLEDGE_BASE("/apps/knowledge-base");

        private final String path;
    }

    @GetMapping
    public String appsPage(Model model) {
        model.addAttribute("apps", List.of(App.values()));
        return "apps";
    }
}