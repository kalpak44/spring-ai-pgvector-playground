package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.BoardMemberRole;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/apps/boards/{boardId}")
@RequiredArgsConstructor
public class BoardDetailController {

    private static final String VIEW = "board-detail";
    private static final String ATTR_CARD_ERROR = "cardError";
    private static final int NOTIF_LIMIT = 20;
    private static final int PRIORITY_MAX_RESULTS = 10;

    private static final Map<CardPriority, Integer> PRIORITY_ORDER = Map.of(
            CardPriority.CRITICAL, 4,
            CardPriority.HIGH, 3,
            CardPriority.MEDIUM, 2,
            CardPriority.LOW, 1
    );

    private final BoardService boardService;
    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepo;
    private final BoardMemberRepository boardMemberRepo;
    private final CardRepository cardRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;
    private final BoardNotificationRepository boardNotifRepo;
    private final UserRepository userRepo;
    private final MessageSource messageSource;

    @GetMapping
    public String boardDetail(@PathVariable Long boardId, Model model, Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        Board board = boardService.getBoardById(boardId, user.getId());

        List<BoardColumn> columns = boardColumnRepo.findByBoardIdOrderByPosition(boardId);
        List<Card> allCards = cardRepo.findByBoardId(boardId);
        Map<Long, List<Card>> cardsByColumn = buildSortedCardMap(columns, allCards);

        // Load enrichment data for card tiles
        List<Long> cardIds = allCards.stream().map(Card::getId).toList();
        Map<Long, List<AppUser>> assigneesByCard = loadAssigneesByCard(cardIds);
        Map<Long, Long> watcherCountByCard = loadWatcherCounts(cardIds);
        Map<Long, Long> commentCountByCard = loadCommentCounts(cardIds);

        // Board members for assignee/watcher selection and settings panel
        List<BoardMember> memberEntities = boardMemberRepo.findByBoardId(boardId);
        Map<Long, BoardMemberRole> memberRoles = memberEntities.stream()
                .collect(Collectors.toMap(BoardMember::getUserId, BoardMember::getRole));
        List<AppUser> boardMembers = loadBoardMembers(boardId);

        List<BoardNotification> notifications = boardNotifRepo
                .findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                .stream().limit(NOTIF_LIMIT).toList();

        model.addAttribute("board", board);
        model.addAttribute("columns", columns);
        model.addAttribute("cardsByColumn", cardsByColumn);
        model.addAttribute("assigneesByCard", assigneesByCard);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("watcherCountByCard", watcherCountByCard);
        model.addAttribute("commentCountByCard", commentCountByCard);
        model.addAttribute("boardMembers", boardMembers);
        model.addAttribute("memberRoles", memberRoles);
        model.addAttribute("isOwner", boardService.isOwner(boardId, user.getId()));
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", notifications.size());
        model.addAttribute("priorities", CardPriority.values());
        return VIEW;
    }

    @PostMapping("/cards")
    public String createCard(@PathVariable Long boardId,
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
            ra.addFlashAttribute(ATTR_CARD_ERROR, msg("boards.detail.card.error.title_required", locale));
            return redirect(boardId);
        }
        if (trimmedTitle.length() > 255) {
            ra.addFlashAttribute(ATTR_CARD_ERROR, msg("boards.detail.card.error.title_too_long", locale));
            return redirect(boardId);
        }

        LocalDateTime deadlineDate = parseDeadline(deadline);

