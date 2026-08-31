package org.dce.ed.route.pacing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Append-only transcript of EDSM pacing experiment runs for later analysis.
 * Lives in the process working directory as {@code edsm-pacing-experiment.log}.
 */
public final class EdsmPacingExperimentLog {
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String BANNER = "=".repeat(80);
    private static final String RULE = "-".repeat(80);

    private final Path file;
    private final Object lock = new Object();
    private volatile String lastError;

    public EdsmPacingExperimentLog() {
        this(defaultFile());
    }

    public EdsmPacingExperimentLog(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.dir", ".")).resolve("edsm-pacing-experiment.log");
    }

    public Path file() {
        return file;
    }

    public String lastError() {
        return lastError;
    }

    public void startRun(LocalDateTime started, List<EdsmPacingExperimentSettings.BatchSpec> batches,
            int systemCount, int expandedWaves) {
        appendBlock(formatRunHeader(started, batches, systemCount, expandedWaves));
    }

    public void append(String line) {
        String text = line == null ? "" : line;
        appendBlock(LocalDateTime.now().format(TIME) + "  " + text);
    }

    public void endRun(LocalDateTime ended, long elapsedMs, String outcome) {
        appendBlock(formatRunFooter(ended, elapsedMs, outcome));
    }

    public static String formatBatchLine(int number, EdsmPacingExperimentSettings.BatchSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return "batch " + number
                + ": count=" + spec.count()
                + " concurrent=" + spec.concurrent()
                + " rest=" + spec.restSeconds() + "s"
                + " delay=" + spec.delayMs() + "ms"
                + " repeat=" + spec.repeats();
    }

    public static String formatRunHeader(LocalDateTime started,
            List<EdsmPacingExperimentSettings.BatchSpec> batches, int systemCount, int expandedWaves) {
        Objects.requireNonNull(started, "started");
        List<EdsmPacingExperimentSettings.BatchSpec> rows = batches != null ? batches : List.of();
        StringBuilder header = new StringBuilder();
        header.append('\n').append(BANNER).append('\n');
        header.append("RUN STARTED  ").append(started.format(TIME)).append('\n');
        header.append("systems=").append(systemCount)
                .append("  configuredBatches=").append(rows.size())
                .append("  expandedWaves=").append(expandedWaves).append('\n');
        for (int i = 0; i < rows.size(); i++) {
            EdsmPacingExperimentSettings.BatchSpec spec = rows.get(i);
            if (spec != null) {
                header.append("  ").append(formatBatchLine(i + 1, spec)).append('\n');
            }
        }
        header.append(RULE);
        return header.toString();
    }

    public static String formatRunFooter(LocalDateTime ended, long elapsedMs, String outcome) {
        Objects.requireNonNull(ended, "ended");
        long totalSeconds = Math.max(0L, elapsedMs) / 1_000L;
        String elapsed = String.format("%02d:%02d", Long.valueOf(totalSeconds / 60L),
                Long.valueOf(totalSeconds % 60L));
        String status = outcome == null || outcome.isBlank() ? "ended" : outcome;
        return "RUN ENDED    " + ended.format(TIME) + "  elapsed=" + elapsed + "  " + status
                + "\n" + BANNER;
    }

    private void appendBlock(String text) {
        synchronized (lock) {
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                String toWrite = text.endsWith("\n") ? text : text + "\n";
                Files.writeString(file, toWrite, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                lastError = null;
            } catch (IOException ex) {
                lastError = ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? " — " + ex.getMessage() : "");
            }
        }
    }
}
