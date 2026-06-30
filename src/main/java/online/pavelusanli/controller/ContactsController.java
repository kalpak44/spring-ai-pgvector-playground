package online.pavelusanli.controller;

import online.pavelusanli.model.AppUser;
import online.pavelusanli.model.Contact;
import online.pavelusanli.model.ContactNote;
import online.pavelusanli.repo.ContactNoteRepository;
import online.pavelusanli.repo.ContactRepository;
import online.pavelusanli.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/apps/contacts")
public class ContactsController {

    private static final int PAGE_SIZE = 20;

    private final ContactRepository contactRepository;
    private final ContactNoteRepository contactNoteRepository;
    private final UserRepository userRepository;

    public ContactsController(ContactRepository contactRepository,
                              ContactNoteRepository contactNoteRepository,
                              UserRepository userRepository) {
        this.contactRepository = contactRepository;
        this.contactNoteRepository = contactNoteRepository;
        this.userRepository = userRepository;
    }

    // ── List ─────────────────────────────────────────────

    @GetMapping
    public String listContacts(@RequestParam(defaultValue = "") String q,
                               @RequestParam(defaultValue = "0") int page,
                               Model model) {
        PageRequest pr = PageRequest.of(page, PAGE_SIZE, Sort.by("lastName", "firstName"));
        Page<Contact> contacts = q.isBlank()
                ? contactRepository.findAll(pr)
                : contactRepository.search(q.trim(), pr);
        model.addAttribute("contacts", contacts);
        model.addAttribute("q", q);
        return "contacts";
    }

    // ── Autocomplete ─────────────────────────────────────

    @GetMapping("/search")
    @ResponseBody
    public List<Map<String, Object>> searchSuggestions(@RequestParam String q) {
        if (q.isBlank()) return List.of();
        List<Contact> results = contactRepository.searchSuggestions(
                q.trim(), PageRequest.of(0, 10));
        return results.stream().map(c -> Map.<String, Object>of(
                "id",    c.getId(),
                "name",  c.getDisplayName(),
                "email", c.getEmail()  != null ? c.getEmail()  : "",
                "phone", c.getPhone()  != null ? c.getPhone()  : ""
        )).collect(Collectors.toList());
    }

    // ── Create ────────────────────────────────────────────

    @GetMapping("/new")
    public String newContactForm(Model model) {
        model.addAttribute("contact", Contact.builder().build());
        model.addAttribute("isNew", true);
        return "contact-detail";
    }

    @PostMapping("/new")
    public String createContact(@RequestParam(required = false) String firstName,
                                @RequestParam(required = false) String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String email,
                                Authentication auth) {
        Contact c = Contact.builder()
                .firstName(blankToNull(firstName))
                .lastName(blankToNull(lastName))
                .phone(blankToNull(phone))
                .email(blankToNull(email))
                .updatedBy(auth.getName())
                .build();
        contactRepository.save(c);
        return "redirect:/apps/contacts/" + c.getId();
    }

    // ── Detail ────────────────────────────────────────────

    @GetMapping("/{id}")
    public String contactDetail(@PathVariable Long id, Model model, Authentication auth) {
        Contact contact = contactRepository.findById(id).orElseThrow();
        model.addAttribute("contact", contact);
        model.addAttribute("isNew", false);
        model.addAttribute("currentUsername", auth.getName());
        return "contact-detail";
    }

    @PostMapping("/{id}")
    public String updateContact(@PathVariable Long id,
                                @RequestParam(required = false) String firstName,
                                @RequestParam(required = false) String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String email,
                                Authentication auth,
                                RedirectAttributes ra) {
        Contact c = contactRepository.findById(id).orElseThrow();
        c.setFirstName(blankToNull(firstName));
        c.setLastName(blankToNull(lastName));
        c.setPhone(blankToNull(phone));
        c.setEmail(blankToNull(email));
        c.setUpdatedBy(auth.getName());
        contactRepository.save(c);
        ra.addFlashAttribute("saveSuccess", true);
        return "redirect:/apps/contacts/" + id;
    }

    // ── Delete ────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String deleteContact(@PathVariable Long id) {
        contactRepository.deleteById(id);
        return "redirect:/apps/contacts";
    }

    // ── Notes ─────────────────────────────────────────────

    @PostMapping("/{id}/notes")
    public String addNote(@PathVariable Long id,
                          @RequestParam String body,
                          Authentication auth) {
        if (body.isBlank()) return "redirect:/apps/contacts/" + id;
        Contact contact = contactRepository.findById(id).orElseThrow();
        AppUser user = userRepository.findByUsername(auth.getName()).orElseThrow();
        contactNoteRepository.save(ContactNote.builder()
                .contact(contact)
                .authorUsername(auth.getName())
                .authorDisplayName(displayName(user))
                .body(body.trim())
                .build());
        return "redirect:/apps/contacts/" + id;
    }

    @PostMapping("/{id}/notes/{noteId}/edit")
    public String editNote(@PathVariable Long id,
                           @PathVariable Long noteId,
                           @RequestParam String body,
                           Authentication auth) {
        ContactNote note = contactNoteRepository.findById(noteId).orElseThrow();
        if (note.getAuthorUsername().equals(auth.getName()) && !body.isBlank()) {
            note.setBody(body.trim());
            contactNoteRepository.save(note);
        }
        return "redirect:/apps/contacts/" + id;
    }

    @PostMapping("/{id}/notes/{noteId}/delete")
    public String deleteNote(@PathVariable Long id,
                             @PathVariable Long noteId,
                             Authentication auth) {
        ContactNote note = contactNoteRepository.findById(noteId).orElseThrow();
        if (note.getAuthorUsername().equals(auth.getName())) {
            contactNoteRepository.delete(note);
        }
        return "redirect:/apps/contacts/" + id;
    }

    // ── Helpers ──────────────────────────────────────────

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