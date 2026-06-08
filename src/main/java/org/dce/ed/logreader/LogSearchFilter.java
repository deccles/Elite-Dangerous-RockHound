package org.dce.ed.logreader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Journal log viewer search: each term is a case-insensitive regex; {@code &} joins terms with AND
 * (all must appear somewhere on the row). Use {@code \&} for a literal ampersand inside one term.
 */
public final class LogSearchFilter {

    private final List<Pattern> andTerms;
    private final Pattern singlePattern;

    private LogSearchFilter(List<Pattern> andTerms, Pattern singlePattern) {
        this.andTerms = andTerms;
        this.singlePattern = singlePattern;
    }

    public static LogSearchFilter compile(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty search");
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty search");
        }
        List<String> terms = splitAndTerms(text);
        List<Pattern> patterns = new ArrayList<>(terms.size());
        for (String term : terms) {
            String trimmed = term.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            patterns.add(Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE));
        }
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("empty search");
        }
        if (patterns.size() == 1) {
            return new LogSearchFilter(List.of(), patterns.getFirst());
        }
        return new LogSearchFilter(List.copyOf(patterns), null);
    }

    /**
     * @param columnTexts row cells left-to-right (e.g. time, event, details)
     */
    public boolean matchesRow(String... columnTexts) {
        if (singlePattern != null) {
            return anyColumnMatches(singlePattern, columnTexts);
        }
        String combined = combineColumns(columnTexts);
        for (Pattern p : andTerms) {
            if (!p.matcher(combined).find()) {
                return false;
            }
        }
        return true;
    }

    public boolean isAndMode() {
        return singlePattern == null;
    }

    static List<String> splitAndTerms(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '&' && (i == 0 || text.charAt(i - 1) != '\\')) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static boolean anyColumnMatches(Pattern pattern, String... columnTexts) {
        if (columnTexts == null) {
            return false;
        }
        for (String s : columnTexts) {
            if (s != null && pattern.matcher(s).find()) {
                return true;
            }
        }
        return false;
    }

    private static String combineColumns(String... columnTexts) {
        if (columnTexts == null || columnTexts.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : columnTexts) {
            if (s != null && !s.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }
}