        try {
            cardService.createCard(boardId, columnId, user.getId(),
                    trimmedTitle, description,
                    priority, color, deadlineDate,
                    assigneeIds, watcherIds);
        } catch (Exception e) {
            log.warn("Card creation failed for board {}: {}", boardId, e.getMessage());
            ra.addFlashAttribute(ATTR_CARD_ERROR, msg("boards.detail.card.error.generic", locale));
        }
        return redirect(boardId);
    }

    @PostMapping("/notifications/read")
    public String markNotificationsRead(@PathVariable Long boardId, Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        boardService.getBoardById(boardId, user.getId());
        boardNotifRepo.markAllReadByUserId(user.getId());
        return redirect(boardId);
    }

    @PostMapping("/settings")
    public String updateSettings(@PathVariable Long boardId,
                                 @RequestParam String name,
                                 @RequestParam(required = false, defaultValue = "") String description,
                                 Authentication auth,
                                 RedirectAttributes ra,
                                 Locale locale) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        String trimmedName = name.trim();
        if (trimmedName.isBlank()) {
            ra.addFlashAttribute("settingsError", msg("boards.new.error.title_required", locale));
            return redirect(boardId);
        }
        if (trimmedName.length() > 128) {
            ra.addFlashAttribute("settingsError", msg("boards.new.error.title_too_long", locale));
            return redirect(boardId);
        }
        boardService.updateBoardName(boardId, user.getId(), trimmedName);
        boardService.updateBoardDescription(boardId, user.getId(),
                description.isBlank() ? null : description.trim());
        return redirect(boardId);
    }

    @PostMapping("/members")
    public String addMember(@PathVariable Long boardId,
                            @RequestParam Long userId,
                            Authentication auth) {
        AppUser actor = userRepo.findByUsername(auth.getName()).orElseThrow();
        boardService.addMember(boardId, actor.getId(), userId);
        return redirect(boardId);
    }

    @PostMapping("/members/{memberId}/remove")
    public String removeMember(@PathVariable Long boardId,
                               @PathVariable Long memberId,
                               Authentication auth) {
        AppUser actor = userRepo.findByUsername(auth.getName()).orElseThrow();
        boardService.removeMember(boardId, actor.getId(), memberId);
        return redirect(boardId);
    }

    @PostMapping("/delete")
    public String deleteBoard(@PathVariable Long boardId, Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        boardService.deleteBoard(boardId, user.getId());
        return "redirect:/apps/boards";
    }

    @GetMapping("/users/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchUsers(
            @PathVariable Long boardId,
            @RequestParam(defaultValue = "") String q,
            Authentication auth) {
        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        boardService.getBoardById(boardId, user.getId());

        List<Map<String, Object>> results = userRepo
                .search(q, PageRequest.of(0, PRIORITY_MAX_RESULTS))
                .stream()
                .map(u -> {
                    String displayName = buildDisplayName(u);
                    return Map.<String, Object>of(
                            "id", u.getId(),
                            "username", u.getUsername(),
                            "displayName", displayName
                    );
                })
                .toList();
        return ResponseEntity.ok(results);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Map<Long, List<Card>> buildSortedCardMap(List<BoardColumn> columns, List<Card> cards) {
        Map<Long, List<Card>> map = new LinkedHashMap<>();
        columns.forEach(col -> map.put(col.getId(), new ArrayList<>()));
        cards.forEach(card -> map.computeIfPresent(card.getColumnId(), (k, list) -> {
            list.add(card);
            return list;
        }));
        map.values().forEach(list -> list.sort(
                Comparator.comparingInt((Card c) -> c.getPriority() == null ? 0 : PRIORITY_ORDER.getOrDefault(c.getPriority(), 0))
                          .reversed()
                          .thenComparingInt(Card::getPosition)
        ));
        return map;
    }

    private Map<Long, List<AppUser>> loadAssigneesByCard(List<Long> cardIds) {
        if (cardIds.isEmpty()) return Map.of();
        List<CardAssignment> assignments = cardAssignmentRepo.findByCardIdIn(cardIds);

        Map<Long, List<Long>> idsByCard = new LinkedHashMap<>();
        assignments.forEach(a -> idsByCard
                .computeIfAbsent(a.getCardId(), k -> new ArrayList<>())
                .add(a.getUserId()));

        Set<Long> userIds = idsByCard.values().stream()
                .flatMap(ids -> ids.stream().limit(2))
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();

        Map<Long, AppUser> usersById = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        Map<Long, List<AppUser>> result = new HashMap<>();
        idsByCard.forEach((cardId, ids) -> result.put(cardId,
                ids.stream().limit(2).map(usersById::get).filter(Objects::nonNull).toList()));
        return result;
    }

    private Map<Long, Long> loadWatcherCounts(List<Long> cardIds) {
        if (cardIds.isEmpty()) return Map.of();
        return cardWatcherRepo.findByCardIdIn(cardIds).stream()
                .collect(Collectors.groupingBy(CardWatcher::getCardId, Collectors.counting()));
    }

    private Map<Long, Long> loadCommentCounts(List<Long> cardIds) {
        if (cardIds.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        cardIds.forEach(id -> {
            long c = cardCommentRepo.countByCardId(id);
            if (c > 0) counts.put(id, c);
        });
        return counts;
    }

    private List<AppUser> loadBoardMembers(Long boardId) {
        List<Long> memberIds = boardMemberRepo.findByBoardId(boardId).stream()
                .map(BoardMember::getUserId).toList();
        return userRepo.findAllById(memberIds);
    }

    private String redirect(Long boardId) {
        return "redirect:/apps/boards/" + boardId;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    static String buildDisplayName(AppUser u) {
        String fn = u.getFirstName();
        String ln = u.getLastName();
        if ((fn == null || fn.isBlank()) && (ln == null || ln.isBlank())) return u.getUsername();
        StringBuilder sb = new StringBuilder();
        if (fn != null && !fn.isBlank()) sb.append(fn);
        if (ln != null && !ln.isBlank()) { if (!sb.isEmpty()) sb.append(' '); sb.append(ln); }
        return sb.toString();
    }

    static LocalDateTime parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) return null;
        try {
            return LocalDate.parse(deadline).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}