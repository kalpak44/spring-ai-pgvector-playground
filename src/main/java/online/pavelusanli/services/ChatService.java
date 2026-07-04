package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.advisors.GoogleAwareAdvisor;
import online.pavelusanli.model.common.Role;
import online.pavelusanli.model.entity.AppUser;
import online.pavelusanli.model.entity.Chat;
import online.pavelusanli.repo.ChatEntryRepository;
import online.pavelusanli.repo.ChatRepository;
import online.pavelusanli.repo.UserRepository;
import online.pavelusanli.tools.BoardSubAgentTool;
import online.pavelusanli.tools.DateTimeTool;
import online.pavelusanli.tools.GoogleSubAgentTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static online.pavelusanli.model.common.Role.ASSISTANT;
import static online.pavelusanli.model.common.Role.USER;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepo;
    private final ChatEntryRepository entryRepo;
    private final UserRepository userRepo;
    private final ChatClient chatClient;
    private final GoogleMcpService googleMcpService;
    private final GoogleSubAgentService googleSubAgentService;
    private final BoardSubAgentService boardSubAgentService;

    public List<Chat> getAllChats(Long userId) {
        return chatRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Chat createNewChat(String title, Long userId) {
        return chatRepo.save(Chat.builder().title(title).userId(userId).build());
    }

    public Chat getChat(Long chatId, Long userId) {
        return chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
    }

    public void deleteChat(Long chatId, Long userId) {
        chatRepo.findByIdAndUserId(chatId, userId).ifPresent(chat -> {
            entryRepo.deleteByChatId(chatId);
            chatRepo.deleteByIdAndUserId(chatId, userId);
        });
    }

    public void proceedInteraction(Long chatId, Long userId, String prompt) {
        chatRepo.findByIdAndUserId(chatId, userId).orElseThrow();
        addEntry(chatId, prompt, USER);
        String answer = chatClient.prompt().user(prompt).call().content();
        addEntry(chatId, answer, ASSISTANT);
    }

    public SseEmitter proceedInteractionWithStreaming(Long chatId, Long userId, String userPrompt) {
        SseEmitter emitter = new SseEmitter(0L);

        if (chatRepo.findByIdAndUserId(chatId, userId).isEmpty()) {
            emitter.complete();
            return emitter;
        }

        boolean googleConnected = googleMcpService.isConnected(userId);
        UserProfile profile = userRepo.findById(userId).map(UserProfile::from).orElse(null);
        String timezone = profile != null ? profile.timezone() : "UTC";

        var spec = chatClient
                .prompt()
                .system(systemPrompt(googleConnected, profile))
                .user(userPrompt)
                .tools(new DateTimeTool(timezone), new BoardSubAgentTool(userId, boardSubAgentService))
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, chatId)
                        .param(GoogleAwareAdvisor.IS_TOOL_REQUEST, googleConnected)
                        .param(GoogleAwareAdvisor.USER_ID, userId));

        if (googleConnected) {
            spec.tools(new GoogleSubAgentTool(userId, googleSubAgentService, timezone));
        }

        spec.stream()
                .chatResponse()
                .subscribe(
                        response -> processToken(response, emitter),
                        emitter::completeWithError,
                        emitter::complete);

        return emitter;
    }

    private static String systemPrompt(boolean googleConnected, UserProfile profile) {
        String timeContext = timeContextSection(profile);
        String langSection = languageSection(profile);
        String header = profileSection(profile);
        String boardsSection = boardsSection();

        if (googleConnected) {
            return timeContext + langSection + header + boardsSection + """
                    You are a helpful AI assistant with access to a Google services sub-agent (Gmail and Google Calendar).

                    ## Read requests (check email, list events, summarize)
                    Call the Google sub-agent immediately and present the results concisely.

                    ## Write requests (send email, create calendar event)
                    Follow this sequence — do NOT call the sub-agent until the user confirms:

                    1. Draft proactively. Compose a complete, ready-to-use draft based on what the user said.
                       For emails: include a subject line and a polite, well-formatted body.
                       For events: include a title, date/time, and attendees.
                       Make reasonable assumptions for anything not explicitly specified.
                       Never insert placeholders like [location], [details], or [optional: ...] into a draft.
                       If a specific detail is unknown and cannot be reasonably assumed, ask for it instead.

                    2. Ask only for information that is genuinely missing and prevents completion:
                       - No recipient → ask who to address it to.
                       - Request is too vague to draft anything meaningful → ask what it should be about.
                       - Event is missing a required field (date, time) → ask only for that field.
                       - Draft would require a placeholder for a concrete detail (location, name, link) → ask for that detail.
                       Do NOT ask about tone, length, format, or details you can infer from context.

                    3. Show the draft and ask for approval:
                       Present it clearly, then ask: "Does this look good, or would you like me to change anything?"

                    4. Execute after confirmation. Once approved, pass the complete content to the sub-agent
                       in a self-contained task description so it can execute without further reasoning.

                    ## Using the sub-agent
                    Pass precise task descriptions. For write tasks, include all final content (subject, body,
                    recipient, event details) so the sub-agent executes directly.
                    Summarize the sub-agent's result naturally — do not quote raw output verbatim.
                    """;
        }
        return timeContext + langSection + header + boardsSection
                + "You are a helpful AI assistant. "
                + "The user's Google account is not connected. "
                + "If asked about emails or calendar events, let them know the Google connector is not enabled or requires additional permissions.";
    }

    private static String boardsSection() {
        return """
                ## Boards & tickets sub-agent
                You have access to a Boards sub-agent for full Kanban board and ticket management.
                Delegate to it whenever the user asks about or wants to act on boards or tickets.

                ### Boards
                - List boards the user can access
                - Create a board (only name is required; description and columns are optional)
                - List or manage board members (invite, remove)

                When creating a board, always suggest the default columns first:
                  **To Do → In Progress → Done**
                Then mention that other column names are available if they want a different workflow:
                  Backlog, Review, QA / Testing, Blocked, Cancelled.
                Note: columns are set at creation time and **cannot be added or changed afterward** —
                confirm the column selection with the user before creating the board.
                After creating a board, always ask: "Who would you like to invite to this board?"
                Accept "no one" or silence as a skip — don't press further.

                ### Read requests (list boards, list tickets, get ticket details, list members)
                Delegate to the sub-agent immediately and present the results concisely.

                ### Ticket write requests (create, update, move, delete, assign, comment)
                Follow this sequence — do NOT call the sub-agent until the user confirms:

                1. Resolve the board.
                   - If the user mentioned a board name, delegate a "list boards" request to the sub-agent to confirm it exists.
                     If the name matches clearly, proceed. If ambiguous, show the candidates and ask the user to confirm.
                     If no matching board is found, say so and ask whether they meant a different board
                     or would like to create a new one.
                   - If no board was mentioned at all, ask which board the ticket belongs to.
                     You may list available boards via the sub-agent to help the user choose.

                2. Draft the ticket proactively.
                   - Propose a clear, concise, action-oriented title.
                   - Write a complete, ready-to-use description. Do NOT include placeholders, parenthetical
                     notes, or prompts like "(add details here)" or "(e.g. tracking number)".
                     If a concrete detail is truly unknown and cannot be reasonably assumed, ask for it
                     BEFORE drafting — never draft with a gap and hope the user fills it in.
                   - Default to the first column (e.g., "To Do") unless the user specified a different one.
                   - Do NOT ask about priority or deadline unless the user explicitly mentioned them.

                3. Show the draft and ask for confirmation.
                   Present the proposed title and description clearly, then ask:
                   "Does this look good, or would you like to adjust anything?"

                   Interpreting the user's reply:
                   - Treat any short positive signal as approval and create the ticket immediately —
                     this includes "yes", "ok", "good", "looks good", "go ahead", "create it",
                     and casual or slightly misspelled variants like "it good", "yep", "sure", "fine".
                   - If the user says something is wrong or wants a change, ask ONE focused question
                     to clarify what should be different. Do not present a bullet list of possibilities.
                   - If the reply is genuinely ambiguous, ask: "Should I go ahead and create it?"

                4. Execute after confirmation.
                   Once approved, delegate to the sub-agent with a precise, self-contained task description
                   including the board name or ID, column, and all final ticket content.

                ### Context rules
                - When the user references a board or ticket without specifying an ID, use listBoards or listCards
                  to find the right one and confirm with the user if ambiguous.
                - Board ID and card ID are needed for most operations — fetch them if not yet known.
                - Priority values: LOW, MEDIUM, HIGH, CRITICAL.
                - Deadline format: ISO date YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS.

                ### Data integrity — STRICT
                Never fabricate boards, tickets, users, columns, comments, or any other application data.
                Always retrieve real data from the backend via the sub-agent before presenting it to the user.
                If no data is returned, say so explicitly ("You have no boards yet", "No tickets found").
                If retrieval fails, explain the failure and suggest retrying or checking permissions.
                Do not infer or assume the existence of any entity based on prior conversation context.
                The backend is the sole source of truth — treat it as such for every operation.

                """;
    }

    private static String timeContextSection(UserProfile profile) {
        if (profile == null) return "";
        ZoneId zone;
        try {
            zone = ZoneId.of(profile.timezone());
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        String now = ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        return "## Time context\n"
                + "Current time: " + now + "\n"
                + "User timezone: " + profile.timezone()
                + " — interpret all temporal references (\"now\", \"today\", \"tomorrow\","
                + " \"in 3 hours\", \"next Monday\") relative to this timezone"
                + " unless the user explicitly specifies another.\n\n";
    }

    private static String languageSection(UserProfile profile) {
        if (profile == null || "en".equals(profile.language())) {
            return "";
        }

        if ("bg".equals(profile.language())) {
            return """
            ## Language
            Respond in Bulgarian. All your responses, email drafts, calendar event content, and other user-facing text should be in Bulgarian unless the user explicitly requests another language.

            """;
        }

        return "";
    }

    private static String profileSection(UserProfile profile) {
        if (profile == null || profile.displayName() == null) return "";
        return "## Acting on behalf of\nName: " + profile.displayName() + "\n\n";
    }

    private void addEntry(Long chatId, String content, Role role) {
        entryRepo.insert(chatId, content, role.name());
    }

    @SneakyThrows
    private static void processToken(ChatResponse response, SseEmitter emitter) {
        Generation result = response.getResult();
        if (result == null) return;
        emitter.send(result.getOutput());
    }

    private record UserProfile(String displayName, String language, String timezone) {
        static UserProfile from(AppUser user) {
            String name = Stream.of(user.getFirstName(), user.getLastName())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
            String lang = user.getLanguage() != null && !user.getLanguage().isBlank() ? user.getLanguage() : "en";
            String tz = user.getTimezone() != null && !user.getTimezone().isBlank() ? user.getTimezone() : "UTC";
            return new UserProfile(name.isBlank() ? null : name, lang, tz);
        }
    }
}
