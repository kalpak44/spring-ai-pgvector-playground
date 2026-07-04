package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.CardPriority;
import online.pavelusanli.model.entity.*;
import online.pavelusanli.repo.*;
import online.pavelusanli.services.BoardService;
import online.pavelusanli.services.CardService;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/apps/boards/{boardId}/cards/{cardId}")
@RequiredArgsConstructor
public class CardDetailController {

    private static final String VIEW = "card-detail";
    private static final String ATTR_ERROR = "cardDetailError";
    private static final String ATTR_SUCCESS = "cardDetailSuccess";

    private final BoardService boardService;
    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepo;
    private final BoardMemberRepository boardMemberRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;
    private final UserRepository userRepo;
    private final MessageSource messageSource;

    @GetMapping
    public String cardDetail(@PathVariable Long boardId, @PathVariable Long cardId,
                             Model model, Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        Board board = boardService.getBoardById(boardId, user.getId());
        Card card = cardService.getCard(boardId, cardId, user.getId());

        BoardColumn column = boardColumnRepo.findById(card.getColumnId()).orElseThrow();
        List<BoardColumn> columns = boardColumnRepo.findByBoardIdOrderByPosition(boardId);

        List<CardAssignment> assignments = cardAssignmentRepo.findByCardId(cardId);
        List<CardWatcher> watchers = cardWatcherRepo.findByCardId(cardId);

        Set<Long> userIds = new HashSet<>();
        assignments.forEach(a -> userIds.add(a.getUserId()));
        watchers.forEach(w -> userIds.add(w.getUserId()));
        if (card.getCreatedBy() != null) userIds.add(card.getCreatedBy());

        Map<Long, AppUser> usersById = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<AppUser> assigneeUsers = assignments.stream()
                .map(a -> usersById.get(a.getUserId()))
                .filter(Objects::nonNull).toList();
        List<AppUser> watcherUsers = watchers.stream()
                .map(w -> usersById.get(w.getUserId()))
                .filter(Objects::nonNull).toList();
        Set<Long> assigneeIds = assignments.stream().map(CardAssignment::getUserId).collect(Collectors.toSet());
        Set<Long> watcherIds = watchers.stream().map(CardWatcher::getUserId).collect(Collectors.toSet());

        List<CardComment> comments = cardCommentRepo
                .findByCardId(cardId, PageRequest.of(0, 200)).getContent();
        Set<Long> authorIds = comments.stream()
                .map(CardComment::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AppUser> authorsById = userRepo.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<AppUser> boardMembers = loadBoardMembers(boardId);

        model.addAttribute("board", board);
        model.addAttribute("card", card);
        model.addAttribute("column", column);
        model.addAttribute("columns", columns);
        model.addAttribute("assigneeUsers", assigneeUsers);
        model.addAttribute("watcherUsers", watcherUsers);
        model.addAttribute("assigneeIds", assigneeIds);
        model.addAttribute("watcherIds", watcherIds);
        model.addAttribute("comments", comments);
        model.addAttribute("authorsById", authorsById);
        model.addAttribute("boardMembers", boardMembers);
        model.addAttribute("currentUserId", user.getId());
        model.addAttribute("isOwner", boardService.isOwner(boardId, user.getId()));
        model.addAttribute("priorities", CardPriority.values());
        model.addAttribute("createdByUser", card.getCreatedBy() != null ? usersById.get(card.getCreatedBy()) : null);
        return VIEW;
    }

    @PostMapping
    public String updateCard(@PathVariable Long boardId, @PathVariable Long cardId,
                             @RequestParam String title,
                             @RequestParam(required = false, defaultValue = "") String description,
                             @RequestParam Long columnId,
                             @RequestParam(required = false) CardPriority priority,
                             @RequestParam(required = false, defaultValue = "") String color,
                             @RequestParam(required = false, defaultValue = "") String deadline,
                             @RequestParam(required = false) List<Long> assigneeIds,
                             @RequestParam(required = false) List<Long> watcherIds,
                             Authentication auth,
                             RedirectAttributes ra,
                             Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();

        String trimmedTitle = title.trim();
        if (trimmedTitle.isBlank()) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.ticket.error.title_required", locale));
            return redirectCard(boardId, cardId);
        }
        if (trimmedTitle.length() > 255) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.ticket.error.title_too_long", locale));
            return redirectCard(boardId, cardId);
        }

