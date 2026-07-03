package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.services.GoogleMcpService.GoogleToolsContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSubAgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final GoogleMcpService googleMcpService;

    public String execute(Long userId, String taskDescription, String timezone) {
        GoogleToolsContext context = googleMcpService.buildContext(userId);
        try {
            if (!context.connected() || context.tools().isEmpty()) {
                return "Google services are not available.";
            }
            log.debug("Google sub-agent executing for user {}: {}", userId, taskDescription);
            String result = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt(timezone))
                    .user(taskDescription)
                    .tools(context.tools())
                    .call()
                    .content();
            log.debug("Google sub-agent completed for user {}", userId);
            return result;
        } catch (Exception e) {
            log.error("Google sub-agent failed for user {} — task: {}", userId, taskDescription, e);
            return "Google sub-agent encountered an error: " + e.getMessage();
        } finally {
            context.close();
        }
    }

    private static String systemPrompt(String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        String now = ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        return """
                You are a focused Google services executor. You receive a specific task and carry it out using Gmail and Google Calendar tools.

                Current date and time: %s
                User timezone: %s — use this timezone when creating events or interpreting dates in the task description.

                Rules:
                1. Execute the task exactly as described — use only the tools required.
                2. For write tasks (send email, create event): all content is already finalized in the task description.
                   Execute it directly. Do not rewrite, re-draft, or add extra content.
                3. For read tasks: fetch the requested data and return a concise, factual summary.
                   Include specific details — counts, dates, senders, subjects, event titles — where relevant.
                4. If the task cannot be completed (missing required field, API error), state why briefly.

                Return only the factual result. No commentary, no suggestions, no follow-up questions.
                """.formatted(now, timezone);
    }
}