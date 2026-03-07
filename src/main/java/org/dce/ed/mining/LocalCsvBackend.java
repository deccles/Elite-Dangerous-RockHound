package org.dce.ed.mining;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Column order: Run, Timestamp, Type, Percentage, Before Amount, After Amount, Actual (difference), Body, Commander (9 columns).
 * Legacy 7-column files are supported on read.
 */
public final class LocalCsvBackend implements ProspectorLogBackend {

    private static final String HEADER = "run,timestamp,material,percent,before amount,after amount,actual,body,commander";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);

    private final Path csvPath;

    public LocalCsvBackend(Path csvPath) {
        this.csvPath = csvPath != null ? csvPath : defaultPath();
    }

    public LocalCsvBackend() {
        this(defaultPath());
    }

    private static Path defaultPath() {
        return Paths.get(System.getProperty("user.home", ""), "EDO").resolve("prospector_log.csv");
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
                String line = r.getRun() + ","
                    + MiningTabPanel.csvEscape(tsStr) + ","
                    + MiningTabPanel.csvEscape(material) + ","
                    + formatDouble(r.getPercent()) + ","
                    + formatDouble(r.getBeforeAmount()) + ","
                    + formatDouble(r.getAfterAmount()) + ","
                    + formatDouble(r.getDifference()) + ","
                    + MiningTabPanel.csvEscape(body) + ","
                    + MiningTabPanel.csvEscape(commander);
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

    @Override
    public List<ProspectorLogRow> loadRows() {
        if (!Files.exists(csvPath)) {
            return List.of();
        }
        List<ProspectorLogRow> out = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
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
                // New 9-column: run,timestamp,material,percent,before amount,after amount,actual,body,commander
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() < 9) continue;
                    try {
                        int run = Integer.parseInt(cols.get(0).trim());
                        Instant ts = parseTimestamp(cols.get(1).trim());
                        String material = cols.get(2).trim();
                        double percent = parseDouble(cols.get(3), 0.0);
                        double before = parseDouble(cols.get(4), 0.0);
                        double after = parseDouble(cols.get(5), 0.0);
                        double diff = parseDouble(cols.get(6), 0.0);
                        String fullBodyName = cols.get(7).trim();
                        String commander = cols.get(8).trim();
                        out.add(new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander));
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
        try {
            return java.time.LocalDateTime.parse(s, DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US))
                .atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(s, DateTimeFormatter.ofPattern("M/d/yyyy H:m:s", Locale.US))
                    .atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception e2) {
                return null;
            }
        }
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
