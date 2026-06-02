package org.dce.systemmodel.designation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesignationParser {

    private static final Pattern TRAILING_STAR_BODY = Pattern
            .compile("(?<![A-Za-z])([A-Za-z])\\s+(\\d+)(?:\\s+([a-z]+))?\\s*$");
    private static final Pattern COMPACT_MOON = Pattern.compile("^(\\d+)\\s*([A-Za-z])\\s*$");

    private DesignationParser() {
    }

    public static boolean hasMoonLetterSuffix(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return false;
        }
        Matcher m = TRAILING_STAR_BODY.matcher(bodyName.trim());
        if (m.find()) {
            String moon = m.group(3);
            return moon != null && !moon.isEmpty();
        }
        String[] parts = bodyName.trim().split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            return prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0));
        }
        return false;
    }

    public static String moonHostDesignation(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return null;
        }
        String trimmed = bodyName.trim();
        Matcher compact = COMPACT_MOON.matcher(trimmed);
        if (compact.matches()) {
            return compact.group(1);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String prev = parts[parts.length - 2];
            if (prev.matches("\\d+") && last.length() == 1 && Character.isLetter(last.charAt(0))) {
                return prev;
            }
        }
        return null;
    }

    public static String shortLabelFromName(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return "";
        }
        int sp = bodyName.lastIndexOf(' ');
        return sp >= 0 ? bodyName.substring(sp + 1).trim() : bodyName.trim();
    }
}
