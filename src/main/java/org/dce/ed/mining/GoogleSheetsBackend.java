package org.dce.ed.mining;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Objects;

import org.dce.ed.OverlayFrame;
import org.dce.ed.OverlayPreferences;

import java.awt.Component;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

/**
 * Prospector log backend that writes to and reads from a Google Sheet.
 * <p>
 * Layout is the 16-column model (Run … Ship, Start time, End time). Invariants for <strong>run start on upsert</strong> and
 * <strong>which row receives run end on dock</strong> are centralized in {@link ProspectorMiningLogPolicy} with
 * unit tests — keep those rules out of ad-hoc conditionals here.
 * <p>
 * Google Sheets often omits <strong>trailing empty cells</strong> from API reads; rows are padded to 16 cells.
 * Worksheets whose header row skipped the Ship column (Commander / Start / End at M–O) are upgraded in place to
 * the canonical header and row shape via {@link #upgradeProspectorSheetIfCommanderStartEndLayoutWithoutShip}.
 * </p>
 * <p>
 * Run <em>number</em> selection for new rows is {@link MiningRunNumberResolver} (also tested).
 * </p>
 * Uses OAuth 2.0; requires client ID/secret and refresh token (see GoogleSheetsAuth and setup instructions).
 */
public final class GoogleSheetsBackend implements ProspectorLogBackend {

    private static final Pattern SPREADSHEET_ID_PATTERN = Pattern.compile("/d/([a-zA-Z0-9_-]+)");
    /**
     * Elite ring belt suffix: inner {@code A Ring}, outer {@code B Ring} (single-ring bodies often use A only).
     * Stripped from the body token after split so mining tables show the parent body index (e.g. {@code 6 B Ring} → {@code 6}).
     */
    private static final Pattern ELITE_AB_RING_SUFFIX = Pattern.compile("\\s+[AB]\\s+Ring$", Pattern.CASE_INSENSITIVE);
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

    /** Preferences value: per-commander worksheet layout (after legacy migration). */
    public static final int MINING_LAYOUT_VERSION_PER_COMMANDER_TABS = 1;

    private static final int LAYOUT_VERSION_PER_COMMANDER = MINING_LAYOUT_VERSION_PER_COMMANDER_TABS;

