package org.dce.ed.logreader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only NDJSON log of {@code Status.json} snapshots that EDO already shows in the
 * journal viewer (meaningful content changes, not timestamp-only rewrites).
 * <p>
 * Written under {@code ~/.edo/logs/StatusHistory.log} (EDO's data dir), one JSON object per
 * line, so GuiFocus / flags can be compared to journal timestamps after the fact.
 */
public final class StatusHistoryLog {

    public static final String FILE_NAME = "StatusHistory.log";

    private static final Object LOCK = new Object();
    private static String lastWrittenKey;
    private static Instant lastIoErrorLog;

    private StatusHistoryLog() {
    }

    /** {@code %USERPROFILE%\\.edo\\logs} (same tree as EDO console logs). */
    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home", "."), ".edo", "logs");
    }

    public static Path defaultFile() {
        return defaultDirectory().resolve(FILE_NAME);
    }

    public static Path fileIn(Path directory) {
        if (directory == null) {
            return null;
        }
        return directory.resolve(FILE_NAME);
    }

    /** Append to {@link #defaultFile()}. */
    public static void append(String statusJson) {
        append(defaultDirectory(), statusJson);
    }

    /**
     * Append one Status.json snapshot. Creates {@code directory} if needed. No-op for blank JSON
     * or a consecutive duplicate of the same compacted line (overlay + log viewer both watching).
     */
    public static void append(Path directory, String statusJson) {
        if (directory == null) {
            return;
        }
        String line = toSingleLine(statusJson);
        if (line.isEmpty()) {
            return;
        }
        Path file = fileIn(directory);
        String key = file.toAbsolutePath().normalize() + "\n" + line;
        synchronized (LOCK) {
            if (Objects.equals(key, lastWrittenKey)) {
                return;
            }
            try {
                Files.createDirectories(directory);
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                lastWrittenKey = key;
            } catch (IOException ex) {
                maybeLogIoError(file, ex);
            }
        }
    }

    static String toSingleLine(String statusJson) {
        if (statusJson == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(statusJson.length());
        for (int i = 0; i < statusJson.length(); i++) {
            char c = statusJson.charAt(i);
            if (c == '\r' || c == '\n') {
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    static void resetDuplicateGuardForTests() {
        synchronized (LOCK) {
            lastWrittenKey = null;
        }
    }

    private static void maybeLogIoError(Path file, IOException ex) {
        Instant now = Instant.now();
        if (lastIoErrorLog != null && Duration.between(lastIoErrorLog, now).getSeconds() < 30) {
            return;
        }
        lastIoErrorLog = now;
        String msg = ex.getMessage();
        if (msg == null) {
            msg = ex.getClass().getSimpleName();
        }
        System.err.println("[EDO] StatusHistoryLog: could not append \""
                + file.toAbsolutePath() + "\": " + msg);
    }
}
