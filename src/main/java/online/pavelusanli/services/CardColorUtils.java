package online.pavelusanli.services;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("cardColorUtils")
public class CardColorUtils {

    public static final List<String> COLORS = List.of(
            "violet", "blue", "teal", "green", "amber", "orange", "red", "pink", "zinc"
    );

    private static final Map<String, String> HEX = Map.of(
            "violet", "#7c3aed",
            "blue",   "#2563eb",
            "teal",   "#0d9488",
            "green",  "#16a34a",
            "amber",  "#d97706",
            "orange", "#ea580c",
            "red",    "#dc2626",
            "pink",   "#db2777",
            "zinc",   "#52525b"
    );

    public String hex(String color) {
        if (color == null || color.isBlank()) return null;
        return HEX.get(color);
    }

    public List<String> colors() {
        return COLORS;
    }
}