package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.services.ChunkingProfileService;
import online.pavelusanli.services.DataSourceService;
import online.pavelusanli.services.SyncJobService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/settings/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private static final String CONFIG_PREFIX = "cfg_";

    private final DataSourceService dataSourceService;
    private final ChunkingProfileService chunkingProfileService;
    private final SyncJobService syncJobService;

    @GetMapping
    public String listPage(Model model) {
        model.addAttribute("dataSources", dataSourceService.findAll());
        model.addAttribute("profiles", chunkingProfileService.findAll());
        return "settings-knowledge-base";
    }

    @PostMapping("/data-sources")
    public String save(@RequestParam String name,
                       @RequestParam String connectorUrl,
                       @RequestParam String connectorName,
                       @RequestParam(required = false) Long chunkingProfileId,
                       HttpServletRequest request,
                       Model model) {
        try {
            Map<String, String> config = request.getParameterMap().entrySet().stream()
                    .filter(e -> e.getKey().startsWith(CONFIG_PREFIX))
                    .collect(Collectors.toMap(
                            e -> e.getKey().substring(CONFIG_PREFIX.length()),
                            e -> e.getValue()[0]
                    ));
            dataSourceService.create(name, connectorUrl, connectorName, config, chunkingProfileId);
            return "redirect:/settings/knowledge-base";
        } catch (Exception e) {
            log.warn("Failed to save data source: {}", e.getMessage());
            model.addAttribute("error", "Failed to save: " + e.getMessage());
            model.addAttribute("dataSources", dataSourceService.findAll());
            model.addAttribute("profiles", chunkingProfileService.findAll());
            return "settings-knowledge-base";
        }
    }

    @PostMapping("/data-sources/{id}/sync")
    public String sync(@PathVariable Long id) {
        if (dataSourceService.markSyncing(id)) {
            syncJobService.triggerSync(id);
        }
        return "redirect:/settings/knowledge-base";
    }

    @PostMapping("/data-sources/{id}/delete")
    public String delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return "redirect:/settings/knowledge-base";
    }
}