package online.pavelusanli.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import online.pavelusanli.repo.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.security.Principal;
import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private static final String LOCALE_INITIALIZED = "locale.initialized";

    private final UserRepository userRepository;

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SessionLocaleResolver resolver = (SessionLocaleResolver) localeResolver();
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                HttpSession session = request.getSession(false);
                if (session == null || Boolean.TRUE.equals(session.getAttribute(LOCALE_INITIALIZED))) {
                    return true;
                }
                Principal principal = request.getUserPrincipal();
                if (principal != null) {
                    userRepository.findByUsername(principal.getName()).ifPresent(user -> {
                        String lang = user.getLanguage();
                        if (lang != null && !lang.isBlank()) {
                            resolver.setLocale(request, response, Locale.forLanguageTag(lang));
                        }
                        String tz = user.getTimezone();
                        if (tz != null && !tz.isBlank()) {
                            session.setAttribute("user.timezone", tz);
                        }
                        session.setAttribute(LOCALE_INITIALIZED, true);
                    });
                }
                return true;
            }
        });
    }
}