package online.pavelusanli.services;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component("dateDisplayUtils")
public class DateDisplayUtils {

    public String format(LocalDateTime utcDateTime, String zoneId, String pattern) {
        if (utcDateTime == null) return "—";
        ZoneId zone = parseZone(zoneId);
        return DateTimeFormatter.ofPattern(pattern)
                .format(utcDateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone));
    }

    private ZoneId parseZone(String zoneId) {
        try {
            return zoneId != null && !zoneId.isBlank() ? ZoneId.of(zoneId) : ZoneId.of("UTC");
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}