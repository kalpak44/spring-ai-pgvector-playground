package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.model.entity.Board;
import online.pavelusanli.repo.BoardRepository;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.services.DateDisplayUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/apps/boards")
@RequiredArgsConstructor
public class BoardListController {

    private static final int PAGE_SIZE = 12;

    private final BoardRepository boardRepo;
    private final UserRepository userRepo;
    private final DateDisplayUtils dateDisplayUtils;

    @GetMapping
    public String boardsListPage(Model model, Authentication auth, HttpServletRequest request) {
        AppUser currentUser = userRepo.findByUsername(auth.getName()).orElseThrow();
        Page<Board> boardsPage = boardRepo.searchAccessibleByUserId(
                currentUser.getId(), "",
                PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt")));

        model.addAttribute("boards", boardsPage.getContent());
        model.addAttribute("ownerNames", resolveOwnerNames(boardsPage.getContent()));
        model.addAttribute("hasMore", boardsPage.hasNext());
        return "boards-list";
    }

    @GetMapping("/fragment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> boardsFragment(
            @RequestParam int page,
            Authentication auth,
            HttpServletRequest request) {
        try {
            AppUser currentUser = userRepo.findByUsername(auth.getName()).orElseThrow();
            Page<Board> boardsPage = boardRepo.searchAccessibleByUserId(
                    currentUser.getId(), "",
                    PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "updatedAt")));

            String zoneId = resolveZoneId(request);
            List<Map<String, Object>> boards = toViews(boardsPage.getContent(), zoneId);
            return ResponseEntity.ok(Map.of("boards", boards, "hasMore", boardsPage.hasNext()));
        } catch (Exception e) {
            log.error("Failed to load boards fragment page {}", page, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to load boards"));
        }
    }

    private List<Map<String, Object>> toViews(List<Board> boards, String zoneId) {
        Map<Long, String> ownerNames = resolveOwnerNames(boards);
        return boards.stream().map(b -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", b.getId());
            view.put("name", b.getName());
            view.put("description", b.getDescription() != null ? b.getDescription() : "");
            view.put("ownerName", ownerNames.getOrDefault(b.getOwnerId(), ""));
            view.put("createdAt", dateDisplayUtils.format(b.getCreatedAt(), zoneId, "MMM d, yyyy"));
            return view;
        }).toList();
    }

    private Map<Long, String> resolveOwnerNames(List<Board> boards) {
        Set<Long> ownerIds = boards.stream().map(Board::getOwnerId).collect(Collectors.toSet());
        return userRepo.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u ->
                        u.getFirstName() != null && !u.getFirstName().isBlank()
                                ? (u.getFirstName() + (u.getLastName() != null ? " " + u.getLastName() : "")).trim()
                                : u.getUsername()));
    }

    private String resolveZoneId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "UTC";
        Object tz = session.getAttribute("user.timezone");
        return tz instanceof String s && !s.isBlank() ? s : "UTC";
    }
}