package online.pavelusanli.services.chunking;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.Arrays;
import java.util.List;

public class SeparatorTextSplitter extends TextSplitter {

    private final String separator;

    public SeparatorTextSplitter(String separator) {
        this.separator = unescape(separator);
    }

    @Override
    protected List<String> splitText(String text) {
        return Arrays.stream(text.split(java.util.regex.Pattern.quote(separator)))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }
}