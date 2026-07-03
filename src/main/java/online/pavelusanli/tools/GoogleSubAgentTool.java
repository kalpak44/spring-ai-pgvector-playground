package online.pavelusanli.tools;

import lombok.RequiredArgsConstructor;
import online.pavelusanli.services.GoogleSubAgentService;
import org.springframework.ai.tool.annotation.Tool;

/**
 * Per-request tool that routes Google-related tasks to a dedicated sub-agent.
 * The sub-agent owns the MCP client lifecycle and returns a compressed result.
 */
@RequiredArgsConstructor
public class GoogleSubAgentTool {

    private final Long userId;
    private final GoogleSubAgentService googleSubAgentService;

    @Tool(description = """
            Delegates a task to the Google services sub-agent (Gmail, Google Calendar).
            Use this when the user asks about emails, calendar events, or anything requiring their Google account.
            Provide a specific, self-contained task description — e.g. "Find unread emails from Alice today",
            "Summarize the last 5 emails in my inbox", "List calendar events for next week",
            "Send an email to bob@example.com with subject 'Hello' and body 'Are you available tomorrow?'".
            The sub-agent executes the task autonomously and returns a compressed summary.
            """)
    public String executeGoogleTask(String taskDescription) {
        return googleSubAgentService.execute(userId, taskDescription);
    }
}