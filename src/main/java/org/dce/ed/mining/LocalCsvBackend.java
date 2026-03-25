package org.dce.ed.mining;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.dce.ed.MiningTabPanel;

/**
 * Prospector log backend that writes to and reads from a local CSV file.
 * Column order: Run, Asteroid, Timestamp, Type, Percentage, Before Amount, After Amount, Actual, Core, Body, Duds, Commander, Start time, End time (14 columns).
 * Legacy 7-, 9-, and 12-column files are supported on read.
 */
public final class LocalCsvBackend implements ProspectorLogBackend {

    private static final String HEADER = "run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,start time,end time";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);

    private final Path csvPath;

    public LocalCsvBackend(Path csvPath) {
        this.csvPath = csvPath != null ? csvPath : defaultPath();
        migrateLegacyPathIfNeeded(this.csvPath);
    }

    public LocalCsvBackend() {
        this(defaultPath());
    }

    private static Path defaultPath() {
        return Paths.get(System.getProperty("user.home", ""), ".edo").resolve("prospector_log.csv");
    }

    private static Path legacyDefaultPath() {
        return Paths.get(System.getProperty("user.home", ""), "EDO").resolve("prospector_log.csv");
    }

    private static void migrateLegacyPathIfNeeded(Path targetPath) {
        if (targetPath == null) {
            return;
        }
        // Only migrate when the caller is using the app's default CSV path.
        // If a custom CSV path is provided (e.g. tests, tools), never copy in user-home
        // legacy data behind their back; that breaks expectations and test isolation.
        Path newDefault = defaultPath();
        boolean targetIsAppDefault = targetPath.toAbsolutePath().normalize()
                .equals(newDefault.toAbsolutePath().normalize());
        if (!targetIsAppDefault) {
            return;
        }
        Path legacy = legacyDefaultPath();
        if (Files.exists(targetPath) || !Files.exists(legacy)) {
            return;
        }
        try {
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(legacy, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[EDO][Mining] Migrated prospector CSV to " + targetPath);
        } catch (Exception ex) {
            System.err.println("[EDO][Mining] Failed to migrate prospector CSV to " + targetPath + ": " + ex.getMessage());
        }
    }

    @Override
    public void appendRows(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try {
            Path parent = csvPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean newFile = !Files.exists(csvPath);
            if (newFile) {
                Files.writeString(csvPath, HEADER + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            ZoneId zone = ZoneId.systemDefault();
            for (ProspectorLogRow r : rows) {
                String tsStr = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(TIMESTAMP_FORMAT) : "";
                if (tsStr == null || tsStr.isEmpty()) tsStr = "-";
                String body = r.getFullBodyName();
                if (body == null || body.isEmpty()) body = "-";
                String commander = r.getCommanderName();
                if (commander == null || commander.isEmpty()) commander = "-";
                String material = r.getMaterial();
                if (material == null || material.isEmpty()) material = "-";
                String asteroid = r.getAsteroidId() != null ? r.getAsteroidId() : "";
                if (asteroid.isEmpty()) asteroid = "-";
                String core = r.getCoreType() != null ? r.getCoreType() : "";
                if (core.isEmpty()) core = "-";
                String startStr = r.getRunStartTime() != null ? r.getRunStartTime().atZone(zone).format(TIMESTAMP_FORMAT) : "";
                String endStr = r.getRunEndTime() != null ? r.getRunEndTime().atZone(zone).format(TIMESTAMP_FORMAT) : "";
                String line = r.getRun() + ","
                    + MiningTabPanel.csvEscape(asteroid) + ","
                    + MiningTabPanel.csvEscape(tsStr) + ","
                    + MiningTabPanel.csvEscape(material) + ","
                    + formatDouble(r.getPercent()) + ","
                    + formatDouble(r.getBeforeAmount()) + ","
                    + formatDouble(r.getAfterAmount()) + ","
                    + formatDouble(r.getDifference()) + ","
                    + MiningTabPanel.csvEscape(core) + ","
                    + MiningTabPanel.csvEscape(body) + ","
                    + r.getDuds() + ","
                    + MiningTabPanel.csvEscape(commander) + ","
                    + MiningTabPanel.csvEscape(startStr) + ","
                    + MiningTabPanel.csvEscape(endStr);
                Files.writeString(csvPath, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to append prospector log", e);
        }
    }

    private static String formatDouble(double v) {
        if (Double.isNaN(v)) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", v);
    }

    /** Build a 14-column CSV line with proper escaping (for updateRunEndTime). */
    private static String buildCsvLine14(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            if (i > 0) sb.append(',');
            String v = i < cols.size() ? cols.get(i) : "";
            if (i == 0) sb.append(v); // run number
            else if (i == 4 || i == 5 || i == 6 || i == 7) sb.append(v); // numeric
            else if (i == 9) sb.append(v); // duds
            else sb.append(MiningTabPanel.csvEscape(v != null ? v : ""));
        }
        return sb.toString();
    }

    @Override
    public List<ProspectorLogRow> loadRows() {
        Path readPath = csvPath;
        Path newDefault = defaultPath();
        boolean targetIsAppDefault = csvPath != null
                && csvPath.toAbsolutePath().normalize().equals(newDefault.toAbsolutePath().normalize());
        if (!Files.exists(readPath)) {
            // Only fall back to legacy default when the caller is using the app's default path.
            if (targetIsAppDefault) {
                Path legacy = legacyDefaultPath();
                if (Files.exists(legacy)) {
                    readPath = legacy;
                }
            }
        }
        if (!Files.exists(readPath)) {
            return List.of();
        }
        List<ProspectorLogRow> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(readPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return List.of();
            }
            boolean legacy = isLegacyFormat(header);
            if (legacy) {
                List<String[]> rawRows = new ArrayList<>();
                if (!looksLikeLegacyHeader(header)) {
                    List<String> cols = parseCsvLine(header);
                    if (cols.size() >= 7) {
                        rawRows.add(cols.toArray(new String[0]));
                    }
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() >= 7) {
                        rawRows.add(cols.toArray(new String[0]));
                    }
                }
                out.addAll(inferRunsFromLegacy(rawRows));
            } else {
                // New 14-column: run,asteroid,timestamp,material,percent,before,after,actual,core,body,duds,commander,start time,end time
                // 12-column: run,asteroid,timestamp,material,percent,before,after,actual,core,body,duds,commander
                // Legacy 9-column: run,timestamp,material,percent,before,after,actual,body,commander (no asteroid, core, duds)
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() < 9) continue;
                    try {
                        int run = Integer.parseInt(cols.get(0).trim());
                        if (cols.size() >= 12) {
                            String asteroidId = cols.get(1).trim();
                            Instant ts = parseTimestamp(cols.get(2).trim());
                            String material = cols.get(3).trim();
                            double percent = parseDouble(cols.get(4), 0.0);
                            double before = parseDouble(cols.get(5), 0.0);
                            double after = parseDouble(cols.get(6), 0.0);
                            double diff = parseDouble(cols.get(7), 0.0);
                            String core = cols.get(8).trim();
                            String fullBodyName = cols.get(9).trim();
                            int duds = parseInt(cols.get(10), 0);
                            String commander = cols.get(11).trim();
                            Instant runStart = (cols.size() >= 14 && cols.get(12) != null && !cols.get(12).trim().isEmpty()) ? parseTimestamp(cols.get(12).trim()) : null;
                            Instant runEnd = (cols.size() >= 14 && cols.get(13) != null && !cols.get(13).trim().isEmpty()) ? parseTimestamp(cols.get(13).trim()) : null;
                            out.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff, commander, core, duds, runStart, runEnd));
                        } else {
                            Instant ts = parseTimestamp(cols.get(1).trim());
                            String material = cols.get(2).trim();
                            double percent = parseDouble(cols.get(3), 0.0);
                            double before = parseDouble(cols.get(4), 0.0);
                            double after = parseDouble(cols.get(5), 0.0);
                            double diff = parseDouble(cols.get(6), 0.0);
                            String fullBodyName = cols.get(7).trim();
                            String commander = cols.get(8).trim();
                            out.add(new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander));
                        }
                    } catch (Exception e) {
                        // skip malformed line
                    }
                }
            }
        } catch (Exception e) {
            // return what we have so far, or empty
        }
        out.sort(Comparator.comparing(ProspectorLogRow::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    @Override
    public void updateRunEndTime(String commander, int run, Instant endTime) {
        if (endTime == null || !Files.exists(csvPath)) {
            return;
        }
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                String header = reader.readLine();
                if (header == null) return;
                lines.add(header);
                ZoneId zone = ZoneId.systemDefault();
                String endStr = endTime.atZone(zone).format(TIMESTAMP_FORMAT);
                String cmdr = commander != null ? commander.trim() : "";
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        lines.add(line);
                        continue;
                    }
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() >= 14) {
                        int rowRun = parseInt(cols.get(0).trim(), 0);
                        String rowCommander = cols.get(11).trim();
                        String rowStart = cols.get(12).trim();
                        if (rowRun == run && rowCommander.equals(cmdr) && !rowStart.isEmpty()) {
                            cols.set(13, endStr);
                            line = buildCsvLine14(cols);
                        }
                    }
                    lines.add(line);
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String l : lines) {
                    writer.write(l);
                    writer.write('\n');
                }
            }
        } catch (Exception e) {
            // don't break UI; caller may log
        }
    }

    /** True if header looks like legacy (no "run" or 7 columns). */
    private static boolean isLegacyFormat(String header) {
        if (header == null) return false;
        String lower = header.toLowerCase(Locale.ROOT);
        if (lower.contains("run") && lower.contains("body")) {
            return false;
        }
        List<String> cols = parseCsvLine(header);
        return cols.size() <= 7;
    }

    /** True if the first line looks like a legacy header row (e.g. "timestamp,material,...") so we skip it. */
    private static boolean looksLikeLegacyHeader(String firstLine) {
        if (firstLine == null || firstLine.isBlank()) return true;
        List<String> cols = parseCsvLine(firstLine);
        if (cols.isEmpty()) return true;
        String first = cols.get(0).toLowerCase(Locale.ROOT);
        return first.contains("timestamp") || first.contains("date") || first.contains("time");
    }

    private static final long GAP_MINUTES = 10;
    private static final long GAP_MS = GAP_MINUTES * 60 * 1000;

    /** Parse legacy 7-col rows: timestamp,material,percent,before,after,difference,email. Sort by time, assign run by >10 min gap. */
    private static List<ProspectorLogRow> inferRunsFromLegacy(List<String[]> rawRows) {
        List<LegacyRow> rows = new ArrayList<>();
        for (String[] cols : rawRows) {
            if (cols.length < 7) continue;
            try {
                Instant ts = parseTimestamp(cols[0].trim());
                String material = cols[1].trim();
                double percent = parseDouble(cols[2], 0.0);
                double before = parseDouble(cols[3], 0.0);
                double after = parseDouble(cols[4], 0.0);
                double diff = parseDouble(cols[5], 0.0);
                String commander = cols[6].trim();
                rows.add(new LegacyRow(ts, material, percent, before, after, diff, commander));
            } catch (Exception ignored) {
            }
        }
        rows.sort(Comparator.comparing(LegacyRow::getTs, Comparator.nullsLast(Comparator.naturalOrder())));
        int run = 1;
        Instant lastTs = null;
        List<ProspectorLogRow> out = new ArrayList<>();
        for (LegacyRow r : rows) {
            if (lastTs != null && r.ts != null && r.ts.toEpochMilli() - lastTs.toEpochMilli() > GAP_MS) {
                run++;
            }
            lastTs = r.ts;
            out.add(new ProspectorLogRow(run, "", r.ts, r.material, r.percent, r.before, r.after, r.diff, r.commander));
        }
        return out;
    }

    private static final class LegacyRow {
        final Instant ts;
        final String material;
        final double percent, before, after, diff;
        final String commander;

        LegacyRow(Instant ts, String material, double percent, double before, double after, double diff, String commander) {
            this.ts = ts;
            this.material = material;
            this.percent = percent;
            this.before = before;
            this.after = after;
            this.diff = diff;
            this.commander = commander != null ? commander : "";
        }

        Instant getTs() {
            return ts;
        }
    }

    private static Instant parseTimestamp(String s) {
        if (s == null || s.isBlank() || "-".equals(s)) {
            return null;
        }
        s = s.trim();
        // Try ISO-8601 first (e.g. 2024-01-15T10:30:00Z or 2024-01-15T10:30:00)
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {
        }
        java.time.format.DateTimeFormatter[] formats = {
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy H:m:s", Locale.US),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("d/M/yyyy H:m:s", Locale.US),
        };
        for (DateTimeFormatter fmt : formats) {
            try {
                return java.time.LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }
}
