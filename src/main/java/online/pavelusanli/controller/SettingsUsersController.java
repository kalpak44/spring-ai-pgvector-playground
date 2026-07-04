package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.pavelusanli.model.common.UserRole;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.repo.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/settings/users")
@RequiredArgsConstructor
public class SettingsUsersController {

    private static final String VIEW_USERS = "settings-users";
    private static final String REDIRECT_USERS = "redirect:/settings/users";
    private static final String ATTR_USERS = "users";
    private static final String ATTR_USERS_PAGE = "usersPage";
    private static final String ATTR_CURRENT_USERNAME = "currentUsername";
    private static final String ATTR_USERS_ERROR = "usersError";
    private static final String ATTR_INVITE_LINK = "inviteLink";
    private static final String ATTR_INVITE_USERNAME = "inviteUsername";
    private static final String MSG_USER_EXISTS = "settings.users.error.exists";
    private static final String MSG_SELF_DELETE = "settings.users.error.self_delete";
    private static final int PAGE_SIZE = 20;
    private static final List<String> ALLOWED_SORT = List.of("username", "firstName", "role", "createdAt");

    private final UserRepository userRepository;
    private final MessageSource messageSource;

    @GetMapping
    public String usersPage(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "createdAt") String sort,
                            @RequestParam(defaultValue = "desc") String dir,
                            Model model, Authentication auth) {
        String sortField = ALLOWED_SORT.contains(sort) ? sort : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<AppUser> usersPage = userRepository.search(
                q.trim(),
                PageRequest.of(page, PAGE_SIZE, Sort.by(direction, sortField)));

        model.addAttribute(ATTR_USERS, usersPage.getContent());
        model.addAttribute(ATTR_USERS_PAGE, usersPage);
        model.addAttribute(ATTR_CURRENT_USERNAME, auth.getName());
        model.addAttribute("q", q);
        model.addAttribute("sort", sortField);
        model.addAttribute("dir", dir);
        return VIEW_USERS;
    }

    @PostMapping("/new")
    public String createUser(@RequestParam String username,
                             @RequestParam String role,
                             HttpServletRequest request,
                             RedirectAttributes ra,
                             Locale locale) {
        if (userRepository.existsByUsername(username)) {
            ra.addFlashAttribute(ATTR_USERS_ERROR, msg(MSG_USER_EXISTS, locale));
            return REDIRECT_USERS;
        }

        String token = UUID.randomUUID().toString();
        userRepository.save(AppUser.builder()
                .username(username)
                .role(UserRole.valueOf(role))
                .inviteToken(token)
                .enabled(false)
                .build());

        String origin = request.getScheme() + "://" + request.getServerName()
                + (isNonStandardPort(request) ? ":" + request.getServerPort() : "");
        ra.addFlashAttribute(ATTR_INVITE_LINK, origin + "/invite/" + token);
        ra.addFlashAttribute(ATTR_INVITE_USERNAME, username);
        return REDIRECT_USERS;
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             Authentication auth,
                             RedirectAttributes ra,
                             Locale locale) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) return REDIRECT_USERS;
        if (user.getUsername().equals(auth.getName())) {
            ra.addFlashAttribute(ATTR_USERS_ERROR, msg(MSG_SELF_DELETE, locale));
            return REDIRECT_USERS;
        }
        userRepository.delete(user);
        return REDIRECT_USERS;
    }

    @PostMapping("/delete")
    public String deleteUsers(@RequestParam(required = false) List<Long> ids,
                              Authentication auth) {
        if (ids == null || ids.isEmpty()) return REDIRECT_USERS;
        String currentUsername = auth.getName();
        userRepository.findAllById(ids).stream()
                .filter(u -> !u.getUsername().equals(currentUsername))
                .forEach(userRepository::delete);
        return REDIRECT_USERS;
    }

    private String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    private static boolean isNonStandardPort(HttpServletRequest request) {
        int port = request.getServerPort();
        return port != 80 && port != 443;
    }
}