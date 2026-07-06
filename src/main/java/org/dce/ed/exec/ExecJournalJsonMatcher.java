package org.dce.ed.exec;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.exec.ExecJournalAttributeFilter.MatchMode;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Simple top-level JSON field matching for journal exec bindings. */
public final class ExecJournalJsonMatcher {

    private static final Pattern SYMBOL = Pattern.compile("\\$([A-Z][A-Z0-9_]*)");

    private ExecJournalJsonMatcher() {
    }

    public static boolean matches(
            JsonObject obj,
            String eventName,
            List<ExecJournalAttributeFilter> filters,
            Map<String, String> placeholders) {
        if (obj == null || eventName == null || eventName.isBlank()) {
            return false;
        }
        if (!eventName.equals(jsonFieldAsString(obj, "event"))) {
            return false;
        }
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        Map<String, String> map = placeholders != null ? placeholders : Map.of();
        for (ExecJournalAttributeFilter filter : filters) {
            if (filter == null || filter.getField() == null || filter.getField().isBlank()) {
                continue;
            }
            if (!matchesFilter(obj, filter, map)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesFilter(JsonObject obj, ExecJournalAttributeFilter filter, Map<String, String> placeholders) {
        String field = filter.getField();
        MatchMode mode = filter.getMatchMode() != null ? filter.getMatchMode() : MatchMode.EQUALS;
        if (mode == MatchMode.EXISTS) {
            return obj.has(field) && !obj.get(field).isJsonNull();
        }
        String actual = jsonFieldAsString(obj, field);
        if (actual == null) {
            return false;
        }
        String expected = substitute(filter.getExpectedValue(), placeholders);
        return switch (mode) {
            case CONTAINS -> actual.contains(expected);
            case EQUALS -> actual.equals(expected);
            default -> false;
        };
    }

    static String substitute(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Map<String, String> map = placeholders != null ? placeholders : Map.of();
        Matcher m = SYMBOL.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String replacement = map.getOrDefault(key, ExecPlaceholderResolver.UNKNOWN);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String jsonFieldAsString(JsonObject obj, String field) {
        if (obj == null || field == null || field.isBlank() || !obj.has(field)) {
            return null;
        }
        JsonElement el = obj.get(field);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            JsonPrimitive prim = el.getAsJsonPrimitive();
            if (prim.isString()) {
                return prim.getAsString();
            }
            if (prim.isNumber()) {
                return prim.getAsNumber().toString();
            }
            if (prim.isBoolean()) {
                return Boolean.toString(prim.getAsBoolean());
            }
        }
        return el.toString();
    }
}