        try {
            Card card = cardService.updateCard(boardId, cardId, user.getId(),
                    trimmedTitle, description,
                    priority, color, BoardDetailController.parseDeadline(deadline),
                    assigneeIds, watcherIds);

            // Move to different column if requested
            if (!card.getColumnId().equals(columnId)) {
                cardService.moveCard(boardId, cardId, columnId, user.getId());
            }

            ra.addFlashAttribute(ATTR_SUCCESS, msg("boards.ticket.update.success", locale));
        } catch (Exception e) {
            log.warn("Card update failed for card {} in board {}: {}", cardId, boardId, e.getMessage());
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.ticket.error.generic", locale));
            return redirectCard(boardId, cardId);
        }
        return "redirect:/apps/boards/" + boardId;
    }

    @PostMapping("/delete")
    public String deleteCard(@PathVariable Long boardId, @PathVariable Long cardId,
                             Authentication auth, RedirectAttributes ra, Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        try {
            cardService.deleteCard(boardId, cardId, user.getId());
        } catch (Exception e) {
            log.warn("Card deletion failed for card {} in board {}: {}", cardId, boardId, e.getMessage());
        }
        return "redirect:/apps/boards/" + boardId;
    }

    @PostMapping("/move")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveCard(@PathVariable Long boardId,
                                                         @PathVariable Long cardId,
                                                         @RequestParam Long columnId,
                                                         Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        try {
            cardService.moveCard(boardId, cardId, columnId, user.getId());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.warn("Move card {} to column {} failed: {}", cardId, columnId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/comments")
    public String addComment(@PathVariable Long boardId, @PathVariable Long cardId,
                             @RequestParam String content,
                             Authentication auth, RedirectAttributes ra, Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();

        String trimmed = content.trim();
        if (trimmed.isBlank()) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.empty", locale));
            return redirectCard(boardId, cardId);
        }
        if (trimmed.length() > 2000) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.too_long", locale));
            return redirectCard(boardId, cardId);
        }

        try {
            cardService.addComment(boardId, cardId, user.getId(), trimmed);
        } catch (Exception e) {
            log.warn("Add comment failed for card {}: {}", cardId, e.getMessage());
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.generic", locale));
        }
        return redirectCard(boardId, cardId);
    }

    @PostMapping("/comments/{commentId}")
    public String updateComment(@PathVariable Long boardId, @PathVariable Long cardId,
                                @PathVariable Long commentId,
                                @RequestParam String content,
                                Authentication auth, RedirectAttributes ra, Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();

        String trimmed = content.trim();
        if (trimmed.isBlank()) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.empty", locale));
            return redirectCard(boardId, cardId);
        }
        if (trimmed.length() > 2000) {
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.too_long", locale));
            return redirectCard(boardId, cardId);
        }

        try {
            cardService.updateComment(boardId, cardId, commentId, user.getId(), trimmed);
        } catch (Exception e) {
            log.warn("Update comment {} failed: {}", commentId, e.getMessage());
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.generic", locale));
        }
        return redirectCard(boardId, cardId);
    }

    @PostMapping("/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long boardId, @PathVariable Long cardId,
                                @PathVariable Long commentId,
                                Authentication auth, RedirectAttributes ra, Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        try {
            cardService.deleteComment(boardId, cardId, commentId, user.getId());
        } catch (Exception e) {
            log.warn("Delete comment {} failed: {}", commentId, e.getMessage());
            ra.addFlashAttribute(ATTR_ERROR, msg("boards.comments.error.generic", locale));
        }
        return redirectCard(boardId, cardId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<AppUser> loadBoardMembers(Long boardId) {
        List<Long> memberIds = boardMemberRepo.findByBoardId(boardId).stream()
                .map(BoardMember::getUserId).toList();
        return userRepo.findAllById(memberIds);
    }

    private String redirectCard(Long boardId, Long cardId) {
        return "redirect:/apps/boards/" + boardId + "/cards/" + cardId;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}