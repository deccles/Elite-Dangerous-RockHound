package org.dce.ed.mining;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Maps commander names to valid Google Sheets worksheet titles and A1 range quoting.
 * <p>
 * Mining prospector data uses worksheets whose names start with {@link #CMDR_WORKSHEET_PREFIX}
 * (case-insensitive {@code CMDR} plus a space) followed by the sanitized commander name.
 * Other worksheets in the spreadsheet are ignored when loading.
 * </p>
 */
public final class MiningSheetTitles {

    /** Max length for Google Sheets tab titles (API limit is 100; stay under for safety). */
    private static final int MAX_TITLE_LENGTH = 99;

    /**
     * Required prefix for mining prospector worksheets (uppercase {@code CMDR} + space).
     * Matching is case-insensitive on the letters; there must be a space after {@code CMDR}.
     */
    public static final String CMDR_WORKSHEET_PREFIX = "CMDR ";

    private static final int CMDR_PREFIX_LEN = CMDR_WORKSHEET_PREFIX.length();

    private MiningSheetTitles() {
    }

    /**
     * Worksheet title for a commander's tab: {@code CMDR } + sanitized commander name.
     */
    public static String sheetTitleForCommander(String commander) {
        String base = (commander == null || commander.isBlank()) ? "-" : commander.trim();
        String sanitized = sanitizeTitle(base);
        String full = CMDR_WORKSHEET_PREFIX + sanitized;
        if (full.length() <= MAX_TITLE_LENGTH) {
            return full;
        }
        int maxSan = MAX_TITLE_LENGTH - CMDR_PREFIX_LEN;
        if (maxSan < 1) {
            return CMDR_WORKSHEET_PREFIX.substring(0, Math.min(CMDR_PREFIX_LEN, MAX_TITLE_LENGTH));
        }
        sanitized = sanitized.substring(0, Math.min(sanitized.length(), maxSan)).trim();
        if (sanitized.isEmpty()) {
            sanitized = "-";
        }
        full = CMDR_WORKSHEET_PREFIX + sanitized;
        if (full.length() > MAX_TITLE_LENGTH) {
            full = full.substring(0, MAX_TITLE_LENGTH);
        }
        return full;
    }

    /**
     * Sanitize a string into a valid sheet title: strip characters invalid in Excel/Sheets tab names,
     * collapse whitespace, trim length.
     */
    public static String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '*' || c == ':' || c == '/' || c == '\\' || c == '?'
                    || c == '[' || c == ']') {
                sb.append('_');
            } else if (Character.isWhitespace(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }
        String s = sb.toString().trim();
        if (s.isEmpty()) {
            s = "-";
        }
        if (s.length() > MAX_TITLE_LENGTH) {
            s = s.substring(0, MAX_TITLE_LENGTH).trim();
        }
        return s;
    }

    /**
     * Make a unique title when {@code base} collides with existing sheet titles.
     */
    public static String uniqueTitle(String base, Set<String> existingTitles) {
        String b = sanitizeTitle(base);
        Set<String> lower = new HashSet<>();
        for (String e : existingTitles) {
            if (e != null) {
                lower.add(e.toLowerCase(Locale.ROOT));
            }
        }
        if (!lower.contains(b.toLowerCase(Locale.ROOT))) {
            return b;
        }
        for (int n = 2; n < 1000; n++) {
            String suffix = " (" + n + ")";
            String candidate = b;
            if (candidate.length() + suffix.length() > MAX_TITLE_LENGTH) {
                candidate = candidate.substring(0, Math.max(1, MAX_TITLE_LENGTH - suffix.length())).trim();
            }
            candidate = candidate + suffix;
            if (candidate.length() > MAX_TITLE_LENGTH) {
                candidate = candidate.substring(0, MAX_TITLE_LENGTH);
            }
            if (!lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return b + " " + System.currentTimeMillis();
    }

    /**
     * Quote a sheet name for A1 notation ranges (e.g. {@code 'My Sheet'!A:Q}).
     */
    public static String quoteSheetNameForRange(String sheetTitle) {
        String s = sheetTitle == null ? "" : sheetTitle;
        String escaped = s.replace("'", "''");
        return "'" + escaped + "'";
    }

    /**
     * Prospector log row range through the Comments column (17 columns A–Q: Run … End time, Comments).
     * Kept as {@code rangeA1P} for call-site stability; the range extends through column Q.
     */
    public static String rangeA1P(String sheetTitle) {
        return quoteSheetNameForRange(sheetTitle) + "!A:Q";
    }

    /**
     * True if this worksheet is a mining prospector tab: after trim, starts with {@code CMDR } (case-insensitive).
     */
    public static boolean isCmdrMiningWorksheet(String title) {
        if (title == null) {
            return false;
        }
        String t = title.trim();
        if (t.length() < CMDR_PREFIX_LEN + 1) {
            return false;
        }
        if (!t.regionMatches(true, 0, "CMDR", 0, 4)) {
            return false;
        }
        return Character.isWhitespace(t.charAt(4));
    }

    /**
     * Commander name from a {@code CMDR …} worksheet title (substring after the prefix and whitespace).
     * Empty if the title does not {@link #isCmdrMiningWorksheet(String)}.
     */
    public static String commanderNameFromCmdrWorksheetTitle(String title) {
        if (!isCmdrMiningWorksheet(title)) {
            return "";
        }
        String t = title.trim();
        int i = 4;
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) {
            i++;
        }
        return t.substring(i).trim();
    }
}
