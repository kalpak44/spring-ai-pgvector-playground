package online.pavelusanli.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class DateTimeTool {

    @Tool(description = "Returns the current date and time in ISO-8601 format (UTC). "
            + "Call this to resolve temporal references such as 'today', 'now', 'this week', "
            + "'yesterday', 'tomorrow', or 'next Monday'.")
    public String currentDateTime() {
        return LocalDateTime.now(ZoneOffset.UTC) + " UTC";
    }
}