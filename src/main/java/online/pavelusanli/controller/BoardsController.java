package online.pavelusanli.controller;

import online.pavelusanli.model.*;
import online.pavelusanli.repo.*;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/apps/boards")
public class BoardsController {

    private final BoardColumnRepository boardColumnRepository;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final UserRepository userRepository;

    public BoardsController(BoardColumnRepository boardColumnRepository,
                            TicketRepository ticketRepository,
                            TicketCommentRepository ticketCommentRepository,
                            UserRepository userRepository) {
        this.boardColumnRepository = boardColumnRepository;
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.userRepository = userRepository;
    }

    // ── Board ─────────────────────────────────────────────

    @GetMapping
    public String boardPage(Model model) {
        model.addAttribute("columns", boardColumnRepository.findAllByOrderByPositionAsc());
        return "boards";
    }

    // ── Create ticket ─────────────────────────────────────

    @PostMapping("/columns/{colId}/tickets")
    public String createTicket(@PathVariable Long colId,
                               @RequestParam String title,
                               Authentication auth) {
        if (title == null || title.isBlank()) return "redirect:/apps/boards";
        BoardColumn col = boardColumnRepository.findById(colId).orElseThrow();
        Ticket ticket = Ticket.builder()
                .title(title.trim())
                .boardColumn(col)
                .createdBy(auth.getName())
                .build();
        ticketRepository.save(ticket);
        return "redirect:/apps/boards/tickets/" + ticket.getId();
    }

    // ── Ticket detail ─────────────────────────────────────

    @GetMapping("/tickets/{id}")
    public String ticketDetail(@PathVariable Long id, Model model, Authentication auth) {
        model.addAttribute("ticket", ticketRepository.findById(id).orElseThrow());
        model.addAttribute("allUsers", userRepository.findAll(Sort.by("username")));
        model.addAttribute("priorities", TicketPriority.values());
        model.addAttribute("currentUsername", auth.getName());
        return "ticket-detail";
    }

    @PostMapping("/tickets/{id}")
    public String updateTicket(@PathVariable Long id,
                               @RequestParam String title,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String assigneeUsername,
                               @RequestParam(required = false) String priority,
                               RedirectAttributes ra) {
        Ticket ticket = ticketRepository.findById(id).orElseThrow();
        ticket.setTitle(title.trim());
        ticket.setDescription(blankToNull(description));

        if (assigneeUsername != null && !assigneeUsername.isBlank()) {
            userRepository.findByUsername(assigneeUsername).ifPresentOrElse(
                u -> {
                    ticket.setAssigneeUsername(u.getUsername());
                    ticket.setAssigneeDisplayName(displayName(u));
                },
                () -> {
                    ticket.setAssigneeUsername(null);
                    ticket.setAssigneeDisplayName(null);
                }
            );
        } else {
            ticket.setAssigneeUsername(null);
            ticket.setAssigneeDisplayName(null);
        }

        ticket.setPriority(priority != null && !priority.isBlank()
                ? TicketPriority.valueOf(priority) : null);
        ticketRepository.save(ticket);
        ra.addFlashAttribute("saveSuccess", true);
        return "redirect:/apps/boards/tickets/" + id;
    }

    // ── Move ticket (AJAX) ────────────────────────────────

    @PostMapping("/tickets/{id}/move")
    @ResponseBody
    public Map<String, Object> moveTicket(@PathVariable Long id,
                                          @RequestParam Long columnId) {
        Ticket ticket = ticketRepository.findById(id).orElseThrow();
        BoardColumn col = boardColumnRepository.findById(columnId).orElseThrow();
        ticket.setBoardColumn(col);
        ticketRepository.save(ticket);
        return Map.of("ok", true);
    }

    // ── Reorder columns (AJAX) ────────────────────────────

    @PostMapping("/columns/{id}/moveto/{targetId}")
    @ResponseBody
    public Map<String, Object> reorderColumn(@PathVariable Long id,
                                             @PathVariable Long targetId) {
        List<BoardColumn> all = boardColumnRepository.findAllByOrderByPositionAsc();
        BoardColumn dragged = all.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
        if (dragged == null) return Map.of("ok", false);

        all.remove(dragged);
        int targetIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(targetId)) { targetIdx = i; break; }
        }
        if (targetIdx < 0) return Map.of("ok", false);

        all.add(targetIdx, dragged);
        for (int i = 0; i < all.size(); i++) all.get(i).setPosition(i);
        boardColumnRepository.saveAll(all);
        return Map.of("ok", true);
    }

    // ── Delete ticket ─────────────────────────────────────

    @PostMapping("/tickets/{id}/delete")
    public String deleteTicket(@PathVariable Long id) {
        ticketRepository.deleteById(id);
        return "redirect:/apps/boards";
    }

    // ── Comments ──────────────────────────────────────────

    @PostMapping("/tickets/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @RequestParam String body,
                             Authentication auth) {
        if (body.isBlank()) return "redirect:/apps/boards/tickets/" + id;
        Ticket ticket = ticketRepository.findById(id).orElseThrow();
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();
        ticketCommentRepository.save(TicketComment.builder()
                .ticket(ticket)
                .authorUsername(auth.getName())
                .authorDisplayName(displayName(user))
                .body(body.trim())
                .build());
        return "redirect:/apps/boards/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/comments/{cid}/edit")
    public String editComment(@PathVariable Long id,
                              @PathVariable Long cid,
                              @RequestParam String body,
                              Authentication auth) {
        TicketComment comment = ticketCommentRepository.findById(cid).orElseThrow();
        if (comment.getAuthorUsername().equals(auth.getName()) && !body.isBlank()) {
            comment.setBody(body.trim());
            ticketCommentRepository.save(comment);
        }
        return "redirect:/apps/boards/tickets/" + id;
    }

    @PostMapping("/tickets/{id}/comments/{cid}/delete")
    public String deleteComment(@PathVariable Long id,
                                @PathVariable Long cid,
                                Authentication auth) {
        TicketComment comment = ticketCommentRepository.findById(cid).orElseThrow();
        if (comment.getAuthorUsername().equals(auth.getName())) {
            ticketCommentRepository.delete(comment);
        }
        return "redirect:/apps/boards/tickets/" + id;
    }

    // ── Helpers ───────────────────────────────────────────

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String displayName(AppUser user) {
        String fn = user.getFirstName();
        String ln = user.getLastName();
        if (fn != null && ln != null) return fn + " " + ln;
        if (fn != null) return fn;
        return user.getUsername();
    }
}