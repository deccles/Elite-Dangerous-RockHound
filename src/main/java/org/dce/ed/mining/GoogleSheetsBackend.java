package org.dce.ed.mining;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dce.ed.OverlayPreferences;

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
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("M/d/yyyy H:m:s", Locale.US),
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

    /** Sheet range: Run, Timestamp, Type, Percentage, Before Amount, After Amount, Actual, Body, Commander (A–I). */
    private static String rangeA1I() {
        return "A:I";
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
                String body = (r.getFullBodyName() != null && !r.getFullBodyName().isEmpty()) ? r.getFullBodyName() : "-";
                String commander = (r.getCommanderName() != null && !r.getCommanderName().isEmpty()) ? r.getCommanderName() : "-";
                String material = (r.getMaterial() != null && !r.getMaterial().isEmpty()) ? r.getMaterial() : "-";
                List<Object> row = new ArrayList<>();
                row.add(r.getRun());
                row.add(ts);
                row.add(material);
                row.add(r.getPercent());
                row.add(r.getBeforeAmount());
                row.add(r.getAfterAmount());
                row.add(r.getDifference());
                row.add(body);
                row.add(commander);
                values.add(row);
            }
            ValueRange bodyRange = new ValueRange().setValues(values);
            AppendValuesResponse res = sheets.spreadsheets().values()
                .append(spreadsheetId, rangeA1I(), bodyRange)
                .setValueInputOption(VALUE_INPUT_OPTION_USER_ENTERED)
                .setInsertDataOption("INSERT_ROWS")
                .execute();
        } catch (Exception e) {
            // don't break UI; caller may log
        }
    }

    @Override
    public List<ProspectorLogRow> loadRows() {
        if (spreadsheetId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Sheets sheets = createSheetsService();
            if (sheets == null) {
                return Collections.emptyList();
            }
            ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, rangeA1I())
                .execute();
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            List<ProspectorLogRow> out = new ArrayList<>();
            // Skip header row (row 0). 9 cols: run,timestamp,material,percent,before,after,actual,body,commander
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row == null || row.size() < 9) continue;
                try {
                    int run = parseInt(row.get(0), 0);
                    Instant ts = parseTimestamp(str(row.get(1)));
                    String material = str(row.get(2));
                    double percent = parseDouble(row.get(3), 0.0);
                    double before = parseDouble(row.get(4), 0.0);
                    double after = parseDouble(row.get(5), 0.0);
                    double diff = parseDouble(row.get(6), 0.0);
                    String fullBodyName = str(row.get(7));
                    String commander = str(row.get(8));
                    out.add(new ProspectorLogRow(run, fullBodyName, ts, material, percent, before, after, diff, commander));
                } catch (Exception ignored) {
                }
            }
            out.sort(java.util.Comparator.comparing(ProspectorLogRow::getTimestamp, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
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
        for (DateTimeFormatter fmt : TIMESTAMP_PARSERS) {
            try {
                return java.time.LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
