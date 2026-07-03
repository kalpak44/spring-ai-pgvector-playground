package online.pavelusanli.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class UserContextAdvice {

    @ModelAttribute("userZoneId")
    public String userZoneId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "UTC";
        Object tz = session.getAttribute("user.timezone");
        return tz instanceof String s && !s.isBlank() ? s : "UTC";
    }
}