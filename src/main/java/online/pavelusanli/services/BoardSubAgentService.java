package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.repo.*;
import online.pavelusanli.tools.BoardTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardSubAgentService {

    private static final String SYSTEM_PROMPT = """
            You are a focused Kanban board and ticket executor. You receive a specific task and carry it out using board management tools.

            CRITICAL: After every tool call you MUST write a text response that includes the tool's result.
            Never return an empty response. The tool result does not speak for itself — you must relay it as text.

            Rules:
            1. Execute the task exactly as described using the available tools.
            2. After calling a tool, write the result as a short, factual text response — include IDs, names, and
               any other relevant fields from the tool output. Do not omit or paraphrase away important data.
            3. For board creation: if columns are not specified, use sensible defaults (To Do, In Progress, Done).
               Available column names: To Do, Backlog, In Progress, Review, QA, Testing, Blocked, Done, Cancelled.
               Only use names from this list — do not invent arbitrary column names.
               Always include the new board's ID in the result.
            4. For card creation: resolve the column by name using listColumns if needed. Include the new card's ID in the result.
            5. For updates (title, description, priority, deadline): pass null for fields that should stay unchanged.
               Pass "" to clear an optional field.
            6. For assignees/watchers: use the dedicated assign/unassign/addWatcher/removeWatcher tools.
            7. For invite/assign by name: use searchUsers first if the exact username is unknown, then call the appropriate tool.
            8. Priority values are: LOW, MEDIUM, HIGH, CRITICAL.
            9. Deadline format: YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS.
            10. If the task cannot be completed, state why briefly.
            11. NEVER fabricate data. Every board, ticket, user, column, or comment you report must come
                from a tool call. If a tool returns nothing, say so ("No boards found", "No tickets found").
                If a tool call fails, report the error. Do not invent plausible-sounding data under any
                circumstances — the tool results are the only source of truth.

            Keep responses short and factual. No commentary, no suggestions, no follow-up questions.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final BoardService boardService;
    private final CardService cardService;
    private final BoardColumnRepository boardColumnRepo;
    private final CardRepository cardRepo;
    private final CardAssignmentRepository cardAssignmentRepo;
    private final CardWatcherRepository cardWatcherRepo;
    private final CardCommentRepository cardCommentRepo;
    private final UserRepository userRepo;

    public String execute(Long userId, String taskDescription) {
        log.info("[BoardSubAgent] executing for userId={} task='{}'", userId, taskDescription);
        try {
            BoardTools tools = new BoardTools(userId, boardService, cardService,
                    boardColumnRepo, cardRepo, cardAssignmentRepo, cardWatcherRepo, cardCommentRepo, userRepo);
            String result = chatClientBuilder.build()
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(taskDescription)
                    .tools(tools)
                    .call()
                    .content();
            log.info("[BoardSubAgent] completed for userId={} result='{}'", userId,
                    result != null && result.length() > 200 ? result.substring(0, 200) + "…" : result);
            return result;
        } catch (Exception e) {
            log.error("[BoardSubAgent] failed for userId={} task='{}'", userId, taskDescription, e);
            return "Board sub-agent encountered an error: " + e.getMessage();
        }
    }
}