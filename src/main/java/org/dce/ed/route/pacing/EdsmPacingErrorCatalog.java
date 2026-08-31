package org.dce.ed.route.pacing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Human-readable labels for HTTP / Cloudflare codes seen by the pacing experiment. */
public final class EdsmPacingErrorCatalog {
    private EdsmPacingErrorCatalog() {
    }

    public static List<String> codesFrom(int statusCode, String detail) {
        Set<String> codes = new LinkedHashSet<>();
        String text = detail != null ? detail.toLowerCase(Locale.ROOT) : "";
        if (text.contains("error code: 1015") || text.contains("error code 1015")) {
            codes.add("1015");
        }
        if (statusCode == 429 || text.contains("http 429")) {
            codes.add("429");
        }
        if (statusCode > 0 && statusCode != 200) {
            codes.add(Integer.toString(statusCode));
        } else if (statusCode == 0 && (detail != null && !detail.isBlank())) {
            codes.add("0");
        }
        return new ArrayList<>(codes);
    }

    public static String describe(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return switch (code.trim()) {
            case "0" -> "No HTTP response (connection failed or timed out)";
            case "400" -> "Bad Request";
            case "403" -> "Forbidden";
            case "404" -> "Not Found";
            case "408" -> "Request Timeout";
            case "429" -> "Too Many Requests";
            case "500" -> "Internal Server Error";
            case "502" -> "Bad Gateway";
            case "503" -> "Service Unavailable";
            case "504" -> "Gateway Timeout";
            case "1015" -> "Cloudflare: this IP sent too many requests";
            default -> "HTTP " + code.trim();
        };
    }
}
