package de.tum.cit.aet.dataProcessing.util;
import java.util.Locale;
import java.util.regex.Pattern;
import java.text.Normalizer;

public class AttendanceUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern FORMAT_CHAR_PATTERN = Pattern.compile("\\p{Cf}+");

    public static String normalize(String teamName) {
        if (teamName == null) {
            return "";
        }
        String normalized = Normalizer.normalize(teamName, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .strip()
                .toLowerCase(Locale.ROOT);
        normalized = FORMAT_CHAR_PATTERN.matcher(normalized).replaceAll("");
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ");
    }
    
}
