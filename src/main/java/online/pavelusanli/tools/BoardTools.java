package online.pavelusanli.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.common.CardPriority;
import online.pavelusanli.model.entity.*;
import online.pavelusanli.repo.*;
import online.pavelusanli.services.BoardService;
import online.pavelusanli.services.CardService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BoardTools {

    private static final List<String> DEFAULT_COLUMNS = List.of("To Do", "In Progress", "Done");

    private final Long userId;
    private final BoardService boardService;
    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepo;
    private final CardRepository cardRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;
    private final UserRepository userRepo;

    // ─── Boards ──────────────────────────────────────────────────────────────

    @Tool(description = """
            Lists all Kanban boards accessible to the current user.
            Returns board names and IDs so they can be referenced in subsequent operations.
            """)
    public String listBoards() {
        log.info("[BoardTools] listBoards called for userId={}", userId);
        try {
            List<Board> boards = boardService.getBoardsForUser(userId);
            log.info("[BoardTools] listBoards returned {} board(s) for userId={}", boards.size(), userId);
            if (boards.isEmpty()) return "No boards found.";
            String list = boards.stream()
                    .map(b -> "- \"" + b.getName() + "\" (ID: " + b.getId() + ")")
                    .collect(Collectors.joining("\n"));
            return "Boards (" + boards.size() + "):\n" + list;
        } catch (Exception e) {
            log.error("[BoardTools] listBoards failed for userId={}", userId, e);
            return "Error retrieving boards: " + e.getMessage();
        }
    }

    @Tool(description = """
            Creates a new Kanban board with the given name, optional description, and column names.
            If columns are null or empty, defaults to: To Do, In Progress, Done.
            Available column names: To Do, Backlog, In Progress, Review, QA, Testing, Blocked, Done, Cancelled.
            Columns are fixed at creation time and cannot be modified afterward.
            Always includes the new board's ID in the result so the caller can reference it for invites.
            """)
    public String createBoard(String name, String description, List<String> columns) {
        List<String> columnNames = (columns == null || columns.isEmpty()) ? DEFAULT_COLUMNS : columns;
        Board board = boardService.createBoard(name, description, userId);
        for (int i = 0; i < columnNames.size(); i++) {
            boardColumnRepo.save(BoardColumn.builder()
                    .boardId(board.getId())
                    .name(columnNames.get(i).trim())
                    .position(i + 1)
                    .build());
        }
        return "Board \"" + board.getName() + "\" created with ID " + board.getId()
                + ". Columns: " + String.join(", ", columnNames) + ".";
    }

    @Tool(description = """
            Lists all columns of a board in their configured order, with names and IDs.
            Use this to find the exact column name before creating or moving a card.
            """)
    public String listColumns(Long boardId) {
        try {
            boardService.getBoardById(boardId, userId);
            List<BoardColumn> cols = boardColumnRepo.findByBoardIdOrderByPosition(boardId);
            if (cols.isEmpty()) return "Board " + boardId + " has no columns.";
            String list = cols.stream()
                    .map(c -> "- \"" + c.getName() + "\" (ID: " + c.getId() + ")")
                    .collect(Collectors.joining("\n"));
            return "Columns:\n" + list;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ─── Members ─────────────────────────────────────────────────────────────

    @Tool(description = """
            Searches for users by name or username (case-insensitive partial match).
            Use this to find the exact username before inviting, assigning, or adding a watcher.
            Returns up to 5 matching users with their usernames and display names.
            """)
    public String searchUsers(String query) {
        var page = userRepo.search(query, PageRequest.of(0, 5));
        if (page.isEmpty()) return "No users found matching \"" + query + "\".";
        String list = page.stream()
                .map(u -> "- " + u.getUsername() + displayName(u))
                .collect(Collectors.joining("\n"));
        return "Users matching \"" + query + "\":\n" + list;
    }

    @Tool(description = """
            Invites a user to a board by their exact username. The current user must be the board owner.
            Use searchUsers first if the exact username is not known.
            """)
    public String inviteMember(Long boardId, String username) {
        AppUser target = userRepo.findByUsername(username).orElse(null);
        if (target == null)
            return "User \"" + username + "\" not found. Use searchUsers to find the correct username.";
        try {
            boardService.addMember(boardId, userId, target.getId());
            Board board = boardService.getBoardById(boardId, userId);
            return "User " + username + displayName(target) + " has been added to board \"" + board.getName() + "\".";
        } catch (Exception e) {
            return "Could not add user to board: " + e.getMessage();
        }
    }

    @Tool(description = """
            Removes a member from a board by their exact username. The current user must be the board owner.
            """)
    public String removeMember(Long boardId, String username) {
        AppUser target = userRepo.findByUsername(username).orElse(null);
        if (target == null) return "User \"" + username + "\" not found.";
        try {
            boardService.removeMember(boardId, userId, target.getId());
            return "User " + username + " has been removed from the board.";
        } catch (Exception e) {
            return "Could not remove member: " + e.getMessage();
        }
    }

    // ─── Card listing ─────────────────────────────────────────────────────────

    @Tool(description = """
            Lists all cards on a board grouped by column.
            Shows each card's ID, title, priority, and deadline.
            Use this to get an overview or to find a card's ID before updating it.
            """)
    public String listCards(Long boardId) {
        try {
            boardService.getBoardById(boardId, userId);
            List<BoardColumn> columns = boardColumnRepo.findByBoardIdOrderByPosition(boardId);
            List<Card> cards = cardRepo.findByBoardId(boardId);
            if (cards.isEmpty()) return "No cards on this board.";

            Map<Long, List<Card>> byColumn = cards.stream()
                    .collect(Collectors.groupingBy(Card::getColumnId));

            StringBuilder sb = new StringBuilder();
            for (BoardColumn col : columns) {
                List<Card> colCards = byColumn.getOrDefault(col.getId(), List.of());
                if (colCards.isEmpty()) continue;
                sb.append("**").append(col.getName()).append("**\n");
                for (Card c : colCards) {
                    sb.append("  - [").append(c.getId()).append("] \"").append(c.getTitle()).append("\"");
                    if (c.getPriority() != null) sb.append(" [").append(c.getPriority()).append("]");
                    if (c.getDeadline() != null) sb.append(" due ").append(c.getDeadline().toLocalDate());
                    sb.append("\n");
                }
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = """
            Returns full details of a specific card: title, descriptions, priority, deadline,
            current column, assignees, watchers, and up to 10 recent comments with their IDs.
            """)
    public String getCard(Long boardId, Long cardId) {
        try {
            Card card = cardService.getCard(boardId, cardId, userId);
            BoardColumn col = boardColumnRepo.findById(card.getColumnId()).orElse(null);

            List<CardAssignment> assignments = cardAssignmentRepo.findByCardId(cardId);
            List<CardWatcher> watchers = cardWatcherRepo.findByCardId(cardId);

            StringBuilder sb = new StringBuilder();
            sb.append("Card [").append(cardId).append("] \"").append(card.getTitle()).append("\"\n");
            sb.append("Column: ").append(col != null ? col.getName() : "?").append("\n");
            if (card.getPriority() != null) sb.append("Priority: ").append(card.getPriority()).append("\n");
            if (card.getDeadline() != null) sb.append("Deadline: ").append(card.getDeadline().toLocalDate()).append("\n");
            if (card.getDescription() != null) sb.append("Description:\n").append(card.getDescription()).append("\n");

            if (!assignments.isEmpty()) {
                String names = assignments.stream().map(a -> usernameById(a.getUserId())).collect(Collectors.joining(", "));
                sb.append("Assignees: ").append(names).append("\n");
            }
            if (!watchers.isEmpty()) {
                String names = watchers.stream().map(w -> usernameById(w.getUserId())).collect(Collectors.joining(", "));
                sb.append("Watchers: ").append(names).append("\n");
            }

            var comments = cardCommentRepo.findByCardId(cardId, PageRequest.of(0, 10));
            if (!comments.isEmpty()) {
                sb.append("Comments (").append(comments.getTotalElements()).append(" total):\n");
                for (CardComment c : comments) {
                    sb.append("  [").append(c.getId()).append("] ")
                            .append(usernameById(c.getAuthorId())).append(": ")
                            .append(c.getContent()).append("\n");
                }
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ─── Card CRUD ────────────────────────────────────────────────────────────

    @Tool(description = """
            Creates a new card (ticket) on a board.
            columnName: name of the target column — use listColumns to see options.
            priority: LOW, MEDIUM, HIGH, or CRITICAL — or null to leave unset.
            deadline: ISO date "YYYY-MM-DD" or datetime "YYYY-MM-DDTHH:MM:SS" — or null.
            description: full card description text shown on the board card.
            Returns the new card ID.
            """)
    public String createCard(Long boardId, String columnName, String title,
                              String description, String priority, String deadline) {
        try {
            Long columnId = resolveColumnByName(boardId, columnName);
            Card card = cardService.createCard(boardId, columnId, userId,
                    title, description, parsePriority(priority), null, parseDeadline(deadline),
                    null, null);
            return "Card \"" + card.getTitle() + "\" created with ID " + card.getId()
                    + " in column \"" + columnName + "\".";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = """
            Updates fields of an existing card. Pass null for any field to keep its current value.
            Pass an empty string "" to clear an optional text or date field.
            description: the card description text — or "" to clear — or null to keep.
            priority: LOW, MEDIUM, HIGH, CRITICAL — or "" to clear — or null to keep.
            deadline: ISO date/datetime — or "" to clear — or null to keep.
            Assignees and watchers are managed by their own dedicated tools.
            """)
    public String updateCard(Long boardId, Long cardId, String title,
                              String description, String priority, String deadline) {
        try {
            Card current = cardService.getCard(boardId, cardId, userId);

            String newTitle = title != null ? title : current.getTitle();
            String newDescription = description != null
                    ? (description.isEmpty() ? null : description)
                    : current.getDescription();
            CardPriority newPriority = priority != null
                    ? (priority.isEmpty() ? null : parsePriority(priority))
                    : current.getPriority();
            LocalDateTime newDeadline = deadline != null
                    ? (deadline.isEmpty() ? null : parseDeadline(deadline))
                    : current.getDeadline();

            List<Long> assigneeIds = cardAssignmentRepo.findByCardId(cardId).stream()
                    .map(CardAssignment::getUserId).toList();
            List<Long> watcherIds = cardWatcherRepo.findByCardId(cardId).stream()
                    .map(CardWatcher::getUserId).toList();

            cardService.updateCard(boardId, cardId, userId,
                    newTitle, newDescription, newPriority, current.getColor(), newDeadline,
                    assigneeIds, watcherIds);
            return "Card [" + cardId + "] \"" + newTitle + "\" updated.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = """
            Moves a card to a different column (changes its status).
            columnName: exact column name — use listColumns to see options.
            """)
    public String moveCard(Long boardId, Long cardId, String columnName) {
        try {
            Long columnId = resolveColumnByName(boardId, columnName);
            Card moved = cardService.moveCard(boardId, cardId, columnId, userId);
            return "Card [" + cardId + "] \"" + moved.getTitle() + "\" moved to \"" + columnName + "\".";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Deletes a card and all its comments, assignees, and watchers permanently.")
    public String deleteCard(Long boardId, Long cardId) {
        try {
            Card card = cardService.getCard(boardId, cardId, userId);
            String title = card.getTitle();
            cardService.deleteCard(boardId, cardId, userId);
            return "Card [" + cardId + "] \"" + title + "\" deleted.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ─── Assignees & Watchers ─────────────────────────────────────────────────

    @Tool(description = "Assigns a user to a card by their exact username.")
    public String assignUser(Long boardId, Long cardId, String username) {
        return modifyParticipant(boardId, cardId, username, true, false);
    }

    @Tool(description = "Removes a user's assignment from a card by their exact username.")
    public String unassignUser(Long boardId, Long cardId, String username) {
        return modifyParticipant(boardId, cardId, username, false, false);
    }

    @Tool(description = "Adds a user as a watcher on a card by their exact username.")
    public String addWatcher(Long boardId, Long cardId, String username) {
        return modifyParticipant(boardId, cardId, username, true, true);
    }

    @Tool(description = "Removes a user's watcher subscription from a card by their exact username.")
    public String removeWatcher(Long boardId, Long cardId, String username) {
        return modifyParticipant(boardId, cardId, username, false, true);
    }

    // ─── Comments ─────────────────────────────────────────────────────────────

    @Tool(description = """
            Lists all comments on a card in chronological order.
            Returns comment IDs (needed for editing or deleting), author usernames, and content.
            """)
    public String listComments(Long boardId, Long cardId) {
        try {
            cardService.getCard(boardId, cardId, userId);
            var page = cardCommentRepo.findByCardId(cardId, PageRequest.of(0, 20));
            if (page.isEmpty()) return "No comments on this card.";
            StringBuilder sb = new StringBuilder("Comments (" + page.getTotalElements() + " total):\n");
            for (CardComment c : page) {
                sb.append("[").append(c.getId()).append("] ")
                        .append(usernameById(c.getAuthorId())).append(": ")
                        .append(c.getContent()).append("\n");
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Adds a comment to a card as the current user.")
    public String addComment(Long boardId, Long cardId, String content) {
        try {
            CardComment comment = cardService.addComment(boardId, cardId, userId, content);
            return "Comment [" + comment.getId() + "] added to card [" + cardId + "].";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = """
            Edits an existing comment. Only the comment's original author can edit it.
            Use listComments to find the commentId.
            """)
    public String editComment(Long boardId, Long cardId, Long commentId, String content) {
        try {
            cardService.updateComment(boardId, cardId, commentId, userId, content);
            return "Comment [" + commentId + "] updated.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = """
            Deletes a comment. Only the comment's original author can delete it.
            Use listComments to find the commentId.
            """)
    public String deleteComment(Long boardId, Long cardId, Long commentId) {
        try {
            cardService.deleteComment(boardId, cardId, commentId, userId);
            return "Comment [" + commentId + "] deleted.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String modifyParticipant(Long boardId, Long cardId, String username,
                                      boolean add, boolean isWatcher) {
        try {
            AppUser target = userRepo.findByUsername(username).orElse(null);
            if (target == null)
                return "User \"" + username + "\" not found. Use searchUsers to find the correct username.";

            Card current = cardService.getCard(boardId, cardId, userId);
            List<Long> assigneeIds = cardAssignmentRepo.findByCardId(cardId).stream()
                    .map(CardAssignment::getUserId).collect(Collectors.toList());
            List<Long> watcherIds = cardWatcherRepo.findByCardId(cardId).stream()
                    .map(CardWatcher::getUserId).collect(Collectors.toList());

            if (isWatcher) {
                if (add && !watcherIds.contains(target.getId())) watcherIds.add(target.getId());
                else if (!add) watcherIds.remove(target.getId());
            } else {
                if (add && !assigneeIds.contains(target.getId())) assigneeIds.add(target.getId());
                else if (!add) assigneeIds.remove(target.getId());
            }

            cardService.updateCard(boardId, cardId, userId,
                    current.getTitle(), current.getDescription(),
                    current.getPriority(), current.getColor(), current.getDeadline(),
                    assigneeIds, watcherIds);

            String action = add ? (isWatcher ? "added as watcher" : "assigned")
                                : (isWatcher ? "removed as watcher" : "unassigned");
            return "User " + username + " " + action + " on card [" + cardId + "] \""
                    + current.getTitle() + "\".";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private Long resolveColumnByName(Long boardId, String columnName) {
        List<BoardColumn> cols = boardColumnRepo.findByBoardIdOrderByPosition(boardId);
        return cols.stream()
                .filter(c -> c.getName().equalsIgnoreCase(columnName))
                .map(BoardColumn::getId)
                .findFirst()
                .orElseThrow(() -> {
                    String available = cols.stream().map(BoardColumn::getName).collect(Collectors.joining(", "));
                    return new NoSuchElementException(
                            "Column \"" + columnName + "\" not found on board " + boardId
                                    + ". Available columns: " + available);
                });
    }

    private static CardPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) return null;
        try {
            return CardPriority.valueOf(priority.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid priority \"" + priority + "\". Use: LOW, MEDIUM, HIGH, CRITICAL.");
        }
    }

    private static LocalDateTime parseDeadline(String deadline) {
        if (deadline == null || deadline.isBlank()) return null;
        String s = deadline.trim();
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDate.parse(s).atStartOfDay();
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException(
                        "Cannot parse deadline \"" + deadline + "\". Use ISO format: YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS.");
            }
        }
    }

    private String usernameById(Long uid) {
        if (uid == null) return "unknown";
        return userRepo.findById(uid).map(AppUser::getUsername).orElse("user#" + uid);
    }

    private static String displayName(AppUser user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        if ((first == null || first.isBlank()) && (last == null || last.isBlank())) return "";
        String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return " (" + full + ")";
    }
}