    /**
     * Comparator for merged multi-tab prospector rows (ascending timestamp; null timestamps last).
     */
    public static final Comparator<ProspectorLogRow> PROSPECTOR_MERGED_ROWS_BY_TIMESTAMP =
            Comparator.comparing(ProspectorLogRow::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder()));

    private final String spreadsheetId;
    private final String url;
    private static final String MISSING_SHEET_URL_MESSAGE =
            "Google Sheets URL is not configured. Open Mining preferences and set the sheet URL, then Connect to Google.";

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

    /** True when {@code values} is null, empty, or only a header row (no data rows). */
    public static boolean legacySheetIsEmptyOrHeaderOnly(List<List<Object>> values) {
        return values == null || values.size() <= 1;
    }

    /**
     * True when the only row after the header is the standard migration note in column A (idempotent migrate).
     */
    public static boolean legacySheetIsMigrationNoteOnly(List<List<Object>> values) {
        if (values == null || values.size() != 2) {
            return false;
        }
        List<Object> r = values.get(1);
        if (r == null || r.isEmpty()) {
            return false;
        }
        Object a0 = r.get(0);
        String s = a0 == null ? "" : a0.toString();
        return s.contains("Migrated to per-commander");
    }

    /**
     * True when {@link #migrateLegacySheetToCommanderTabs()} would copy rows into commander tabs
     * (real data present, not already reduced to the migration note only).
     */
    public static boolean legacySheetNeedsCommanderTabSplit(List<List<Object>> values) {
        if (legacySheetIsEmptyOrHeaderOnly(values)) {
            return false;
        }
        if (legacySheetIsMigrationNoteOnly(values)) {
            return false;
        }
        return true;
    }

    /**
     * Whether first-launch migration may run for the given layout/backend/url. Does not read preferences or OAuth.
     */
    public static boolean shouldRunFirstLaunchMiningSheetMigration(int layoutVersion, String backend, String url) {
        if (layoutVersion >= MINING_LAYOUT_VERSION_PER_COMMANDER_TABS) {
            return false;
        }
        if (!"google".equals(backend)) {
            return false;
        }
        return url != null && !url.isBlank();
    }

    /**
     * Whether first-launch migration may run (layout version, backend, URL from preferences). Does not check OAuth.
     */
    public static boolean shouldRunFirstLaunchMiningSheetMigration() {
        return shouldRunFirstLaunchMiningSheetMigration(
                OverlayPreferences.getMiningGoogleSheetsLayoutVersion(),
                OverlayPreferences.getMiningLogBackend(),
                OverlayPreferences.getMiningGoogleSheetsUrl());
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

    /** Sheet range: Run … End time (A–P) for a named worksheet. */
    static String rangeA1PForSheetTitle(String sheetTitle) {
        return MiningSheetTitles.rangeA1P(sheetTitle);
    }

    /**
     * True when the sheet header row uses the current layout with a dedicated Ship column (index 13).
     */
    public static boolean headerDefinesProspectorShipColumn(List<Object> header) {
        if (header == null || header.size() < 14) {
            return false;
        }
        return "Ship".equalsIgnoreCase(str(header.get(13)));
    }

    /**
     * Some tabs were saved without a Ship column: headers show Commander, then Start time, then End time (and
     * sometimes an extra column). Data was still written with indices 12–15 = Commander, Ship, Start, End, so the
     * ship name appeared under the "Start time" label. When every data row is at most 15 cells wide, remap from
     * (cmdr, start, end) at 12–14 into the canonical 16-column shape; when rows are already 16 wide, replace only the
     * header row with the canonical titles.
     */
    static void upgradeProspectorSheetIfCommanderStartEndLayoutWithoutShip(List<List<Object>> values) {
        if (values == null || values.size() < 2) {
            return;
        }
        List<Object> header = values.get(0);
        if (header == null || headerDefinesProspectorShipColumn(header)) {
            return;
        }
        if (header.size() < 14) {
            return;
        }
        String h12 = str(header.get(12));
        String h13 = str(header.get(13));
        if (!h12.toLowerCase(Locale.ROOT).contains("commander") || !h13.toLowerCase(Locale.ROOT).contains("start")) {
            return;
        }
        boolean anyDataRowLen16 = false;
        for (int i = 1; i < values.size(); i++) {
            List<Object> r = values.get(i);
            if (r != null && r.size() >= 16) {
                anyDataRowLen16 = true;
                break;
            }
        }
        if (!anyDataRowLen16) {
            values.set(0, new ArrayList<>(headerRow16()));
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null) {
                    continue;
                }
                if (!(row instanceof ArrayList)) {
                    row = new ArrayList<>(row);
                    values.set(i, row);
                }
                List<Object> out = new ArrayList<>(16);
                for (int c = 0; c < 12; c++) {
                    out.add(c < row.size() ? row.get(c) : "");
                }
                out.add(row.size() > 12 ? row.get(12) : "");
                out.add("");
                out.add(row.size() > 13 ? row.get(13) : "");
                out.add(row.size() > 14 ? row.get(14) : "");
                values.set(i, out);
            }
        } else {
            values.set(0, new ArrayList<>(headerRow16()));
        }
    }

    /**
     * Pads a data row to 16 columns (Google may omit trailing empty cells) and clears obvious ship/start/end glitches.
     */
    static void normalizeProspectorDataRowForHeader(List<Object> header, List<Object> row) {
        if (row == null) {
            return;
        }
        ensureRowSize(row, 16);
        recoverMisplacedShipNameIntoColumn13(row);
        repairDuplicateShipInRunTimeColumns(row);
    }

    /**
     * When Ship (13) is empty but Start or End holds non-timestamp text (e.g. ship name written into the wrong column),
     * move that value into Ship and clear the slot. Prefers recovering from End (15) then Start (14).
     */
    static void recoverMisplacedShipNameIntoColumn13(List<Object> row) {
        if (row == null || row.size() < 16) {
            return;
        }
        if (!normProspectorShipCell(row.get(13)).isEmpty()) {
            return;
        }
        if (tryMoveNonTimestampFromSlotIntoShip(row, 15)) {
            return;
        }
        tryMoveNonTimestampFromSlotIntoShip(row, 14);
    }

    private static String normProspectorShipCell(Object o) {
        String s = str(o);
        return "-".equals(s) ? "" : s;
    }

    /**
     * @return true if a value was moved into column 13
     */
    private static boolean tryMoveNonTimestampFromSlotIntoShip(List<Object> row, int fromCol) {
        Object raw = row.get(fromCol);
        String t = str(raw);
        if (t.isEmpty() || "-".equals(t)) {
            return false;
        }
        if (parseTimestampCell(raw) != null) {
            return false;
        }
        if (fromCol == 14 && ProspectorMiningLogPolicy.looksLikeRunStartTimestamp(t)) {
            return false;
        }
        row.set(13, raw);
        row.set(fromCol, "");
        return true;
    }

    /**
     * Clears Start / End when they repeat the ship cell text but do not parse as timestamps (classic N+O+P ship
     * duplication after a bad row-width fix). Does not clear real times or unrelated text.
     */
    static void repairDuplicateShipInRunTimeColumns(List<Object> row) {
        if (row == null || row.size() < 16) {
            return;
        }
        String ship = str(row.get(13));
        if (ship.isEmpty() || "-".equals(ship)) {
            return;
        }
        Object rawStart = row.get(14);
        Object rawEnd = row.get(15);
        if (parseTimestampCell(rawStart) == null && ship.equalsIgnoreCase(str(rawStart))) {
            row.set(14, "");
        }
        if (parseTimestampCell(rawEnd) == null && ship.equalsIgnoreCase(str(rawEnd))) {
            row.set(15, "");
        }
    }

    /**
     * After reading A:P from Sheets: upgrade wrong headers missing Ship, then pad every data row to 16 columns and
     * repair duplicate ship text in start/end slots.
     */
    static void normalizeProspectorSheetValuesInPlace(List<List<Object>> values) {
        if (values == null || values.size() < 2) {
            return;
        }
        upgradeProspectorSheetIfCommanderStartEndLayoutWithoutShip(values);
        List<Object> header = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null) {
                continue;
            }
            // Arrays.asList / List.of cannot grow; ensureRowSize uses add.
            if (!(row instanceof ArrayList)) {
                row = new ArrayList<>(row);
                values.set(i, row);
            }
            normalizeProspectorDataRowForHeader(header, row);
        }
    }

    static int sheetRunStartColumnIndex(List<Object> row) {
        return row != null && row.size() >= 16 ? 14 : 13;
    }

    static int sheetRunEndColumnIndex(List<Object> row) {
        return row != null && row.size() >= 16 ? 15 : 14;
    }

    private static String truncateMsg(String m) {
        if (m == null) {
            return "Unknown error";
        }
        m = m.trim();
        return m.length() > 200 ? m.substring(0, 197) + "..." : m;
    }

    /**
     * Tabs we already verified exist (or just created). Avoids a spreadsheets.get on every append/upsert.
     * Key is spreadsheetId + NUL + title. Cleared when the sheet is missing on a values call (best-effort).
     */
    private static final Set<String> ENSURED_SHEET_WITH_HEADER_KEYS = ConcurrentHashMap.newKeySet();

    /** For tests that need a clean slate when reusing the same spreadsheet id. */
    static void clearEnsuredSheetWithHeaderCacheForTests() {
        ENSURED_SHEET_WITH_HEADER_KEYS.clear();
    }

    static void forgetEnsuredSheetWithHeader(String spreadsheetId, String title) {
        if (spreadsheetId == null || title == null) {
            return;
        }
        ENSURED_SHEET_WITH_HEADER_KEYS.remove(spreadsheetId + "\0" + title);
    }

    private static boolean sheetErrorSuggestsMissingWorksheet(String m) {
        if (m == null) {
            return false;
        }
        return m.contains("Unable to parse range")
                || m.contains("not found")
                || m.contains("Invalid value at");
    }

    private void ensureSheetWithHeader(Sheets sheets, String title) throws IOException, GeneralSecurityException {
        String cacheKey = spreadsheetId + "\0" + title;
        if (ENSURED_SHEET_WITH_HEADER_KEYS.contains(cacheKey)) {
            return;
        }
        Spreadsheet spr = sheets.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> list = spr.getSheets();
        if (list != null) {
            for (Sheet sh : list) {
                SheetProperties p = sh.getProperties();
                if (p != null && Objects.equals(title, p.getTitle())) {
                    ENSURED_SHEET_WITH_HEADER_KEYS.add(cacheKey);
                    return;
                }
            }
        }
        SheetProperties props = new SheetProperties().setTitle(title);
        AddSheetRequest add = new AddSheetRequest().setProperties(props);
        Request req = new Request().setAddSheet(add);
        BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest()
            .setRequests(Collections.singletonList(req));
        sheets.spreadsheets().batchUpdate(spreadsheetId, batch).execute();
        ValueRange headerOnly = new ValueRange().setValues(Collections.singletonList(headerRow16()));
        sheets.spreadsheets().values()
            .update(spreadsheetId, rangeA1PForSheetTitle(title), headerOnly)
            .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
            .execute();
        ENSURED_SHEET_WITH_HEADER_KEYS.add(cacheKey);
    }

    private static List<Object> headerRow16() {
        List<Object> h = new ArrayList<>();
        h.add("Run");
        h.add("Asteroid");
        h.add("Timestamp");
        h.add("Type");
        h.add("%");
        h.add("Before");
        h.add("After");
        h.add("Actual");
        h.add("Core");
        h.add("Duds");
        h.add("System");
        h.add("Body");
        h.add("Commander");
        h.add("Ship");
        h.add("Start time");
        h.add("End time");
        return h;
    }

    /**
     * Renumber prospector runs in the configured Google Sheet so that:
     * - Run numbers are globally unique (1, 2, 3, ...) across all commanders.
     * - Blocks of rows that previously shared the same (run, commander) stay together.
     * - Those blocks are sorted from earliest to latest by their first timestamp.
     *
     * This reads all rows, computes new run numbers, optionally reorders the blocks by time,
     * and writes the full A:P range back to the sheet.
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

    /**
     * Reads every {@code CMDR …} mining worksheet (A:P), normalizes row widths, clears Start/End cells that duplicate
     * the ship name without a parseable timestamp, and writes the full grid back. Use once after upgrading from a
     * build that mis-handled trailing-empty API rows.
     */
    public ProspectorWriteResult repairAllCmdrMiningWorksheetLayoutsResult() {
        if (spreadsheetId.isEmpty()) {
            return ProspectorWriteResult.failure("No spreadsheet ID");
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return authFailure();
            }
            Spreadsheet spr = sheets.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheetList = spr.getSheets();
            if (sheetList == null || sheetList.isEmpty()) {
                return ProspectorWriteResult.ok();
            }
            List<String> cmdrTitles = new ArrayList<>();
            for (Sheet sh : sheetList) {
                SheetProperties p = sh.getProperties();
                if (p == null) {
                    continue;
                }
                String title = p.getTitle();
                if (title == null || title.isBlank()) {
                    continue;
                }
                if (!MiningSheetTitles.isCmdrMiningWorksheet(title)) {
                    continue;
                }
                cmdrTitles.add(title);
            }
            if (cmdrTitles.isEmpty()) {
                return ProspectorWriteResult.ok();
            }
            int tabsWritten = 0;
            for (String sheetTitle : cmdrTitles) {
                ValueRange response = sheets.spreadsheets().values()
                        .get(spreadsheetId, rangeA1PForSheetTitle(sheetTitle))
                        .execute();
                List<List<Object>> values = response.getValues();
                if (values == null || values.size() <= 1) {
                    continue;
                }
                normalizeProspectorSheetValuesInPlace(values);
                sheets.spreadsheets().values()
                        .update(spreadsheetId, rangeA1PForSheetTitle(sheetTitle), new ValueRange().setValues(values))
                        .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                        .execute();
                tabsWritten++;
            }
            if (tabsWritten == 0) {
                return ProspectorWriteResult.ok();
            }
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            return ProspectorWriteResult.failure(truncateMsg(e.getMessage()), e);
        }
    }

    private static ProspectorWriteResult authFailure() {
        return ProspectorWriteResult.failure("Google Sheets is not signed in. Configure OAuth in Mining preferences.");
    }

    private static ProspectorWriteResult missingSpreadsheetUrlFailure() {
        return ProspectorWriteResult.failure(MISSING_SHEET_URL_MESSAGE);
    }

    private static List<Object> rowToSheetValues(ProspectorLogRow r, ZoneId zone, DateTimeFormatter fmt) {
        String ts = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(fmt) : "-";
        String fullBody = (r.getFullBodyName() != null) ? r.getFullBodyName() : "";
        String[] sysBody = splitSystemAndBody(fullBody);
        String system = sysBody[0];
        String body = sysBody[1];
        String commander = (r.getCommanderName() != null && !r.getCommanderName().isEmpty()) ? r.getCommanderName() : "-";
        String ship = (r.getShipType() != null && !r.getShipType().isEmpty()) ? r.getShipType() : "-";
        String material = (r.getMaterial() != null && !r.getMaterial().isEmpty()) ? r.getMaterial() : "-";
        String asteroid = (r.getAsteroidId() != null && !r.getAsteroidId().isEmpty()) ? r.getAsteroidId() : "-";
        String core = (r.getCoreType() != null && !r.getCoreType().isEmpty()) ? r.getCoreType() : "-";
        List<Object> row = new ArrayList<>();
        row.add(r.getRun());
        row.add(asteroid);
        row.add(ts);
        row.add(material);
        row.add(r.getPercent());
        row.add(r.getBeforeAmount());
        row.add(r.getAfterAmount());
        row.add(r.getDifference());
        row.add(core);
        row.add(r.getDuds());
        row.add(system);
        row.add(body);
        row.add(commander);
        row.add(ship);
        row.add(r.getRunStartTime() != null ? r.getRunStartTime().atZone(zone).format(fmt) : "");
        row.add(r.getRunEndTime() != null ? r.getRunEndTime().atZone(zone).format(fmt) : "");
        return row;
    }

    /**
     * Append rows to the commander-specific worksheet (per-row commander must match for a single batch).
     */
    public ProspectorWriteResult appendRowsResult(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ProspectorWriteResult.ok();
        }
        if (spreadsheetId.isEmpty()) {
            return missingSpreadsheetUrlFailure();
        }
        ProspectorLogRow first = rows.get(0);
        String sheetTitle = MiningSheetTitles.sheetTitleForCommander(first != null ? first.getCommanderName() : "-");
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return authFailure();
            }
            ensureSheetWithHeader(sheets, sheetTitle);
            List<List<Object>> values = new ArrayList<>();
            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);
            for (ProspectorLogRow r : rows) {
                if (r != null) {
                    values.add(rowToSheetValues(r, zone, fmt));
                }
            }
            ValueRange bodyRange = new ValueRange().setValues(values);
            sheets.spreadsheets().values()
                .append(spreadsheetId, rangeA1PForSheetTitle(sheetTitle), bodyRange)
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .setInsertDataOption("INSERT_ROWS")
                .execute();
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            if (sheetErrorSuggestsMissingWorksheet(e.getMessage())) {
                forgetEnsuredSheetWithHeader(spreadsheetId, sheetTitle);
            }
            return ProspectorWriteResult.failure(truncateMsg(e.getMessage()), e);
        }
    }

    @Override
    public void appendRows(List<ProspectorLogRow> rows) {
        appendRowsResult(rows);
    }

    /**
     * Insert or update prospector rows keyed by (run, asteroid, material, commander). System/body columns
     * are not part of row identity; existing non-blank location cells are preserved on update.
     */
    public ProspectorWriteResult upsertRowsResult(List<ProspectorLogRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return ProspectorWriteResult.ok();
        }
        if (spreadsheetId.isEmpty()) {
            return missingSpreadsheetUrlFailure();
        }
        ProspectorLogRow head = rows.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (head == null) {
            return ProspectorWriteResult.ok();
        }
        String sheetTitle = MiningSheetTitles.sheetTitleForCommander(head.getCommanderName());
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return authFailure();
            }
            ensureSheetWithHeader(sheets, sheetTitle);
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1PForSheetTitle(sheetTitle))
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                return appendRowsResult(rows);
            }

            List<Object> header = values.get(0);
            if (header == null || header.size() < 13) {
                return appendRowsResult(rows);
            }
            normalizeProspectorSheetValuesInPlace(values);

            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);

            for (ProspectorLogRow r : rows) {
                if (r == null) {
                    continue;
                }
                String ts = r.getTimestamp() != null ? r.getTimestamp().atZone(zone).format(fmt) : "-";
                String fullBody = r.getFullBodyName() != null ? r.getFullBodyName() : "";
                String[] sysBody = splitSystemAndBody(fullBody);
                String incSystem = sysBody[0].trim();
                String incBody = sysBody[1].trim();
                String commander = (r.getCommanderName() != null && !r.getCommanderName().isEmpty()) ? r.getCommanderName() : "-";
                String shipCell = (r.getShipType() != null && !r.getShipType().isEmpty()) ? r.getShipType() : "-";
                String material = (r.getMaterial() != null && !r.getMaterial().isEmpty()) ? r.getMaterial() : "-";
                String asteroid = (r.getAsteroidId() != null && !r.getAsteroidId().isEmpty()) ? r.getAsteroidId() : "-";
                String core = (r.getCoreType() != null && !r.getCoreType().isEmpty()) ? r.getCoreType() : "-";

                boolean updated = false;
                // Match (run, asteroid, material, commander) with flexible system/body so blank journal
                // location does not append a duplicate row. When incoming location is blank, prefer updating
                // a row that already has system/body so orphan "-" rows are not chosen first.
                int matchIdx = findProspectorUpsertRowIndex(values, r.getRun(), asteroid, material, commander,
                        incSystem, incBody);
                if (matchIdx >= 0) {
                    List<Object> row = values.get(matchIdx);
                    ensureRowSize(row, 16);
                    String existingSystem = str(row.get(10));
                    String existingBody = str(row.get(11));

                    // Keep existing system/body when already set so journal location churn does not rewrite columns.
                    String outSystem = !isBlankSheetCell(existingSystem)
                            ? existingSystem
                            : (!isBlankSheetCell(incSystem) ? incSystem : "");
                    String outBody = !isBlankSheetCell(existingBody)
                            ? existingBody
                            : (!isBlankSheetCell(incBody) ? incBody : "");
                    if (isBlankSheetCell(outSystem)) {
                        outSystem = "";
                    }
                    if (isBlankSheetCell(outBody)) {
                        outBody = "";
                    }

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
                    row.set(10, outSystem);
                    row.set(11, outBody);
                    row.set(12, commander);
                    row.set(13, shipCell);
                    int startCol = sheetRunStartColumnIndex(row);
                    int endCol = sheetRunEndColumnIndex(row);
                    String existingStart = row.size() > startCol ? str(row.get(startCol)) : "";
                    if (ProspectorMiningLogPolicy.shouldWriteRunStartOnUpsertExistingRow(
                            asteroid,
                            existingStart,
                            r.getRunStartTime())) {
                        row.set(startCol, r.getRunStartTime().atZone(zone).format(fmt));
                    }
                    if (r.getRunEndTime() != null) {
                        row.set(endCol, r.getRunEndTime().atZone(zone).format(fmt));
                    }
                    updated = true;
                }

                if (!updated) {
                    String outSystem = isBlankSheetCell(incSystem) ? "" : incSystem;
                    String outBody = isBlankSheetCell(incBody) ? "" : incBody;
                    List<Object> newRow = new ArrayList<>();
                    newRow.add(r.getRun());
                    newRow.add(asteroid);
                    newRow.add(ts);
                    newRow.add(material);
                    newRow.add(r.getPercent());
                    newRow.add(r.getBeforeAmount());
                    newRow.add(r.getAfterAmount());
                    newRow.add(r.getDifference());
                    newRow.add(core);
                    newRow.add(r.getDuds());
                    newRow.add(outSystem);
                    newRow.add(outBody);
                    newRow.add(commander);
                    newRow.add(shipCell);
                    boolean asteroidA = "A".equalsIgnoreCase(asteroid != null ? asteroid.trim() : "");
                    newRow.add((asteroidA && r.getRunStartTime() != null)
                            ? r.getRunStartTime().atZone(zone).format(fmt)
                            : "");
                    newRow.add(r.getRunEndTime() != null ? r.getRunEndTime().atZone(zone).format(fmt) : "");
                    values.add(newRow);
                }
            }
            sortProspectorDataRowsInPlace(values);

            ValueRange bodyRange = new ValueRange().setValues(values);
            sheets.spreadsheets().values()
                .update(spreadsheetId, rangeA1PForSheetTitle(sheetTitle), bodyRange)
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .execute();
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            if (sheetErrorSuggestsMissingWorksheet(e.getMessage())) {
                forgetEnsuredSheetWithHeader(spreadsheetId, sheetTitle);
            }
            return ProspectorWriteResult.failure(truncateMsg(e.getMessage()), e);
        }
    }

    public void upsertRows(List<ProspectorLogRow> rows) {
        upsertRowsResult(rows);
    }

    @Override
    public List<ProspectorLogRow> loadRows() {
        // Backwards-compatible: ignore status, just return whatever rows we have.
        ProspectorLoadResult result = loadRowsWithStatus();
        return result != null ? result.getRows() : Collections.emptyList();
    }

    /**
     * Load all rows from all worksheets (merged), for spreadsheet table display.
     */
    public ProspectorLoadResult loadRowsWithStatus() {
        if (spreadsheetId.isEmpty()) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    MISSING_SHEET_URL_MESSAGE);
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                        "Google Sheets is not signed in.");
            }
            Spreadsheet spr = sheets.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheetList = spr.getSheets();
            if (sheetList == null || sheetList.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<String> cmdrTitles = new ArrayList<>();
            for (Sheet sh : sheetList) {
                SheetProperties p = sh.getProperties();
                if (p == null) {
                    continue;
                }
                String title = p.getTitle();
                if (title == null || title.isBlank()) {
                    continue;
                }
                if (!MiningSheetTitles.isCmdrMiningWorksheet(title)) {
                    continue;
                }
                cmdrTitles.add(title);
            }
            if (cmdrTitles.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<String> ranges = new ArrayList<>(cmdrTitles.size());
            for (String title : cmdrTitles) {
                ranges.add(rangeA1PForSheetTitle(title));
            }
            BatchGetValuesResponse batchResponse;
            try {
                batchResponse = sheets.spreadsheets().values()
                    .batchGet(spreadsheetId)
                    .setRanges(ranges)
                    .execute();
            } catch (Exception ex) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                        truncateMsg(ex.getMessage()));
            }
            List<ValueRange> valueRanges = batchResponse.getValueRanges();
            List<ProspectorLogRow> all = new ArrayList<>();
            if (valueRanges == null || valueRanges.size() != cmdrTitles.size()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                        "Unexpected Google Sheets batch read response.");
            }
            for (int i = 0; i < cmdrTitles.size(); i++) {
                String title = cmdrTitles.get(i);
                ValueRange response = valueRanges.get(i);
                List<List<Object>> values = response != null ? response.getValues() : null;
                if (values == null || values.size() <= 1) {
                    continue;
                }
                all.addAll(parseSheetDataRows(values, title));
            }
            if (all.isEmpty()) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            all.sort(PROSPECTOR_MERGED_ROWS_BY_TIMESTAMP);
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, all);
        } catch (Exception e) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    truncateMsg(e.getMessage()));
        }
    }

    /**
     * Load rows for one commander only (that commander's worksheet), for run-number resolution.
     */
    public ProspectorLoadResult loadRowsWithStatusForCommander(String commander) {
        if (spreadsheetId.isEmpty()) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    MISSING_SHEET_URL_MESSAGE);
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                        "Google Sheets is not signed in.");
            }
            String sheetTitle = MiningSheetTitles.sheetTitleForCommander(commander);
            ValueRange response;
            try {
                response = sheets.spreadsheets().values()
                    .get(spreadsheetId, rangeA1PForSheetTitle(sheetTitle))
                    .execute();
            } catch (Exception ex) {
                String m = ex.getMessage() != null ? ex.getMessage() : "";
                if (m.contains("Unable to parse range") || m.contains("not found")) {
                    forgetEnsuredSheetWithHeader(spreadsheetId, sheetTitle);
                    return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
                }
                return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                        truncateMsg(ex.getMessage()));
            }
            List<List<Object>> values = response.getValues();
            if (values == null || values.size() <= 1) {
                return new ProspectorLoadResult(ProspectorLoadResult.Status.EMPTY_SHEET, Collections.emptyList());
            }
            List<ProspectorLogRow> out = parseSheetDataRows(values, MiningSheetTitles.sheetTitleForCommander(commander));
            out.sort(Comparator.comparing(ProspectorLogRow::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));
            return new ProspectorLoadResult(ProspectorLoadResult.Status.OK, out);
        } catch (Exception e) {
            return new ProspectorLoadResult(ProspectorLoadResult.Status.ERROR, Collections.emptyList(),
                    truncateMsg(e.getMessage()));
        }
    }

    /**
     * @param worksheetTitle Google worksheet tab title; used when the Commander column is blank on per-commander tabs.
     */
    private List<ProspectorLogRow> parseSheetDataRows(List<List<Object>> values, String worksheetTitle) {
        if (values != null && values.size() > 1) {
            upgradeProspectorSheetIfCommanderStartEndLayoutWithoutShip(values);
        }
        List<ProspectorLogRow> out = new ArrayList<>();
        List<Object> header = values.size() > 0 ? values.get(0) : null;
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.size() < 9) {
                continue;
            }
            List<Object> cells = new ArrayList<>(row);
            normalizeProspectorDataRowForHeader(header, cells);
            try {
                int run = parseInt(cells.get(0), 0);
                if (run < 1) {
                    continue;
                }
                if (cells.size() >= 13) {
                    String asteroidId = str(cells.get(1));
                    Instant ts = parseTimestampCell(cells.get(2));
                    String material = str(cells.get(3));
                    double percent = parseDouble(cells.get(4), 0.0);
                    double before = parseDouble(cells.get(5), 0.0);
                    double after = parseDouble(cells.get(6), 0.0);
                    double diff = parseDouble(cells.get(7), 0.0);
                    String core = str(cells.get(8));
                    int duds = parseInt(cells.get(9), 0);
                    String system = str(cells.get(10));
                    String body = str(cells.get(11));
                    if (isBlankSheetCell(system)) {
                        system = "";
                    }
                    if (isBlankSheetCell(body)) {
                        body = "";
                    }
                    if (system.isEmpty() && !body.isEmpty()) {
                        String[] inferred = splitSystemAndBody(body);
                        if (!inferred[0].isEmpty()) {
                            system = inferred[0];
                            body = inferred[1];
                        }
                    }
                    String commander = commanderWithWorksheetDefault(str(cells.get(12)), worksheetTitle);
                    int startCol = sheetRunStartColumnIndex(cells);
                    int endCol = sheetRunEndColumnIndex(cells);
                    String shipType = "";
                    if (cells.size() >= 16) {
                        shipType = str(cells.get(13));
                        if (isBlankSheetCell(shipType) || "-".equals(shipType.trim())) {
                            shipType = "";
                        }
                    }
                    String rawStart = (cells.size() > startCol && cells.get(startCol) != null) ? cells.get(startCol).toString() : "";
                    String rawEnd = (cells.size() > endCol && cells.get(endCol) != null) ? cells.get(endCol).toString() : "";
                    Instant runStart = (!rawStart.isBlank()) ? parseTimestampCell(cells.get(startCol)) : null;
                    Instant runEnd = (!rawEnd.isBlank()) ? parseTimestampCell(cells.get(endCol)) : null;
                    String fullBodyName = buildFullBodyName(system, body);
                    out.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff, commander, shipType, core, duds, runStart, runEnd));
                } else if (cells.size() >= 12) {
                    String asteroidId = str(cells.get(1));
                    Instant ts = parseTimestampCell(cells.get(2));
                    String material = str(cells.get(3));
                    double percent = parseDouble(cells.get(4), 0.0);
                    double before = parseDouble(cells.get(5), 0.0);
                    double after = parseDouble(cells.get(6), 0.0);
                    double diff = parseDouble(cells.get(7), 0.0);
                    String core = str(cells.get(8));
                    String body = str(cells.get(9));
                    if (isBlankSheetCell(body)) {
                        body = "";
                    }
                    int duds = parseInt(cells.get(10), 0);
                    String commander = commanderWithWorksheetDefault(str(cells.get(11)), worksheetTitle);
                    String fullBodyName = buildFullBodyName("", body);
                    out.add(new ProspectorLogRow(run, asteroidId, fullBodyName, ts, material, percent, before, after, diff, commander, core, duds));
                } else {
                    Instant ts = parseTimestampCell(cells.get(1));
                    String material = str(cells.get(2));
                    double percent = parseDouble(cells.get(3), 0.0);
                    double before = parseDouble(cells.get(4), 0.0);
                    double after = parseDouble(cells.get(5), 0.0);
                    double diff = parseDouble(cells.get(6), 0.0);
                    String fullBodyName = str(cells.get(7));
                    String commander = commanderWithWorksheetDefault(str(cells.get(8)), worksheetTitle);
                    out.add(new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String commanderWithWorksheetDefault(String commander, String worksheetTitle) {
        if (commander != null && !commander.isBlank()) {
            return commander;
        }
        if (worksheetTitle == null || worksheetTitle.isBlank()) {
            return commander != null ? commander : "";
        }
        if (!MiningSheetTitles.isCmdrMiningWorksheet(worksheetTitle)) {
            return commander != null ? commander : "";
        }
        String fromTab = MiningSheetTitles.commanderNameFromCmdrWorksheetTitle(worksheetTitle);
        return !fromTab.isBlank() ? fromTab : "";
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
     * Core renumber/sort logic: each worksheet is renumbered 1..n within that sheet. Returns [groupCount, rowCount].
     */
    private int[] renumberRunsAndSortInternal() throws IOException, GeneralSecurityException {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            return new int[] {0, 0};
        }
        Sheets sheets = createSheetsService();
        if (sheets == null) {
            return new int[] {0, 0};
        }
        Spreadsheet spr = sheets.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheetList = spr.getSheets();
        if (sheetList == null || sheetList.isEmpty()) {
            return new int[] {0, 0};
        }
        int totalGroups = 0;
        int totalRows = 0;
        for (Sheet sh : sheetList) {
            SheetProperties p = sh.getProperties();
            if (p == null) {
                continue;
            }
            String title = p.getTitle();
            if (title == null || "Archive".equalsIgnoreCase(title.trim())) {
                continue;
            }
            int[] part = renumberOneSheet(sheets, title);
            totalGroups += part[0];
            totalRows += part[1];
        }
        return new int[] {totalGroups, totalRows};
    }

    private int[] renumberOneSheet(Sheets sheets, String sheetTitle) throws IOException {
        ValueRange response = sheets.spreadsheets().values()
            .get(spreadsheetId, rangeA1PForSheetTitle(sheetTitle))
            .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() <= 1) {
            return new int[] {0, 0};
        }
        normalizeProspectorSheetValuesInPlace(values);

        List<Object> header = values.get(0);
        List<DataRow> dataRows = new ArrayList<>();

        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.isEmpty()) {
                continue;
            }
            int run = parseInt(row.size() > 0 ? row.get(0) : null, 0);
            String commander;
            if (row.size() > 12) {
                commander = str(row.get(12));
            } else if (row.size() > 11) {
                commander = str(row.get(11));
            } else {
                commander = "";
            }
            String tsStr = row.size() > 2 ? str(row.get(2)) : "";
            Instant ts = parseTimestamp(tsStr);
            dataRows.add(new DataRow(i, run, commander, ts, row));
        }

        if (dataRows.isEmpty()) {
            return new int[] {0, 0};
        }

        Map<GroupKey, List<DataRow>> groups = new HashMap<>();
        for (DataRow r : dataRows) {
            GroupKey key = new GroupKey(r.oldRun, r.commander);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<Group> groupList = new ArrayList<>();
        for (Map.Entry<GroupKey, List<DataRow>> e : groups.entrySet()) {
            Instant earliest = null;
            for (DataRow r : e.getValue()) {
                if (r.timestamp == null) {
                    continue;
                }
                if (earliest == null || r.timestamp.isBefore(earliest)) {
                    earliest = r.timestamp;
                }
            }
            groupList.add(new Group(e.getKey(), e.getValue(), earliest));
        }

        groupList.sort(Comparator
            .comparing((Group g) -> g.earliest, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(g -> g.key.oldRun)
            .thenComparing(g -> g.key.commander, String.CASE_INSENSITIVE_ORDER));

        for (Group g : groupList) {
            g.rows.sort(Comparator
                .comparing((DataRow r) -> r.timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(r -> r.originalIndex));
        }

        int nextRun = 1;
        List<List<Object>> newDataRows = new ArrayList<>();
        for (Group g : groupList) {
            int newRun = nextRun++;
            for (DataRow r : g.rows) {
                while (r.cells.size() < 1) {
                    r.cells.add("");
                }
                r.cells.set(0, newRun);
                newDataRows.add(r.cells);
            }
        }

        List<List<Object>> updated = new ArrayList<>();
        updated.add(header);
        updated.addAll(newDataRows);

        ValueRange body = new ValueRange().setValues(updated);
        sheets.spreadsheets().values()
            .update(spreadsheetId, rangeA1PForSheetTitle(sheetTitle), body)
            .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
            .execute();

        return new int[] {groupList.size(), newDataRows.size()};
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    /** Blank, whitespace-only, or legacy "-" placeholder from older writes — treat as no location. */
    static boolean isBlankSheetCell(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        return t.isEmpty() || "-".equals(t);
    }

    static String normSheetCell(String s) {
        return isBlankSheetCell(s) ? "" : s.trim();
    }

    /**
     * Row identity for prospector upserts is (run, asteroid, material, commander) only — not system/body.
     * Location columns are display data; journal system/body changes must never skip an upsert and append a duplicate row.
     */
    @SuppressWarnings("unused")
    static boolean locationsCompatibleForUpsert(String incSys, String incBody, String existingSys, String existingBody) {
        return true;
    }

    private static boolean upsertCoreKeyMatches(List<Object> row, int run, String asteroid, String material, String commander) {
        if (row == null || row.size() < 4) {
            return false;
        }
        int existingRun = parseInt(row.get(0), 0);
        String existingAsteroid = str(row.get(1));
        String existingMaterial = row.size() > 3 ? str(row.get(3)) : "";
        String existingCommander = row.size() > 12 ? str(row.get(12)) : "";
        return existingRun == run
            && normSheetCell(existingAsteroid).equals(normSheetCell(asteroid))
            && normSheetCell(existingMaterial).equals(normSheetCell(material))
            && normSheetCell(existingCommander).equals(normSheetCell(commander));
    }

    /**
     * Find a data row (index into {@code values}) to upsert, or -1. When incoming system/body are blank,
     * prefers matches with a populated location. Among multiple rows matching the core key, prefer the most recent by
     * timestamp (column C), then the last row index.
     */
    static int findProspectorUpsertRowIndex(List<List<Object>> values, int run, String asteroid, String material,
            String commander, String incSystem, String incBody) {
        if (values == null || values.size() <= 1) {
            return -1;
        }
        String nis = normSheetCell(incSystem);
        String nib = normSheetCell(incBody);
        boolean incLocBlank = nis.isEmpty() && nib.isEmpty();

        if (incLocBlank) {
            int best = -1;
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null) {
                    continue;
                }
                if (!upsertCoreKeyMatches(row, run, asteroid, material, commander)) {
                    continue;
                }
                String es = row.size() > 10 ? str(row.get(10)) : "";
                String eb = row.size() > 11 ? str(row.get(11)) : "";
                if (!locationsCompatibleForUpsert(incSystem, incBody, es, eb)) {
                    continue;
                }
                if (!normSheetCell(es).isEmpty() || !normSheetCell(eb).isEmpty()) {
                    best = pickPreferredUpsertRow(values, best, i);
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        int best = -1;
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null) {
                continue;
            }
            if (!upsertCoreKeyMatches(row, run, asteroid, material, commander)) {
                continue;
            }
            String es = row.size() > 10 ? str(row.get(10)) : "";
            String eb = row.size() > 11 ? str(row.get(11)) : "";
            if (locationsCompatibleForUpsert(incSystem, incBody, es, eb)) {
                best = pickPreferredUpsertRow(values, best, i);
            }
        }
        return best;
    }

    private static int pickPreferredUpsertRow(List<List<Object>> values, int currentBestIdx, int candidateIdx) {
        if (candidateIdx < 0) {
            return currentBestIdx;
        }
        if (currentBestIdx < 0) {
            return candidateIdx;
        }
        List<Object> best = currentBestIdx < values.size() ? values.get(currentBestIdx) : null;
        List<Object> cand = candidateIdx < values.size() ? values.get(candidateIdx) : null;
        Instant bestTs = (best != null && best.size() > 2) ? parseTimestampCell(best.get(2)) : null;
        Instant candTs = (cand != null && cand.size() > 2) ? parseTimestampCell(cand.get(2)) : null;
        if (bestTs == null && candTs != null) {
            return candidateIdx;
        }
        if (bestTs != null && candTs != null && candTs.isAfter(bestTs)) {
            return candidateIdx;
        }
        if (bestTs != null && candTs == null) {
            return currentBestIdx;
        }
        return candidateIdx > currentBestIdx ? candidateIdx : currentBestIdx;
    }

    /**
     * Keep rows stable and human-scannable in Sheets: same commander/run rows stay together and asteroid letters
     * sort A, B, ... Z, AA, AB so a late append for asteroid A does not appear far below newer asteroids.
     */
    static void sortProspectorDataRowsInPlace(List<List<Object>> values) {
        if (values == null || values.size() <= 2) {
            return;
        }
        List<Object> header = values.get(0);
        List<List<Object>> data = new ArrayList<>(values.subList(1, values.size()));
        data.sort(Comparator
                .comparingInt((List<Object> row) -> parseInt(row != null && row.size() > 0 ? row.get(0) : null, Integer.MAX_VALUE))
                .thenComparing((List<Object> row) -> str(row != null && row.size() > 12 ? row.get(12) : ""), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(row -> asteroidIdSortIndex(row != null && row.size() > 1 ? str(row.get(1)) : ""))
                .thenComparing((List<Object> row) -> {
                    Instant ts = row != null && row.size() > 2 ? parseTimestampCell(row.get(2)) : null;
                    return ts != null ? ts : Instant.EPOCH;
                })
                .thenComparing((List<Object> row) -> str(row != null && row.size() > 3 ? row.get(3) : ""), String.CASE_INSENSITIVE_ORDER));
        values.clear();
        values.add(header);
        values.addAll(data);
    }

    static int asteroidIdSortIndex(String asteroidId) {
        String s = asteroidId != null ? asteroidId.trim().toUpperCase(Locale.ROOT) : "";
        if (s.isEmpty() || "-".equals(s)) {
            return Integer.MAX_VALUE;
        }
        int idx = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                return Integer.MAX_VALUE;
            }
            idx = idx * 26 + (ch - 'A' + 1);
        }
        return idx - 1;
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

    public ProspectorWriteResult updateRunEndTimeResult(String commander, int run, Instant endTime) {
        if (endTime == null) {
            return ProspectorWriteResult.ok();
        }
        if (spreadsheetId.isEmpty()) {
            return missingSpreadsheetUrlFailure();
        }
        String sheetTitle = MiningSheetTitles.sheetTitleForCommander(commander);
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return authFailure();
            }
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1PForSheetTitle(sheetTitle))
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.size() < 2) {
                return ProspectorWriteResult.ok();
            }
            normalizeProspectorSheetValuesInPlace(values);
            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US);
            String endStr = endTime.atZone(zone).format(fmt);
            String cmdr = commander != null ? commander : "";
            int rowIndex = ProspectorMiningLogPolicy.findDataRowIndexForCanonicalRunEnd(values, run, cmdr);
            if (rowIndex >= 0) {
                List<Object> row = values.get(rowIndex);
                ensureRowSize(row, 16);
                row.set(sheetRunEndColumnIndex(row), endStr);
                ValueRange bodyRange = new ValueRange().setValues(values);
                sheets.spreadsheets().values()
                        .update(spreadsheetId, rangeA1PForSheetTitle(sheetTitle), bodyRange)
                        .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                        .execute();
            }
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            if (sheetErrorSuggestsMissingWorksheet(e.getMessage())) {
                forgetEnsuredSheetWithHeader(spreadsheetId, sheetTitle);
            }
            return ProspectorWriteResult.failure(truncateMsg(e.getMessage()), e);
        }
    }

    @Override
    public void updateRunEndTime(String commander, int run, Instant endTime) {
        updateRunEndTimeResult(commander, run, endTime);
    }

    /**
     * Renumber rows in memory (header + data), same rules as {@link #renumberOneSheet}.
     */
    private List<List<Object>> renumberSheetValuesInMemory(List<List<Object>> values) {
        if (values == null || values.size() <= 1) {
            return values;
        }
        normalizeProspectorSheetValuesInPlace(values);
        List<Object> header = values.get(0);
        List<DataRow> dataRows = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row == null || row.isEmpty()) {
                continue;
            }
            int run = parseInt(row.size() > 0 ? row.get(0) : null, 0);
            String commander;
            if (row.size() > 12) {
                commander = str(row.get(12));
            } else if (row.size() > 11) {
                commander = str(row.get(11));
            } else {
                commander = "";
            }
            String tsStr = row.size() > 2 ? str(row.get(2)) : "";
            Instant ts = parseTimestamp(tsStr);
            dataRows.add(new DataRow(i, run, commander, ts, row));
        }
        if (dataRows.isEmpty()) {
            return values;
        }
        Map<GroupKey, List<DataRow>> groups = new HashMap<>();
        for (DataRow r : dataRows) {
            GroupKey key = new GroupKey(r.oldRun, r.commander);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<Group> groupList = new ArrayList<>();
        for (Map.Entry<GroupKey, List<DataRow>> e : groups.entrySet()) {
            Instant earliest = null;
            for (DataRow r : e.getValue()) {
                if (r.timestamp == null) {
                    continue;
                }
                if (earliest == null || r.timestamp.isBefore(earliest)) {
                    earliest = r.timestamp;
                }
            }
            groupList.add(new Group(e.getKey(), e.getValue(), earliest));
        }
        groupList.sort(Comparator
            .comparing((Group g) -> g.earliest, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(g -> g.key.oldRun)
            .thenComparing(g -> g.key.commander, String.CASE_INSENSITIVE_ORDER));
        for (Group g : groupList) {
            g.rows.sort(Comparator
                .comparing((DataRow r) -> r.timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(r -> r.originalIndex));
        }
        int nextRun = 1;
        List<List<Object>> newDataRows = new ArrayList<>();
        for (Group g : groupList) {
            int newRun = nextRun++;
            for (DataRow r : g.rows) {
                while (r.cells.size() < 1) {
                    r.cells.add("");
                }
                r.cells.set(0, newRun);
                newDataRows.add(r.cells);
            }
        }
        List<List<Object>> updated = new ArrayList<>();
        updated.add(header);
        updated.addAll(newDataRows);
        return updated;
    }

    /**
     * Split legacy (first) sheet rows by commander into per-commander worksheets and renumber 1..n per sheet.
     */
    public ProspectorWriteResult migrateLegacySheetToCommanderTabs() {
        if (spreadsheetId.isEmpty()) {
            return ProspectorWriteResult.failure("No spreadsheet ID");
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return authFailure();
            }
            Spreadsheet spr = sheets.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheetList = spr.getSheets();
            if (sheetList == null || sheetList.isEmpty()) {
                OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
                return ProspectorWriteResult.ok();
            }
            String legacyTitle = sheetList.get(0).getProperties().getTitle();
            if (legacyTitle == null) {
                legacyTitle = "Sheet1";
            }
            ValueRange vr = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1PForSheetTitle(legacyTitle))
                .execute();
            List<List<Object>> values = vr.getValues();
            if (legacySheetIsEmptyOrHeaderOnly(values)) {
                OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
                return ProspectorWriteResult.ok();
            }
            if (legacySheetIsMigrationNoteOnly(values)) {
                OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
                return ProspectorWriteResult.ok();
            }
            normalizeProspectorSheetValuesInPlace(values);
            Set<String> usedTitles = new HashSet<>();
            for (Sheet sh : sheetList) {
                SheetProperties p = sh.getProperties();
                if (p != null && p.getTitle() != null) {
                    usedTitles.add(p.getTitle());
                }
            }
            Map<String, List<List<Object>>> byCmdr = new LinkedHashMap<>();
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null || row.isEmpty()) {
                    continue;
                }
                String cmdr;
                if (row.size() > 12) {
                    cmdr = str(row.get(12));
                } else if (row.size() > 11) {
                    cmdr = str(row.get(11));
                } else {
                    cmdr = "-";
                }
                if (cmdr.isBlank()) {
                    cmdr = "-";
                }
                List<Object> migrated = new ArrayList<>(row);
                byCmdr.computeIfAbsent(cmdr, k -> new ArrayList<>()).add(migrated);
            }
            if (byCmdr.isEmpty()) {
                OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
                return ProspectorWriteResult.ok();
            }
            for (Map.Entry<String, List<List<Object>>> e : byCmdr.entrySet()) {
                List<List<Object>> sheetGrid = new ArrayList<>();
                sheetGrid.add(headerRow16());
                sheetGrid.addAll(e.getValue());
                List<List<Object>> renumbered = renumberSheetValuesInMemory(sheetGrid);
                String wantTitle = MiningSheetTitles.sheetTitleForCommander(e.getKey());
                String unique = MiningSheetTitles.uniqueTitle(wantTitle, usedTitles);
                usedTitles.add(unique);
                ensureSheetWithHeader(sheets, unique);
                ValueRange body = new ValueRange().setValues(renumbered);
                sheets.spreadsheets().values()
                    .update(spreadsheetId, rangeA1PForSheetTitle(unique), body)
                    .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                    .execute();
            }
            List<List<Object>> cleared = new ArrayList<>();
            cleared.add(headerRow16());
            List<Object> noteRow = new ArrayList<>();
            for (int c = 0; c < 16; c++) {
                noteRow.add(c == 0 ? ("Migrated to per-commander tabs — " + LocalDate.now()) : "");
            }
            cleared.add(noteRow);
            sheets.spreadsheets().values()
                .update(spreadsheetId, rangeA1PForSheetTitle(legacyTitle), new ValueRange().setValues(cleared))
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .execute();
            OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
            return ProspectorWriteResult.ok();
        } catch (Exception e) {
            return ProspectorWriteResult.failure(truncateMsg(e.getMessage()), e);
        }
    }

    /**
     * First launch: migrate legacy mixed sheet to per-commander tabs when layout version is still 0.
     */
    public static void scheduleFirstLaunchMigration(OverlayFrame frame) {
        if (!shouldRunFirstLaunchMiningSheetMigration()) {
            return;
        }
        String url = OverlayPreferences.getMiningGoogleSheetsUrl();
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private ProspectorWriteResult result;

            @Override
            protected Void doInBackground() {
                GoogleSheetsBackend backend = new GoogleSheetsBackend(url);
                result = backend.migrateLegacySheetToCommanderTabs();
                return null;
            }

            @Override
            protected void done() {
                if (result != null && result.isOk()) {
                    OverlayPreferences.setMiningGoogleSheetsLayoutVersion(LAYOUT_VERSION_PER_COMMANDER);
                    if (frame != null) {
                        frame.clearMiningSheetsStatusError();
                    }
                } else if (frame != null && result != null) {
                    frame.setMiningSheetsStatusError("Mining sheet migration: " + result.getMessage());
                }
            }
        };
        worker.execute();
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

    /**
     * Parse a sheet cell that may be a formatted string, ISO text, Unix epoch number, or Sheets serial date.
     * Package-private for tests in {@code org.dce.ed.mining}.
     */
    static Instant parseTimestampCell(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return instantFromNumericTimestamp(n.doubleValue());
        }
        return parseTimestamp(str(o));
    }

    /**
     * Interpret numeric cells from Google Sheets API: serial dates (~25k–60k), Unix seconds, or milliseconds.
     */
    private static Instant instantFromNumericTimestamp(double n) {
        if (Double.isNaN(n) || !Double.isFinite(n)) {
            return null;
        }
        // Milliseconds since epoch (e.g. 1.71e12)
        if (n >= 1e11) {
            return Instant.ofEpochMilli((long) n);
        }
        // Seconds since epoch
        if (n >= 1e9 && n < 1e11) {
            return Instant.ofEpochSecond((long) n);
        }
        // Google Sheets / Excel serial: days since 1899-12-30 (UTC), optional fractional day
        if (n > 20_000 && n < 80_000) {
            return sheetsSerialDateToInstant(n);
        }
        return null;
    }

    private static Instant sheetsSerialDateToInstant(double serial) {
        long wholeDays = (long) Math.floor(serial);
        double frac = serial - wholeDays;
        LocalDate base = LocalDate.of(1899, 12, 30);
        long nanos = (long) Math.round(frac * 86_400_000_000_000L);
        return base.plusDays(wholeDays).atStartOfDay(ZoneOffset.UTC).plusNanos(nanos).toInstant();
    }

    /**
     * Encodes system and body for {@link ProspectorLogRow#getFullBodyName()} so {@link #splitSystemAndBody} can
     * recover columns: when the body is empty, uses {@code system + " >"} (not raw system) so the name is not
     * mistaken for a body-only value.
     */
    public static String buildFullBodyNameForProspectorRow(String system, String body) {
        String sys = system != null ? system.trim() : "";
        String b = body != null ? body.trim() : "";
        if (sys.isEmpty() && b.isEmpty()) {
            return "";
        }
        if (sys.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return sys + " >";
        }
        return sys + " > " + b;
    }

    /**
     * Split a combined \"system > body\" string into [system, body], with ring / duplicate-prefix cleanup.
     * Public for {@link org.dce.ed.MiningTabPanel} table display (same rules as sheet columns).
     */
    public static String[] splitSystemAndBody(String fullBodyName) {
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
            int idx2 = s.indexOf(" >");
            if (idx2 >= 0) {
                system = s.substring(0, idx2).trim();
                body = s.substring(idx2 + 2).trim();
            } else {
                body = s;
            }
        }
        // If the body repeats the full system name, strip it only when followed by a space and the system
        // name is long enough to avoid treating \"1\" as a prefix of \"1 B Ring\" (which would yield \"B\").
        if (system.length() >= 3 && body.startsWith(system + " ")) {
            body = body.substring(system.length() + 1).trim();
        }
        body = stripEliteAbRingSuffix(body);
        return new String[] {system, body};
    }

    /** Removes trailing Elite {@code A Ring} / {@code B Ring} from the body fragment (inner vs outer belt). */
    static String stripEliteAbRingSuffix(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return ELITE_AB_RING_SUFFIX.matcher(body.trim()).replaceFirst("").trim();
    }

    private static String buildFullBodyName(String system, String body) {
        return buildFullBodyNameForProspectorRow(system, body);
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
