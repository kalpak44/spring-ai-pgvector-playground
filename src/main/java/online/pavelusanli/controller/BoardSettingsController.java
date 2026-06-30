package online.pavelusanli.controller;

import online.pavelusanli.model.BoardColumn;
import online.pavelusanli.repo.BoardColumnRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/settings/boards")
public class BoardSettingsController {

    private final BoardColumnRepository boardColumnRepository;

    public BoardSettingsController(BoardColumnRepository boardColumnRepository) {
        this.boardColumnRepository = boardColumnRepository;
    }

    @GetMapping
    public String boardsPage(Model model, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        model.addAttribute("columns", boardColumnRepository.findAllByOrderByPositionAsc());
        return "settings-boards";
    }

    @PostMapping("/new")
    public String createColumn(@RequestParam String name, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        if (name == null || name.isBlank()) return "redirect:/settings/boards";
        List<BoardColumn> existing = boardColumnRepository.findAllByOrderByPositionAsc();
        int nextPos = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;
        boardColumnRepository.save(BoardColumn.builder()
                .name(name.trim())
                .position(nextPos)
                .build());
        return "redirect:/settings/boards";
    }

    @PostMapping("/{id}/up")
    public String moveColumnUp(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        List<BoardColumn> all = boardColumnRepository.findAllByOrderByPositionAsc();
        int idx = indexById(all, id);
        if (idx > 0) swapPositions(all.get(idx), all.get(idx - 1));
        return "redirect:/settings/boards";
    }

    @PostMapping("/{id}/down")
    public String moveColumnDown(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        List<BoardColumn> all = boardColumnRepository.findAllByOrderByPositionAsc();
        int idx = indexById(all, id);
        if (idx >= 0 && idx < all.size() - 1) swapPositions(all.get(idx), all.get(idx + 1));
        return "redirect:/settings/boards";
    }

    @PostMapping("/{id}/delete")
    public String deleteColumn(@PathVariable Long id, Authentication auth) {
        if (!isAdmin(auth)) return "redirect:/settings";
        boardColumnRepository.deleteById(id);
        return "redirect:/settings/boards";
    }

    // ── Helpers ───────────────────────────────────────────

    private void swapPositions(BoardColumn a, BoardColumn b) {
        int tmp = a.getPosition();
        a.setPosition(b.getPosition());
        b.setPosition(tmp);
        boardColumnRepository.saveAll(List.of(a, b));
    }

    private int indexById(List<BoardColumn> list, Long id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}