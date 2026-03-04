package de.tum.cit.aet.pairProgramming.util;

import java.util.Locale;
import java.util.regex.Pattern;
import java.text.Normalizer;

public class AttendanceUtils {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern FORMAT_CHAR_PATTERN = Pattern.compile("\\p{Cf}+");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-z0-9\\s]");

    /**
     * Normalizes a given team name by removing spaces, trimming, replacing special chars etc.
     * @param teamName - team name
     * @return Normalized team name
     */
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

    /**
     * Normalizes a team name for fuzzy matching by:
     * - Applying standard normalization
     * - Removing common prefixes/suffixes (Team, Group, Co., KG, etc.) with or without spaces
     * - Converting special characters to their word equivalents (& to and)
     * - Removing ALL whitespace to handle spacing variations (team seven vs teamseven)
     * - Removing all non-alphanumeric characters
     *
     * @param teamName - team name
     * @return Normalized name suitable for fuzzy matching
     */
    public static String normalizeForFuzzyMatch(String teamName) {
        if (teamName == null) {
            return "";
        }

        // Start with standard normalization
        String normalized = normalize(teamName);

        // Replace common special character sequences with their word equivalents
        normalized = normalized.replace(" & ", "and")
                .replace("&", "and")
                .replace(".", "");

        // Remove common prefixes and suffixes (with or without spaces)
        // This handles: "team seven", "teamseven", "Team Seven", etc.
        normalized = normalized.replaceAll("(?i)^team\\s*", "")      // Remove leading "team" with optional space
                .replaceAll("(?i)^group\\s*", "")                    // Remove leading "group" with optional space
                .replaceAll("(?i)\\s*team$", "")                     // Remove trailing "team" with optional space
                .replaceAll("(?i)\\s*group$", "")                    // Remove trailing "group" with optional space
                .replaceAll("(?i)\\s*\\+?\\s*co\\.?\\s*kg\\s*$", ""); // Remove "+ Co. KG" or "Co. KG"

        // Remove all non-alphanumeric characters (including spaces)
        // This handles: "crop and code" vs "cropandcode", spacing variations, etc.
        normalized = SPECIAL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll("");

        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * Used for fuzzy matching when exact normalized match fails.
     *
     * @param s1 first string
     * @param s2 second string
     * @return Levenshtein distance (0 = identical)
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null) {
            s1 = "";
        }
        if (s2 == null) {
            s2 = "";
        }

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) {
            return len2;
        }
        if (len2 == 0) {
            return len1;
        }

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                                dp[i - 1][j] + 1,      // deletion
                                dp[i][j - 1] + 1),    // insertion
                        dp[i - 1][j - 1] + cost  // substitution
                );
            }
        }

        return dp[len1][len2];
    }

}
