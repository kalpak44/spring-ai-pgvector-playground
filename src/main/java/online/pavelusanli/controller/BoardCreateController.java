package online.pavelusanli.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.model.entity.Board;
import online.pavelusanli.model.entity.BoardColumn;
import online.pavelusanli.repo.BoardColumnRepository;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.BoardService;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

@Slf4j
@Controller
@RequestMapping("/apps/boards/new")
@RequiredArgsConstructor
public class BoardCreateController {

    private static final String VIEW = "board-create";
    private static final String REDIRECT_LIST = "redirect:/apps/boards";

    private static final List<String> RECOMMENDED_KEYS = List.of(
            "boards.column.todo",
            "boards.column.in_progress",
            "boards.column.review",
            "boards.column.done"
    );

    private static final List<String> ALL_COLUMN_KEYS = List.of(
            "boards.column.backlog",
            "boards.column.todo",
            "boards.column.in_progress",
            "boards.column.review",
            "boards.column.done",
            "boards.column.testing",
            "boards.column.blocked",
            "boards.column.deployed",
            "boards.column.archived"
    );

    private final BoardService boardService;
    private final BoardColumnRepository boardColumnRepo;
    private final UserRepository userRepo;
    private final MessageSource messageSource;

    @GetMapping
    public String createPage(Model model, Locale locale) {
        model.addAttribute("allColumnNames", resolveNames(ALL_COLUMN_KEYS, locale));
        model.addAttribute("selectedColumns", resolveNames(RECOMMENDED_KEYS, locale));
        model.addAttribute("formTitle", "");
        model.addAttribute("formDescription", "");
        return VIEW;
    }

    @PostMapping
    public String createBoard(@RequestParam String title,
                              @RequestParam(required = false, defaultValue = "") String description,
                              @RequestParam(required = false) List<String> columns,
                              Authentication auth,
                              Model model,
                              Locale locale) {
        String trimmedTitle = title.trim();

        if (trimmedTitle.isBlank()) {
            repopulate(model, locale, columns, title, description);
            model.addAttribute("error", msg("boards.new.error.title_required", locale));
            return VIEW;
        }
        if (trimmedTitle.length() > 128) {
            repopulate(model, locale, columns, title, description);
            model.addAttribute("error", msg("boards.new.error.title_too_long", locale));
            return VIEW;
        }

        AppUser user = userRepo.findByUsername(auth.getName()).orElseThrow();
        String descValue = description.isBlank() ? null : description.trim();
        Board board = boardService.createBoard(trimmedTitle, descValue, user.getId());

        List<String> columnNames = columns != null
                ? columns.stream().filter(c -> c != null && !c.isBlank()).toList()
                : List.of();
        for (int i = 0; i < columnNames.size(); i++) {
            boardColumnRepo.save(BoardColumn.builder()
                    .boardId(board.getId())
                    .name(columnNames.get(i).trim())
                    .position(i + 1)
                    .build());
        }

        log.debug("Board {} created with {} columns by {}", board.getId(), columnNames.size(), user.getUsername());
        return REDIRECT_LIST;
    }

    private void repopulate(Model model, Locale locale, List<String> columns, String title, String description) {
        model.addAttribute("allColumnNames", resolveNames(ALL_COLUMN_KEYS, locale));
        model.addAttribute("selectedColumns", columns != null ? columns : resolveNames(RECOMMENDED_KEYS, locale));
        model.addAttribute("formTitle", title);
        model.addAttribute("formDescription", description);
    }

    private List<String> resolveNames(List<String> keys, Locale locale) {
        return keys.stream()
                .map(key -> messageSource.getMessage(key, null, locale))
                .toList();
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }
}