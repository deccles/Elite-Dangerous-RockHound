package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dce.ed.OverlayPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleSheetsBackendLegacyMigrationTest {

    private int savedLayoutVersion;
    private String savedBackend;
    private String savedUrl;

    @BeforeEach
    void savePrefs() {
        savedLayoutVersion = OverlayPreferences.getMiningGoogleSheetsLayoutVersion();
        savedBackend = OverlayPreferences.getMiningLogBackend();
        savedUrl = OverlayPreferences.getMiningGoogleSheetsUrl();
    }

    @AfterEach
    void restorePrefs() {
        OverlayPreferences.setMiningGoogleSheetsLayoutVersion(savedLayoutVersion);
        OverlayPreferences.setMiningLogBackend(savedBackend);
        if (savedUrl == null || savedUrl.isBlank()) {
            OverlayPreferences.clearMiningGoogleSheetsUrl();
        } else {
            OverlayPreferences.setMiningGoogleSheetsUrl(savedUrl);
        }
    }

    @Test
    void legacySheetIsEmptyOrHeaderOnly() {
        assertTrue(GoogleSheetsBackend.legacySheetIsEmptyOrHeaderOnly(null));
        assertTrue(GoogleSheetsBackend.legacySheetIsEmptyOrHeaderOnly(List.of()));
        assertTrue(GoogleSheetsBackend.legacySheetIsEmptyOrHeaderOnly(List.of(List.of("Run"))));
        assertFalse(GoogleSheetsBackend.legacySheetIsEmptyOrHeaderOnly(
                Arrays.asList(List.of("h"), List.of("1"))));
    }

    @Test
    void legacySheetIsMigrationNoteOnly() {
        List<List<Object>> note = Arrays.asList(
                List.of("Run"),
                List.of("Migrated to per-commander tabs — 2026-04-06"));
        assertTrue(GoogleSheetsBackend.legacySheetIsMigrationNoteOnly(note));
        assertFalse(GoogleSheetsBackend.legacySheetIsMigrationNoteOnly(
                Arrays.asList(List.of("h"), List.of("19", "A"))));
    }

    @Test
    void legacySheetNeedsCommanderTabSplit() {
        assertFalse(GoogleSheetsBackend.legacySheetNeedsCommanderTabSplit(null));
        List<List<Object>> data = Arrays.asList(
                List.of("Run"),
                List.of(19, "A", "2026/1/1 10:00", "X", 10, 0, 1, 1, "", 0, "S", "B", "Cmdr", "", ""));
        assertTrue(GoogleSheetsBackend.legacySheetNeedsCommanderTabSplit(data));
    }

    @Test
    void prospectorMergedRowsByTimestamp_sortsAscendingNullsLast() {
        Instant t1 = Instant.parse("2026-04-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-04-02T10:00:00Z");
        List<ProspectorLogRow> rows = new ArrayList<>();
        rows.add(new ProspectorLogRow(1, "S > B", t2, "m", 1, 0, 1, 1, "c"));
        rows.add(new ProspectorLogRow(1, "S > B", null, "m", 1, 0, 1, 1, "c"));
        rows.add(new ProspectorLogRow(1, "S > B", t1, "m", 1, 0, 1, 1, "c"));
        rows.sort(GoogleSheetsBackend.PROSPECTOR_MERGED_ROWS_BY_TIMESTAMP);
        assertEquals(t1, rows.get(0).getTimestamp());
        assertEquals(t2, rows.get(1).getTimestamp());
        assertEquals(null, rows.get(2).getTimestamp());
    }

    @Test
    void shouldRunFirstLaunchMiningSheetMigration_respectsPrefs() {
        OverlayPreferences.setMiningGoogleSheetsLayoutVersion(0);
        OverlayPreferences.setMiningLogBackend("google");
        OverlayPreferences.setMiningGoogleSheetsUrl("https://docs.google.com/spreadsheets/d/abc123/edit");
        assertTrue(GoogleSheetsBackend.shouldRunFirstLaunchMiningSheetMigration());

        OverlayPreferences.setMiningGoogleSheetsLayoutVersion(
                GoogleSheetsBackend.MINING_LAYOUT_VERSION_PER_COMMANDER_TABS);
        assertFalse(GoogleSheetsBackend.shouldRunFirstLaunchMiningSheetMigration());

        OverlayPreferences.setMiningGoogleSheetsLayoutVersion(0);
        OverlayPreferences.setMiningLogBackend("local");
        assertFalse(GoogleSheetsBackend.shouldRunFirstLaunchMiningSheetMigration());

        OverlayPreferences.setMiningLogBackend("google");
        OverlayPreferences.clearMiningGoogleSheetsUrl();
        assertFalse(GoogleSheetsBackend.shouldRunFirstLaunchMiningSheetMigration());
    }
}
