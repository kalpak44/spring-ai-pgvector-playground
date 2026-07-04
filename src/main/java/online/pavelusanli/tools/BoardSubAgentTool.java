package online.pavelusanli.tools;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.services.BoardSubAgentService;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Per-request tool that routes board-related tasks to the dedicated boards sub-agent.
 */
@RequiredArgsConstructor
public class BoardSubAgentTool {

    private final Long userId;
    private final BoardSubAgentService boardSubAgentService;

    @Tool(description = """
            Delegates a task to the Kanban boards and tickets sub-agent.
            Use this for any board or ticket operation — reading or writing — including:
            listing boards, looking up boards by name, listing tickets, getting ticket details,
            creating or updating boards and tickets, moving tickets, managing assignees, watchers, and comments.
            Provide a specific, self-contained task description — e.g.:
            "List all boards the user has access to",
            "List boards matching the name 'marketing'",
            "List all tickets on board 42 grouped by column",
            "Create a ticket on board 42 in column 'To Do' with title 'Fix login redirect' and description 'The redirect after login goes to the wrong page on mobile.'",
            "Move ticket 17 on board 42 to column 'In Progress'",
            "Assign user alice to ticket 17 on board 42",
            "Create a board called 'Marketing Q3' with columns: To Do, In Progress, Review, Done".
            The sub-agent executes the task and returns a concise factual result including IDs.
            """)
    public String executeBoardTask(String taskDescription) {
        return boardSubAgentService.execute(userId, taskDescription);
    }
}