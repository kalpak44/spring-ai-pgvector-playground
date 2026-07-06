package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.ChunkingStrategy;
import online.pavelusanli.services.ChunkingProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/settings/chunking-profiles")
@RequiredArgsConstructor
public class ChunkingProfileController {

    private final ChunkingProfileService service;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("profiles", service.findAll());
        return "chunking-profiles";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam ChunkingStrategy strategy,
                         @RequestParam(required = false) Integer chunkSize,
                         @RequestParam(required = false) Integer chunkOverlap,
                         @RequestParam(required = false) String separator,
                         Model model) {
        try {
            service.create(name, description, strategy, chunkSize, chunkOverlap, separator);
            return "redirect:/settings/chunking-profiles";
        } catch (Exception e) {
            log.warn("Failed to create chunking profile: {}", e.getMessage());
            model.addAttribute("error", "Failed to create profile: " + e.getMessage());
            model.addAttribute("profiles", service.findAll());
            return "chunking-profiles";
        }
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam ChunkingStrategy strategy,
                         @RequestParam(required = false) Integer chunkSize,
                         @RequestParam(required = false) Integer chunkOverlap,
                         @RequestParam(required = false) String separator,
                         Model model) {
        try {
            service.update(id, name, description, strategy, chunkSize, chunkOverlap, separator);
            return "redirect:/settings/chunking-profiles";
        } catch (Exception e) {
            log.warn("Failed to update chunking profile {}: {}", id, e.getMessage());
            model.addAttribute("error", "Failed to update profile: " + e.getMessage());
            model.addAttribute("profiles", service.findAll());
            return "chunking-profiles";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/settings/chunking-profiles";
    }
}