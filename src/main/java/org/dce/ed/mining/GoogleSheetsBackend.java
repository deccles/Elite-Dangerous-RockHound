package org.dce.ed.mining;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.OverlayPreferences;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

/**
 * Prospector log backend that writes to and reads from a Google Sheet.
 * Column order: Run, Timestamp, Type, Percentage, Before Amount, After Amount, Actual, Body, Commander (A:I).
 * Uses OAuth 2.0; requires client ID/secret and refresh token (see GoogleSheetsAuth and setup instructions).
 */
public final class GoogleSheetsBackend implements ProspectorLogBackend {

    private static final Pattern SPREADSHEET_ID_PATTERN = Pattern.compile("/d/([a-zA-Z0-9_-]+)");
    private static final String VALUE_INPUT_OPTION_USER_ENTERED = "USER_ENTERED";
    private static final DateTimeFormatter[] TIMESTAMP_PARSERS = {
        // Common US-style 24h date-times with and without seconds
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),

        // Day-first variants (some locales / manual edits)
        DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.US),

        // 12h clock with AM/PM (if sheet is formatted that way)
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US),
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yyyy h:mm:ss a", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yyyy h:mm a", Locale.US),
    };

    private final String spreadsheetId;
    private final String url;

    public GoogleSheetsBackend(String spreadsheetUrl) {
        this.url = spreadsheetUrl != null ? spreadsheetUrl.trim() : "";
        this.spreadsheetId = parseSpreadsheetId(this.url);
    }

    /**
     * Extract spreadsheet ID from the edit URL, e.g.
     * https://docs.google.com/spreadsheets/d/18bYWZFYQWKZREZIMOh6A/edit?gid=43311951
     */
    static String parseSpreadsheetId(String spreadsheetUrl) {
        if (spreadsheetUrl == null || spreadsheetUrl.isBlank()) {
            return "";
        }
        Matcher m = SPREADSHEET_ID_PATTERN.matcher(spreadsheetUrl);
        return m.find() ? m.group(1) : "";
    }

    private static Sheets createSheetsService() throws IOException, GeneralSecurityException {
        var credential = GoogleSheetsAuth.getCredential();
        if (credential == null) {
            return null;
        }
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();
        return new Sheets.Builder(transport, jsonFactory, credential)
            .setApplicationName("EDO-Overlay-Mining")
            .build();
    }

    /** Sheet range: Run, Asteroid, Timestamp, Type, %, Before, After, Actual, Core, Duds, System, Body, Commander, Start time, End time (A–O). */
    private static String rangeA1O() {
        return "A:O";
    }

    /**
     * Renumber prospector runs in the configured Google Sheet so that:
     * - Run numbers are globally unique (1, 2, 3, ...) across all commanders.
     * - Blocks of rows that previously shared the same (run, commander) stay together.
     * - Those blocks are sorted from earliest to latest by their first timestamp.
     *
     * This reads all rows, computes new run numbers, optionally reorders the blocks by time,
     * and writes the full A:L range back to the sheet.
     */
    public static void renumberRunsAndSortUsingPreferences(Component parent) {
        String url = OverlayPreferences.getMiningGoogleSheetsUrl();
        if (url == null || url.isBlank()) {
            JOptionPane.showMessageDialog(parent,
                "Set a Google Sheets URL in the Mining preferences first.",
                "No Google Sheet configured",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        GoogleSheetsBackend backend = new GoogleSheetsBackend(url);
        backend.renumberRunsAndSort(parent);
    }

    @Override
    public void appendRows(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty() || spreadsheetId.isEmpty()) {
            return;
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return;
            }
            List<List<Object>> values = new ArrayList<>();
            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);
            for (ProspectorLogRow r : rows) {
                String ts = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(fmt) : "-";
                String fullBody = (r.getFullBodyName() != null) ? r.getFullBodyName() : "";
                String[] sysBody = splitSystemAndBody(fullBody);
                String system = sysBody[0];
                String body = sysBody[1];
                String commander = (r.getCommanderName() != null && !r.getCommanderName().isEmpty()) ? r.getCommanderName() : "-";
                String material = (r.getMaterial() != null && !r.getMaterial().isEmpty()) ? r.getMaterial() : "-";
                String asteroid = (r.getAsteroidId() != null && !r.getAsteroidId().isEmpty()) ? r.getAsteroidId() : "-";
                String core = (r.getCoreType() != null && !r.getCoreType().isEmpty()) ? r.getCoreType() : "-";
                List<Object> row = new ArrayList<>();
                row.add(r.getRun());          // 0 Run
                row.add(asteroid);            // 1 Asteroid
                row.add(ts);                  // 2 Timestamp
                row.add(material);            // 3 Type
                row.add(r.getPercent());      // 4 %
                row.add(r.getBeforeAmount()); // 5 Before
                row.add(r.getAfterAmount());  // 6 After
                row.add(r.getDifference());   // 7 Actual/Tons
                row.add(core);                // 8 Core
                row.add(r.getDuds());         // 9 Duds
                row.add(system);              // 10 System
                row.add(body);                // 11 Body
                row.add(commander);           // 12 Commander
                row.add(r.getRunStartTime() != null ? r.getRunStartTime().atZone(zone).format(fmt) : "");  // 13 Start time
                row.add(r.getRunEndTime() != null ? r.getRunEndTime().atZone(zone).format(fmt) : "");      // 14 End time
                values.add(row);
            }
            ValueRange bodyRange = new ValueRange().setValues(values);
            AppendValuesResponse res = sheets.spreadsheets().values()
                .append(spreadsheetId, rangeA1O(), bodyRange)
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .setInsertDataOption("INSERT_ROWS")
                .execute();
        } catch (Exception e) {
            // don't break UI; caller may log
        }
    }

    /**
     * Insert or update prospector rows keyed by (run, asteroid, material, commander, system, body).
     * If a matching row already exists, it is updated in-place; otherwise a new row is appended.
     */
    public void upsertRows(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty() || spreadsheetId.isEmpty()) {
            return;
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return;
            }
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1O())
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                // No existing header; fall back to simple append.
                appendRows(rows);
                return;
            }

            // Ensure header for new layout; if not, we fall back to append-only semantics.
            List<Object> header = values.get(0);
            if (header == null || header.size() < 13) {
                appendRows(rows);
                return;
            }

            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);

            for (ProspectorLogRow r : rows) {
                if (r == null) {
                    continue;
                }
                String ts = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(fmt) : "-";
                String fullBody = (r.getFullBodyName() != null && !r.getFullBodyName().isEmpty()) ? r.getFullBodyName() : "-";
                String[] sysBody = splitSystemAndBody(fullBody);
                String system = sysBody[0].isEmpty() ? "-" : sysBody[0];
                String body = sysBody[1].isEmpty() ? "-" : sysBody[1];
                String commander = (r.getCommanderName() != null && !r.getCommanderName().isEmpty()) ? r.getCommanderName() : "-";
                String material = (r.getMaterial() != null && !r.getMaterial().isEmpty()) ? r.getMaterial() : "-";
                String asteroid = (r.getAsteroidId() != null && !r.getAsteroidId().isEmpty()) ? r.getAsteroidId() : "-";
                String core = (r.getCoreType() != null && !r.getCoreType().isEmpty()) ? r.getCoreType() : "-";

                boolean updated = false;
                // Search for an existing row with the same logical key.
                for (int i = 1; i < values.size(); i++) {
                    List<Object> row = values.get(i);
                    if (row == null || row.size() < 13) {
                        continue;
                    }
                    int existingRun = parseInt(row.get(0), 0);
                    String existingAsteroid = str(row.get(1));
                    String existingMaterial = str(row.get(3));
                    String existingSystem = str(row.get(10));
                    String existingBody = str(row.get(11));
                    String existingCommander = str(row.get(12));

                    if (existingRun == r.getRun()
                        && existingAsteroid.equals(asteroid)
                        && existingMaterial.equals(material)
                        && existingSystem.equals(system)
                        && existingBody.equals(body)
                        && existingCommander.equals(commander)) {

                        // Update this row in-place. Preserve existing run start/end when the
                        // incoming row has null (e.g. a later cargo update) so we don't wipe them.
                        row.set(0, r.getRun());
                        row.set(1, asteroid);
                        row.set(2, ts);
                        row.set(3, material);
                        row.set(4, r.getPercent());
                        row.set(5, r.getBeforeAmount());
                        row.set(6, r.getAfterAmount());
                        row.set(7, r.getDifference());
                        row.set(8, core);
                        row.set(9, r.getDuds());
                        row.set(10, system);
                        row.set(11, body);
                        row.set(12, commander);
                        ensureRowSize(row, 15);
                        // Never overwrite an existing start time: cargo upserts on a "continued" run still send
                        // a fresh lastUndockTime and would corrupt the canonical run start.
                        if (r.getRunStartTime() != null) {
                            String existingStart = row.size() > 13 ? str(row.get(13)) : "";
                            if (existingStart.isBlank()) {
                                row.set(13, r.getRunStartTime().atZone(zone).format(fmt));
                            }
                        }
                        if (r.getRunEndTime() != null) {
                            row.set(14, r.getRunEndTime().atZone(zone).format(fmt));
                        }
                        updated = true;
                        break;
                    }
                }

                if (!updated) {
                    // Append as a new row.
                    List<Object> newRow = new ArrayList<>();
                    newRow.add(r.getRun());          // 0 Run
                    newRow.add(asteroid);            // 1 Asteroid
                    newRow.add(ts);                  // 2 Timestamp
                    newRow.add(material);            // 3 Type
                    newRow.add(r.getPercent());      // 4 %
                    newRow.add(r.getBeforeAmount()); // 5 Before
                    newRow.add(r.getAfterAmount());  // 6 After
                    newRow.add(r.getDifference());   // 7 Actual/Tons
                    newRow.add(core);                // 8 Core
                    newRow.add(r.getDuds());         // 9 Duds
                    newRow.add(system);              // 10 System
                    newRow.add(body);                // 11 Body
                    newRow.add(commander);           // 12 Commander
                    newRow.add(r.getRunStartTime() != null ? r.getRunStartTime().atZone(zone).format(fmt) : "");
                    newRow.add(r.getRunEndTime() != null ? r.getRunEndTime().atZone(zone).format(fmt) : "");
                    values.add(newRow);
                }
            }

            ValueRange bodyRange = new ValueRange().setValues(values);
            sheets.spreadsheets().values()
                .update(spreadsheetId, rangeA1O(), bodyRange)
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .execute();
        } catch (Exception e) {
            // don't break UI; caller may log
        }
    }

    @Override
    public List<ProspectorLogRow> loadRows() {
        // Backwards-compatible: ignore status, just return whatever rows we have.
        ProspectorLoadResult result = loadRowsWithStatus();
        return result != null ? result.getRows() : Collections.emptyList();
    }

    /**
     * Load all rows with explicit status so callers can distinguish
     * between an empty sheet and a read/error condition.
     */
    public ProspectorLoadResult loadRowsWithStatus() {
        if (spreadsheetId.isEmpty()) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList());
            }
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1O())
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.size() <= 1) {
                // No rows or only a header row.
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<ProspectorLogRow> out = new ArrayList<>();
            // Skip header.
            // New layout: 15 cols: run,asteroid,timestamp,material,percent,before,after,actual,core,duds,system,body,commander,start time,end time.
            // Legacy 13/12/9 column layouts are still supported for older sheets.
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null || row.size() < 9) continue;
                try {
                    int run = parseInt(row.get(0), 0);
                    if (row.size() >= 13) {
                        // New layout A–M or A–O
                        String asteroidId = str(row.get(1));
                        Instant ts = parseTimestamp(str(row.get(2)));
                        String material = str(row.get(3));
                        double percent = parseDouble(row.get(4), 0.0);
                        double before = parseDouble(row.get(5), 0.0);
                        double after = parseDouble(row.get(6), 0.0);
                        double diff = parseDouble(row.get(7), 0.0);
                        String core = str(row.get(8));
                        int duds = parseInt(row.get(9), 0);
                        String system = str(row.get(10));
                        String body = str(row.get(11));
                        String commander = str(row.get(12));
                        // Sheets trims trailing empty cells per row. A row with a start time but no end time
                        // will have size 14 (indexes 0..13). Guard the start and end columns independently.
                        String rawStart = (row.size() >= 14 && row.get(13) != null) ? row.get(13).toString() : "";
                        String rawEnd = (row.size() >= 15 && row.get(14) != null) ? row.get(14).toString() : "";
                        Instant runStart = (!rawStart.isBlank()) ? parseTimestamp(rawStart) : null;
                        Instant runEnd = (!rawEnd.isBlank()) ? parseTimestamp(rawEnd) : null;
                        String fullBodyName = buildFullBodyName(system, body);
                        out.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff, commander, core, duds, runStart, runEnd));
                    } else if (row.size() >= 12) {
                        // Legacy 12-col layout (no separate System column, Body only)
                        String asteroidId = str(row.get(1));
                        Instant ts = parseTimestamp(str(row.get(2)));
                        String material = str(row.get(3));
                        double percent = parseDouble(row.get(4), 0.0);
                        double before = parseDouble(row.get(5), 0.0);
                        double after = parseDouble(row.get(6), 0.0);
                        double diff = parseDouble(row.get(7), 0.0);
                        String core = str(row.get(8));
                        String body = str(row.get(9));
                        int duds = parseInt(row.get(10), 0);
                        String commander = str(row.get(11));
                        String fullBodyName = buildFullBodyName("", body);
                        out.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff, commander, core, duds));
                    } else {
                        Instant ts = parseTimestamp(str(row.get(1)));
                        String material = str(row.get(2));
                        double percent = parseDouble(row.get(3), 0.0);
                        double before = parseDouble(row.get(4), 0.0);
                        double after = parseDouble(row.get(5), 0.0);
                        double diff = parseDouble(row.get(6), 0.0);
                        String fullBodyName = str(row.get(7));
                        String commander = str(row.get(8));
                        out.add(new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander));
                    }
                } catch (Exception ignored) {
                }
            }
            out.sort(java.util.Comparator.comparing(ProspectorLogRow::getTimestamp, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, out);
        } catch (Exception e) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList());
        }
    }

    /**
     * Background task: renumber and sort prospector runs, then report result to the user.
     */
    public void renumberRunsAndSort(Component parent) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private Exception failure;
            private int groupsUpdated;
            private int rowsUpdated;

            @Override
            protected Void doInBackground() {
                try {
                    int[] result = renumberRunsAndSortInternal();
                    groupsUpdated = result[0];
                    rowsUpdated = result[1];
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    JOptionPane.showMessageDialog(parent,
                        "Unable to fix mining runs:\n" + failure.getMessage(),
                        "Mining Sheet Update Failed",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (rowsUpdated == 0) {
                    JOptionPane.showMessageDialog(parent,
                        "No mining rows were found to update.",
                        "Mining Sheet",
                        JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                JOptionPane.showMessageDialog(parent,
                    String.format(Locale.US,
                        "Updated %d run group(s), %d row(s).\nRuns are now unique and sorted from earliest to latest.",
                        groupsUpdated, rowsUpdated),
                    "Mining Sheet Updated",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        };
        worker.execute();
    }

    /**
     * Core renumber/sort logic. Returns [groupCount, rowCount].
     */
    private int[] renumberRunsAndSortInternal() throws IOException, GeneralSecurityException {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            return new int[] {0, 0};
        }
        Sheets sheets = createSheetsService();
        if (sheets == null) {
            return new int[] {0, 0};
        }

        ValueRange response = sheets.spreadsheets().values()
            .get(spreadsheetId, rangeA1O())
            .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() <= 1) {
            return new int[] {0, 0};
        }

        List<Object> header = values.get(0);
        List<DataRow> dataRows = new ArrayList<>();

        // Collect all data rows with their parsed timestamp/run/commander.
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.isEmpty()) {
                continue;
            }
            int run = parseInt(row.size() > 0 ? row.get(0) : null, 0);
            String commander = row.size() > 11 ? str(row.get(11)) : "";
            String tsStr = row.size() > 2 ? str(row.get(2)) : "";
            Instant ts = parseTimestamp(tsStr);
            dataRows.add(new DataRow(i, run, commander, ts, row));
        }

        if (dataRows.isEmpty()) {
            return new int[] {0, 0};
        }

        // Group by (oldRun, commander).
        Map<GroupKey, List<DataRow>> groups = new HashMap<>();
        for (DataRow r : dataRows) {
            GroupKey key = new GroupKey(r.oldRun, r.commander);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // Compute earliest timestamp per group.
        List<Group> groupList = new ArrayList<>();
        for (Map.Entry<GroupKey, List<DataRow>> e : groups.entrySet()) {
            Instant earliest = null;
            for (DataRow r : e.getValue()) {
                if (r.timestamp == null) continue;
                if (earliest == null || r.timestamp.isBefore(earliest)) {
                    earliest = r.timestamp;
                }
            }
            groupList.add(new Group(e.getKey(), e.getValue(), earliest));
        }

        // Sort groups by earliest timestamp ascending, then by oldRun, then commander.
        groupList.sort(Comparator
            .comparing((Group g) -> g.earliest, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(g -> g.key.oldRun)
            .thenComparing(g -> g.key.commander, String.CASE_INSENSITIVE_ORDER));

        // Within each group, sort rows by timestamp ascending to keep blocks tidy.
        for (Group g : groupList) {
            g.rows.sort(Comparator
                .comparing((DataRow r) -> r.timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(r -> r.originalIndex));
        }

        // Assign new globally unique run numbers per group.
        int nextRun = 1;
        List<List<Object>> newDataRows = new ArrayList<>();
        for (Group g : groupList) {
            int newRun = nextRun++;
            for (DataRow r : g.rows) {
                // Ensure row has at least one column for Run.
                while (r.cells.size() < 1) {
                    r.cells.add("");
                }
                r.cells.set(0, newRun);
                newDataRows.add(r.cells);
            }
        }

        // Rebuild values: header + sorted/renumbered data rows.
        List<List<Object>> updated = new ArrayList<>();
        updated.add(header);
        updated.addAll(newDataRows);

        ValueRange body = new ValueRange().setValues(updated);
        sheets.spreadsheets().values()
            .update(spreadsheetId, rangeA1O(), body)
            .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
            .execute();

        return new int[] {groupList.size(), newDataRows.size()};
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static int parseInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void ensureRowSize(List<Object> row, int size) {
        while (row.size() < size) {
            row.add("");
        }
    }

    @Override
    public void updateRunEndTime(String commander, int run, Instant endTime) {
        if (spreadsheetId.isEmpty() || endTime == null) {
            return;
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) return;
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1O())
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.size() < 2) return;
            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);
            String endStr = endTime.atZone(zone).format(fmt);
            String cmdr = commander != null ? commander : "";
            // Every row for this run+commander must get an end time. Otherwise loadRows still sees
            // (start set, end empty) on sibling materials / later asteroids and computeRunNumberForWrite
            // treats the run as still open forever (matches LocalCsvBackend behavior).
            boolean any = false;
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null || row.size() < 13) {
                    continue;
                }
                int rowRun = parseInt(row.get(0), 0);
                String rowCommander = str(row.get(12));
                if (rowRun == run && java.util.Objects.equals(rowCommander, cmdr)) {
                    ensureRowSize(row, 15);
                    row.set(14, endStr);
                    any = true;
                }
            }
            if (any) {
                ValueRange bodyRange = new ValueRange().setValues(values);
                sheets.spreadsheets().values()
                        .update(spreadsheetId, rangeA1O(), bodyRange)
                        .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                        .execute();
            }
        } catch (Exception e) {
            // don't break UI; caller may log
        }
    }

    private static double parseDouble(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Instant parseTimestamp(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        try {
            return java.time.Instant.parse(s);
        } catch (Exception ignored) {
        }
        for (DateTimeFormatter fmt : TIMESTAMP_PARSERS) {
            try {
                return java.time.LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    // Split a combined \"system > body\" string into [system, body], with some cleanup.
    private static String[] splitSystemAndBody(String fullBodyName) {
        String system = "";
        String body = "";
        if (fullBodyName == null) {
            return new String[] {"", ""};
        }
        String s = fullBodyName.trim();
        if (s.isEmpty()) {
            return new String[] {"", ""};
        }
        int idx = s.indexOf(" > ");
        if (idx >= 0) {
            system = s.substring(0, idx).trim();
            body = s.substring(idx + 3).trim();
        } else {
            body = s;
        }
        // If the body still starts with the system name, strip it.
        if (!system.isEmpty() && body.startsWith(system)) {
            body = body.substring(system.length()).trim();
        }
        // Drop trailing \"Ring\" suffix to get just the orbital identifier (e.g. \"6 B\").
        if (body.endsWith(" Ring")) {
            body = body.substring(0, body.length() - " Ring".length()).trim();
        }
        return new String[] {system, body};
    }

    private static String buildFullBodyName(String system, String body) {
        String sys = system != null ? system.trim() : "";
        String b = body != null ? body.trim() : "";
        if (sys.isEmpty() && b.isEmpty()) {
            return "";
        }
        if (sys.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return sys;
        }
        return sys + " > " + b;
    }

    // Helper types for renumbering/sorting
    private static final class DataRow {
        final int originalIndex;
        final int oldRun;
        final String commander;
        final Instant timestamp;
        final List<Object> cells;

        DataRow(int originalIndex, int oldRun, String commander, Instant timestamp, List<Object> cells) {
            this.originalIndex = originalIndex;
            this.oldRun = oldRun;
            this.commander = commander != null ? commander : "";
            this.timestamp = timestamp;
            this.cells = new ArrayList<>(cells);
        }
    }

    private static final class GroupKey {
        final int oldRun;
        final String commander;

        GroupKey(int oldRun, String commander) {
            this.oldRun = oldRun;
            this.commander = commander != null ? commander : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupKey)) return false;
            GroupKey other = (GroupKey) o;
            return oldRun == other.oldRun && java.util.Objects.equals(commander, other.commander);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(oldRun, commander);
        }
    }

    private static final class Group {
        final GroupKey key;
        final List<DataRow> rows;
        final Instant earliest;

        Group(GroupKey key, List<DataRow> rows, Instant earliest) {
            this.key = key;
            this.rows = rows;
            this.earliest = earliest;
        }
    }
}
