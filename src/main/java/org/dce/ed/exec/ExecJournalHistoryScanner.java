package org.dce.ed.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/** Reads past journal files for Exec filter UI (not used at trigger dispatch). */
public final class ExecJournalHistoryScanner {

    public static final int DEFAULT_MAX_FILES = 3;
    public static final int DEFAULT_MAX_MATCHES = 500;

    private ExecJournalHistoryScanner() {
    }

    public record JournalExample(JsonObject json, Instant timestamp, Path sourceFile, int lineNumber) {
    }

    public static List<JournalExample> scan(String eventName) throws IOException {
        return scan(eventName, DEFAULT_MAX_FILES, DEFAULT_MAX_MATCHES);
    }

    public static List<JournalExample> scan(String eventName, int maxFiles, int maxMatches) throws IOException {
        if (eventName == null || eventName.isBlank()) {
            return List.of();
        }
        Path journalDir = resolveJournalDirectory();
        if (journalDir == null || !Files.isDirectory(journalDir)) {
            return List.of();
        }
        List<Path> files = listJournalFiles(journalDir, Math.max(1, maxFiles));
        List<JournalExample> matches = new ArrayList<>();
        for (Path file : files) {
            if (matches.size() >= maxMatches) {
                break;
            }
            scanFile(file, eventName, matches, maxMatches);
        }
        matches.sort(Comparator.comparing(JournalExample::timestamp).reversed());
        if (matches.size() > maxMatches) {
            return matches.subList(0, maxMatches);
        }
        return matches;
    }

    static Path resolveJournalDirectory() {
        Path dir = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
        if (dir != null && Files.isDirectory(dir)) {
            return dir;
        }
        String profile = System.getenv("USERPROFILE");
        Path home = profile != null && !profile.isBlank()
                ? Path.of(profile)
                : Path.of(System.getProperty("user.home"));
        return home.resolve("Saved Games")
                .resolve("Frontier Developments")
                .resolve("Elite Dangerous");
    }

    private static List<Path> listJournalFiles(Path journalDir, int maxFiles) throws IOException {
        try (Stream<Path> stream = Files.list(journalDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("Journal.") && name.endsWith(".log");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            if (files.size() <= maxFiles) {
                return files;
            }
            return files.subList(files.size() - maxFiles, files.size());
        }
    }

    private static void scanFile(Path file, String eventName, List<JournalExample> out, int maxMatches)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            if (out.size() >= maxMatches) {
                return;
            }
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            JsonObject obj;
            try {
                obj = JsonParser.parseString(line.trim()).getAsJsonObject();
            } catch (JsonSyntaxException | IllegalStateException | ClassCastException ex) {
                continue;
            }
            if (!eventName.equals(ExecJournalJsonMatcher.jsonFieldAsString(obj, "event"))) {
                continue;
            }
            Instant ts = parseTimestamp(obj);
            out.add(new JournalExample(obj, ts, file, i + 1));
        }
    }

    static Instant parseTimestamp(JsonObject obj) {
        String raw = ExecJournalJsonMatcher.jsonFieldAsString(obj, "timestamp");
        if (raw == null || raw.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return Instant.EPOCH;
        }
    }
}
