package online.pavelusanli.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.pavelusanli.services.GoogleMcpService.GoogleToolsContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSubAgentService {

    private final ChatClient.Builder chatClientBuilder;
    private final GoogleMcpService googleMcpService;

    public String execute(Long userId, String taskDescription) {
        GoogleToolsContext context = googleMcpService.buildContext(userId);
        try {
            if (!context.connected() || context.tools().isEmpty()) {
                return "Google services are not available.";
            }
            log.debug("Google sub-agent executing for user {}: {}", userId, taskDescription);
            String result = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt())
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

    private static String systemPrompt() {
        String now = LocalDateTime.now(ZoneOffset.UTC) + " UTC";
        return """
                You are a focused Google services sub-agent. Perform exactly the task given to you using Gmail and Google Calendar tools.

                Current date and time: %s

                Rules:
                1. Use only the tools needed for the task — no extra calls
                2. Report back with a concise, factual summary
                3. Include specific details: counts, dates, senders, subjects, event titles where relevant
                4. If the task cannot be completed, state why briefly

                Return only the factual result. No commentary, no suggestions, no follow-up questions.
                """.formatted(now);
    }
}