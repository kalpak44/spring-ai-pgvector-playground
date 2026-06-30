package online.pavelusanli.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import online.pavelusanli.services.SystemSettingsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver(SystemSettingsService settingsService) {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                Object override = request.getAttribute("locale.preview");
                if (override != null) {
                    return "bg".equals(override) ? Locale.forLanguageTag("bg") : Locale.ENGLISH;
                }
                return "bg".equals(settingsService.getLanguage())
                        ? Locale.forLanguageTag("bg")
                        : Locale.ENGLISH;
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                // locale is system-wide; change it via Settings → System
            }
        };
    }
}