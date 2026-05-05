package org.dce.ed.mining;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dce.ed.MiningTabPanel;

/**
 * Prospector log backend that writes per-commander CSV files in a base directory (default {@code ~/.edo/}).
 * Each commander's rows live in their own {@code CMDR <name>.csv} file (matching the Google Sheets per-commander
 * tab naming convention from {@link MiningSheetTitles}). The legacy single-file {@code prospector_log.csv} is
 * intentionally ignored — Both-mode sync runs through {@link ProspectorBothModeSync} which expects per-commander
 * scoping on both sides.
 *
 * <p>Schema (16 columns, mirroring the Sheets layout for everything except the Sheets-only System/Body split):</p>
 * <pre>run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,ship,comments,start time,end time</pre>
 *
 * <p>Legacy 7-, 9-, 12-, 14-, and 15-column files are tolerated on read for tests and historical files passed
 * directly via {@link #LocalCsvBackend(Path)} — but the per-commander production path never reads the old flat
 * file.</p>
 */
public final class LocalCsvBackend implements ProspectorLogBackend {

    private static final String HEADER =
            "run,asteroid,timestamp,material,percent,before amount,after amount,actual,core,body,duds,commander,ship,comments,start time,end time";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);

    /** New CSV layout column indexes (16 columns total). */
    private static final class Col {
        static final int RUN = 0;
        static final int ASTEROID = 1;
        static final int TIMESTAMP = 2;
        static final int MATERIAL = 3;
        static final int PERCENT = 4;
        static final int BEFORE = 5;
        static final int AFTER = 6;
        static final int ACTUAL = 7;
        static final int CORE = 8;
        static final int BODY = 9;
        static final int DUDS = 10;
        static final int COMMANDER = 11;
        static final int SHIP = 12;
        static final int COMMENTS = 13;
        static final int START = 14;
        static final int END = 15;
        static final int WIDTH = 16;
    }

    /**
     * Either a base directory containing {@code CMDR *.csv} files (production / Both-mode sync), or a single CSV
     * file (legacy tests / direct-file callers). When this is a regular file we operate on it as one flat log so
     * existing direct-file tests keep passing; when it's a directory (or doesn't exist yet), we use the per-
     * commander layout.
     */
    private final Path basePath;
    private final boolean singleFileMode;

    public LocalCsvBackend(Path path) {
        this.basePath = path != null ? path : defaultBaseDir();
        this.singleFileMode = path != null && Files.isRegularFile(path);
    }

    public LocalCsvBackend() {
        this(defaultBaseDir());
    }

    private static Path defaultBaseDir() {
        return Paths.get(System.getProperty("user.home", ""), ".edo");
    }

    /** True if this backend is operating in legacy single-file mode (a file path was provided). */
    boolean isSingleFileMode() {
        return singleFileMode;
    }

    /**
     * Per-commander CSV file path for the given commander name in directory mode. Uses the same sanitized
     * {@code CMDR <name>} title as {@link MiningSheetTitles#sheetTitleForCommander} so file and tab names align.
     */
    Path csvFileForCommander(String commander) {
        if (singleFileMode) {
            return basePath;
        }
        String title = MiningSheetTitles.sheetTitleForCommander(commander);
        return basePath.resolve(title + ".csv");
    }

    @Override
    public void appendRows(List<ProspectorLogRow> rows) {
        ProspectorWriteResult r = appendRowsResult(rows);
        if (r != null && !r.isOk()) {
            Throwable c = r.getCause();
            if (c instanceof RuntimeException re) throw re;
            throw new RuntimeException(r.getMessage(), c);
        }
    }

    @Override
    public List<ProspectorLogRow> loadRows() {
        ProspectorLoadResult r = loadRowsWithStatus();
        return r != null ? r.getRows() : Collections.emptyList();
    }

    @Override
    public void updateRunEndTime(String commander, int run, Instant endTime) {
        updateRunEndTimeResult(commander, run, endTime);
    }

    @Override
    public ProspectorWriteResult appendRowsResult(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ProspectorWriteResult.ok();
        }
        try {
            Map<String, List<ProspectorLogRow>> grouped = groupByCommander(rows);
            ZoneId zone = ZoneId.systemDefault();
            for (Map.Entry<String, List<ProspectorLogRow>> e : grouped.entrySet()) {
                Path file = csvFileForCommander(e.getKey());
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                boolean newFile = !Files.exists(file);
                if (newFile) {
                    Files.writeString(file, HEADER + "\n", StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
                StringBuilder sb = new StringBuilder();
                for (ProspectorLogRow r : e.getValue()) {
                    sb.append(formatRow(r, zone)).append('\n');
                }
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            return ProspectorWriteResult.failure(e.getMessage() != null ? e.getMessage() : "CSV write failed", e);
        }
    }

    @Override
    public ProspectorWriteResult upsertRowsResult(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ProspectorWriteResult.ok();
        }
        try {
            Map<String, List<ProspectorLogRow>> grouped = groupByCommander(rows);
            ZoneId zone = ZoneId.systemDefault();
            for (Map.Entry<String, List<ProspectorLogRow>> e : grouped.entrySet()) {
                upsertCommanderFile(e.getKey(), e.getValue(), zone);
            }
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            return ProspectorWriteResult.failure(e.getMessage() != null ? e.getMessage() : "CSV upsert failed", e);
        }
    }

    private void upsertCommanderFile(String commander, List<ProspectorLogRow> incomingForCommander, ZoneId zone)
            throws Exception {
        Path file = csvFileForCommander(commander);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Read existing rows (or start fresh).
        List<ProspectorLogRow> existing;
        if (Files.exists(file)) {
            existing = readRowsFromFile(file);
        } else {
            existing = new ArrayList<>();
        }

        // Build a key index over existing rows so we can find matches and merge in place.
        Map<UpsertKey, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < existing.size(); i++) {
            idx.put(UpsertKey.of(existing.get(i)), i);
        }

        for (ProspectorLogRow r : incomingForCommander) {
            if (r == null) {
                continue;
            }
            UpsertKey key = UpsertKey.of(r);
            Integer pos = idx.get(key);
            if (pos != null) {
                ProspectorLogRow merged = mergeRows(existing.get(pos), r);
                existing.set(pos, merged);
            } else {
                existing.add(r);
                idx.put(key, existing.size() - 1);
            }
        }

        // Stable sort: run, asteroid letter (A,B,...AA), then timestamp.
        existing.sort(Comparator
                .comparingInt(ProspectorLogRow::getRun)
                .thenComparingInt(rr -> ProspectorRowMergeRules.asteroidIdSortIndex(rr.getAsteroidId()))
                .thenComparing((ProspectorLogRow rr) -> rr.getTimestamp() == null ? Instant.EPOCH : rr.getTimestamp())
                .thenComparing(ProspectorLogRow::getMaterial, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        // Rewrite the entire file (header + rows).
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        for (ProspectorLogRow r : existing) {
            sb.append(formatRow(r, zone)).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Apply shared merge rules: incoming wins where set, except core/comments/duds which use shared helpers. */
    private static ProspectorLogRow mergeRows(ProspectorLogRow existing, ProspectorLogRow incoming) {
        String core = ProspectorRowMergeRules.mergeCore(incoming.getCoreType(), existing.getCoreType());
        if ("-".equals(core)) {
            // ProspectorRowMergeRules returns "-" for blank+blank to match Sheets storage; in the Java row model
            // we keep an empty string so downstream code that checks isBlank() works the same way as before.
            core = "";
        }
        String comments = ProspectorRowMergeRules.mergeComments(incoming.getComments(), existing.getComments());
        int duds = ProspectorRowMergeRules.mergeDuds(incoming.getDuds(), existing.getDuds());

        // Run start: never overwrite a meaningful existing start with null/blank.
        Instant runStart = incoming.getRunStartTime();
        if (runStart == null) {
            runStart = existing.getRunStartTime();
        }
        // Run end: incoming wins if non-null, else keep existing.
        Instant runEnd = incoming.getRunEndTime() != null ? incoming.getRunEndTime() : existing.getRunEndTime();

        // Body / ship: prefer incoming when non-blank; keep existing otherwise.
        String body = !blank(incoming.getFullBodyName()) ? incoming.getFullBodyName() : existing.getFullBodyName();
        String ship = !blank(incoming.getShipType()) ? incoming.getShipType() : existing.getShipType();

        return new ProspectorLogRow(
                incoming.getRun(),
                incoming.getAsteroidId(),
                body,
                incoming.getTimestamp() != null ? incoming.getTimestamp() : existing.getTimestamp(),
                incoming.getMaterial(),
                incoming.getPercent(),
                incoming.getBeforeAmount(),
                incoming.getAfterAmount(),
                incoming.getDifference(),
                incoming.getCommanderName(),
                ship,
                core,
                duds,
                runStart,
                runEnd,
                comments);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String formatRow(ProspectorLogRow r, ZoneId zone) {
        String tsStr = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(TIMESTAMP_FORMAT) : "";
        if (tsStr.isEmpty()) tsStr = "-";
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
        String ship = r.getShipType();
        if (ship == null || ship.isEmpty()) ship = "-";
        String comments = r.getComments() != null ? r.getComments() : "";
        return r.getRun() + ","
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
                + MiningTabPanel.csvEscape(ship) + ","
                + MiningTabPanel.csvEscape(comments) + ","
                + MiningTabPanel.csvEscape(startStr) + ","
                + MiningTabPanel.csvEscape(endStr);
    }

    private static String formatDouble(double v) {
        if (Double.isNaN(v)) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static Map<String, List<ProspectorLogRow>> groupByCommander(List<ProspectorLogRow> rows) {
        Map<String, List<ProspectorLogRow>> out = new LinkedHashMap<>();
        for (ProspectorLogRow r : rows) {
            if (r == null) {
                continue;
            }
            String key = r.getCommanderName() != null ? r.getCommanderName().trim() : "";
            if (key.isEmpty() || "-".equals(key)) {
                key = "-";
            }
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    @Override
    public ProspectorLoadResult loadRowsWithStatus() {
        try {
            if (singleFileMode) {
                if (!Files.exists(basePath)) {
                    return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
                }
                List<ProspectorLogRow> rows = readRowsFromFile(basePath);
                rows.sort(Comparator.comparing(ProspectorLogRow::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                return new ProspectorLoadResult(
                        rows.isEmpty() ? ProspectorLoadResult.Status.EMPTY_SHEET : ProspectorLoadResult.Status.OK,
                        rows);
            }
            if (!Files.isDirectory(basePath)) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<Path> files = listCommanderCsvFiles(basePath);
            if (files.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<ProspectorLogRow> all = new ArrayList<>();
            for (Path f : files) {
                all.addAll(readRowsFromFile(f));
            }
            all.sort(Comparator.comparing(ProspectorLogRow::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            if (all.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, all);
        } catch (Exception e) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    e.getMessage() != null ? e.getMessage() : "CSV read failed");
        }
    }

    @Override
    public ProspectorLoadResult loadRowsWithStatusForCommander(String commander) {
        try {
            Path file = csvFileForCommander(commander);
            if (!Files.exists(file)) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<ProspectorLogRow> rows = readRowsFromFile(file);
            // In single-file mode we still filter by commander since the file may contain many.
            if (singleFileMode && commander != null && !commander.isBlank()) {
                String want = commander.trim();
                rows.removeIf(r -> r == null || !want.equalsIgnoreCase(r.getCommanderName().trim()));
            }
            rows.sort(Comparator.comparing(ProspectorLogRow::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            if (rows.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, rows);
        } catch (Exception e) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    e.getMessage() != null ? e.getMessage() : "CSV read failed");
        }
    }

    private static List<Path> listCommanderCsvFiles(Path dir) throws Exception {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "CMDR *.csv")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    public ProspectorWriteResult updateRunEndTimeResult(String commander, int run, Instant endTime) {
        if (endTime == null) {
            return ProspectorWriteResult.ok();
        }
        try {
            Path file = csvFileForCommander(commander);
            if (!Files.exists(file)) {
                return ProspectorWriteResult.ok();
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String header = reader.readLine();
                if (header == null) return ProspectorWriteResult.ok();
                lines.add(header);
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            ZoneId zone = ZoneId.systemDefault();
            String endStr = endTime.atZone(zone).format(TIMESTAMP_FORMAT);
            String cmdr = commander != null ? commander.trim() : "";

            // Prefer asteroid A with a non-blank start cell, then any A row, then any matching row.
            int updateLine = findCanonicalRunEndLine(lines, run, cmdr, true, true);
            if (updateLine < 0) {
                updateLine = findCanonicalRunEndLine(lines, run, cmdr, true, false);
            }
            if (updateLine < 0) {
                updateLine = findCanonicalRunEndLine(lines, run, cmdr, false, true);
            }

            if (updateLine >= 0) {
                List<String> cols = parseCsvLine(lines.get(updateLine));
                cols = upgradeRowToWidth16(cols);
                cols.set(Col.END, endStr);
                lines.set(updateLine, buildCsvLine16(cols));
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                for (String l : lines) {
                    writer.write(l);
                    writer.write('\n');
                }
            }
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            return ProspectorWriteResult.failure(e.getMessage() != null ? e.getMessage() : "CSV update failed", e);
        }
    }

    /** Scan {@code lines} (1+ are data) for a row matching {@code run}/{@code cmdr}; returns -1 if none. */
    private static int findCanonicalRunEndLine(List<String> lines, int run, String cmdr, boolean requireMeaningfulStart,
            boolean requireAsteroidA) {
        for (int li = 1; li < lines.size(); li++) {
            String line = lines.get(li);
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> cols = parseCsvLine(line);
            if (cols.size() < 12) {
                continue;
            }
            int rowRun = parseInt(cols.get(Col.RUN).trim(), 0);
            String rowCommander = colAt(cols, Col.COMMANDER, columnIndexCommanderForLegacy(cols)).trim();
            String asteroid = colAt(cols, Col.ASTEROID, 1).trim();
            int startIdx = legacyAwareStartIndex(cols);
            String rowStart = startIdx >= 0 && cols.size() > startIdx ? cols.get(startIdx).trim() : "";
            if (rowRun != run || !rowCommander.equals(cmdr)) {
                continue;
            }
            if (requireAsteroidA && !"A".equalsIgnoreCase(asteroid)) {
                continue;
            }
            if (requireMeaningfulStart && (rowStart.isEmpty() || "-".equals(rowStart))) {
                continue;
            }
            return li;
        }
        return -1;
    }

    private static String colAt(List<String> cols, int newIdx, int legacyFallbackIdx) {
        if (cols.size() >= Col.WIDTH) {
            return cols.size() > newIdx ? cols.get(newIdx) : "";
        }
        return cols.size() > legacyFallbackIdx ? cols.get(legacyFallbackIdx) : "";
    }

    /** Commander column index in legacy 14/15-col layouts where it sat at position 11. */
    private static int columnIndexCommanderForLegacy(List<String> cols) {
        return 11;
    }

    /** Start time column index across the supported widths (16-col new, 15-col, 14-col). */
    private static int legacyAwareStartIndex(List<String> cols) {
        if (cols.size() >= Col.WIDTH) {
            return Col.START; // 14
        }
        if (cols.size() >= 15) {
            return 13; // legacy 15-col with ship but no comments
        }
        if (cols.size() >= 14) {
            return 12; // legacy 14-col with no ship
        }
        return -1;
    }

    /** Pad / re-encode a parsed legacy row into the canonical 16-column shape (inserts a blank Comments col). */
    private static List<String> upgradeRowToWidth16(List<String> cols) {
        List<String> out = new ArrayList<>(cols);
        // 14 col legacy: run,asteroid,timestamp,material,percent,before,after,actual,core,body,duds,commander,start,end
        // No ship, no comments. Insert "-" at index 12 (ship), then "" at 13 (comments).
        if (out.size() == 14) {
            out.add(12, "-"); // ship
            out.add(13, ""); // comments
        }
        // 15-col legacy: ship at 12; insert "" at 13 (comments) before start/end.
        if (out.size() == 15) {
            out.add(13, ""); // comments
        }
        while (out.size() < Col.WIDTH) {
            out.add("");
        }
        return out;
    }

    /** Build a 16-column CSV line with proper escaping (used by updateRunEndTime). */
    private static String buildCsvLine16(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Col.WIDTH; i++) {
            if (i > 0) sb.append(',');
            String v = i < cols.size() ? cols.get(i) : "";
            if (i == Col.RUN || i == Col.PERCENT || i == Col.BEFORE || i == Col.AFTER || i == Col.ACTUAL
                    || i == Col.DUDS) {
                sb.append(v);
            } else {
                sb.append(MiningTabPanel.csvEscape(v != null ? v : ""));
            }
        }
        return sb.toString();
    }

    /** Read all data rows from one CSV file, applying legacy header normalization. */
    private static List<ProspectorLogRow> readRowsFromFile(Path file) throws Exception {
        List<ProspectorLogRow> out = new ArrayList<>();
        // Read raw text and split into records using a quote-aware tokenizer so embedded newlines in escaped
        // comments are not split into separate rows.
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        List<String> records = splitCsvRecords(raw);
        if (records.isEmpty()) {
            return out;
        }
        String header = records.get(0);
        boolean legacy = isLegacyFormat(header);
        if (legacy) {
            List<String[]> rawRows = new ArrayList<>();
            if (!looksLikeLegacyHeader(header)) {
                List<String> cols = parseCsvLine(header);
                if (cols.size() >= 7) {
                    rawRows.add(cols.toArray(new String[0]));
                }
            }
            for (int i = 1; i < records.size(); i++) {
                String rec = records.get(i);
                if (rec.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(rec);
                if (cols.size() >= 7) {
                    rawRows.add(cols.toArray(new String[0]));
                }
            }
            out.addAll(inferRunsFromLegacy(rawRows));
        } else {
            for (int i = 1; i < records.size(); i++) {
                String rec = records.get(i);
                if (rec.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(rec);
                if (cols.size() < 9) continue;
                try {
                    ProspectorLogRow r = parseModernCsvRow(cols);
                    if (r != null) {
                        out.add(r);
                    }
                } catch (Exception ignored) {
                    // skip malformed line
                }
            }
        }
        return out;
    }

    /**
     * Split a CSV file string into records (rows). Newlines inside quoted fields are preserved as part of the
     * record, matching the {@code MiningTabPanel.csvEscape} contract.
     */
    private static List<String> splitCsvRecords(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < len && text.charAt(i + 1) == '"') {
                    cur.append('"').append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                    cur.append('"');
                }
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** Parse a non-legacy CSV row supporting 12-, 14-, 15-, and 16-column widths. */
    private static ProspectorLogRow parseModernCsvRow(List<String> cols) {
        int run = parseInt(cols.get(Col.RUN).trim(), 0);
        if (run < 1) {
            return null;
        }
        if (cols.size() >= 12) {
            String asteroidId = cols.get(Col.ASTEROID).trim();
            Instant ts = parseTimestamp(cols.get(Col.TIMESTAMP).trim());
            String material = cols.get(Col.MATERIAL).trim();
            double percent = parseDouble(cols.get(Col.PERCENT), 0.0);
            double before = parseDouble(cols.get(Col.BEFORE), 0.0);
            double after = parseDouble(cols.get(Col.AFTER), 0.0);
            double diff = parseDouble(cols.get(Col.ACTUAL), 0.0);
            String core = cols.get(Col.CORE).trim();
            if ("-".equals(core)) core = "";
            String fullBodyName = cols.get(Col.BODY).trim();
            int duds = parseInt(cols.get(Col.DUDS), 0);
            String commander = cols.get(Col.COMMANDER).trim();

            String shipType = "";
            String comments = "";
            int startIdx;
            int endIdx;
            if (cols.size() >= Col.WIDTH) {
                shipType = cols.get(Col.SHIP).trim();
                if ("-".equals(shipType)) shipType = "";
                comments = cols.get(Col.COMMENTS);
                if (comments == null) comments = "";
                else comments = comments.trim();
                startIdx = Col.START;
                endIdx = Col.END;
            } else if (cols.size() >= 15) {
                // legacy 15-col with ship but no comments: ship at 12, start at 13, end at 14
                shipType = cols.get(12).trim();
                if ("-".equals(shipType)) shipType = "";
                startIdx = 13;
                endIdx = 14;
            } else if (cols.size() >= 14) {
                // legacy 14-col without ship: start at 12, end at 13
                startIdx = 12;
                endIdx = 13;
            } else {
                startIdx = -1;
                endIdx = -1;
            }
            Instant runStart = (startIdx >= 0 && cols.size() > startIdx
                    && cols.get(startIdx) != null && !cols.get(startIdx).trim().isEmpty())
                    ? parseTimestamp(cols.get(startIdx).trim()) : null;
            Instant runEnd = (endIdx >= 0 && cols.size() > endIdx
                    && cols.get(endIdx) != null && !cols.get(endIdx).trim().isEmpty())
                    ? parseTimestamp(cols.get(endIdx).trim()) : null;
            return new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff,
                    commander, shipType, core, duds, runStart, runEnd, comments);
        }
        // 9-col legacy (no asteroid/core/duds/ship/comments)
        Instant ts = parseTimestamp(cols.get(1).trim());
        String material = cols.get(2).trim();
        double percent = parseDouble(cols.get(3), 0.0);
        double before = parseDouble(cols.get(4), 0.0);
        double after = parseDouble(cols.get(5), 0.0);
        double diff = parseDouble(cols.get(6), 0.0);
        String fullBodyName = cols.get(7).trim();
        String commander = cols.get(8).trim();
        return new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander);
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

        LegacyRow(Instant ts, String material, double percent, double before, double after, double diff,
                String commander) {
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
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {
        }
        DateTimeFormatter[] formats = {
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

    @Override
    public String displayName() {
        return "Local CSV";
    }

    @Override
    public boolean prefersDebouncedRefresh() {
        return false;
    }

    /** Identity tuple for upserts (must match {@code GoogleSheetsBackend.upsertCoreKeyMatches}). */
    private static final class UpsertKey {
        final int run;
        final String asteroid;
        final String material;
        final String commander;

        UpsertKey(int run, String asteroid, String material, String commander) {
            this.run = run;
            this.asteroid = norm(asteroid);
            this.material = norm(material);
            this.commander = norm(commander);
        }

        static UpsertKey of(ProspectorLogRow r) {
            return new UpsertKey(r.getRun(), r.getAsteroidId(), r.getMaterial(), r.getCommanderName());
        }

        private static String norm(String s) {
            String t = s == null ? "" : s.trim();
            return t.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof UpsertKey k)) return false;
            return run == k.run && asteroid.equals(k.asteroid) && material.equals(k.material)
                    && commander.equals(k.commander);
        }

        @Override
        public int hashCode() {
            int h = Integer.hashCode(run);
            h = 31 * h + asteroid.hashCode();
            h = 31 * h + material.hashCode();
            h = 31 * h + commander.hashCode();
            return h;
        }
    }
}
