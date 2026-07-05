package org.dce.ed.exec.placeholder;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes exec program argument strings (respects double-quoted segments). */
public final class ExecArgsTokenizer {

    private ExecArgsTokenizer() {
    }

    public static List<String> tokenize(String programArgs) {
        List<String> tokens = new ArrayList<>();
        if (programArgs == null || programArgs.isBlank()) {
            return tokens;
        }
        String s = programArgs.trim();
        int i = 0;
        int len = s.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            if (s.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < len) {
                        sb.append(s.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                tokens.add(sb.toString());
            } else {
                int start = i;
                while (i < len && !Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                tokens.add(s.substring(start, i));
            }
        }
        return tokens;
    }
}
