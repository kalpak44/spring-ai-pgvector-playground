package online.pavelusanli.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final ZoneId zoneId;

    public DateTimeTool(String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        this.zoneId = zone;
    }

    @Tool(description = "Returns the current date and time in the user's local timezone. "
            + "Call this to resolve temporal references such as 'today', 'now', 'this week', "
            + "'yesterday', 'tomorrow', or 'next Monday'.")
    public String currentDateTime() {
        return ZonedDateTime.now(zoneId).format(FORMATTER);
    }
}