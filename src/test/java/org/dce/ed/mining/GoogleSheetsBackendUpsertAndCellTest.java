package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for sheet cell normalization, upsert location matching, prospector row index selection,
 * full-body encoding, and timestamp parsing helpers on {@link GoogleSheetsBackend}.
 */
class GoogleSheetsBackendUpsertAndCellTest {

    private static List<Object> dataRow(
            int run,
            String asteroid,
            String material,
            String system,
            String body,
            String commander) {
        List<Object> r = new ArrayList<>(15);
        r.add(run);
        r.add(asteroid);
        r.add("2026-04-01 10:00:00");
        r.add(material);
        for (int i = 4; i <= 9; i++) {
            r.add(i == 8 ? "-" : 0);
        }
        r.add(system);
        r.add(body);
        r.add(commander);
        r.add("");
        r.add("");
        return r;
    }

    @Nested
    class BlankCells {
        @Test
        void isBlankSheetCell_nullDashWhitespace() {
            assertTrue(GoogleSheetsBackend.isBlankSheetCell(null));
            assertTrue(GoogleSheetsBackend.isBlankSheetCell(""));
            assertTrue(GoogleSheetsBackend.isBlankSheetCell("   "));
            assertTrue(GoogleSheetsBackend.isBlankSheetCell("-"));
            assertTrue(GoogleSheetsBackend.isBlankSheetCell("  -  "));
            assertFalse(GoogleSheetsBackend.isBlankSheetCell("Sol"));
            assertFalse(GoogleSheetsBackend.isBlankSheetCell("0"));
        }

        @Test
        void normSheetCell_blankBecomesEmpty() {
            assertEquals("", GoogleSheetsBackend.normSheetCell(null));
            assertEquals("", GoogleSheetsBackend.normSheetCell("-"));
            assertEquals("", GoogleSheetsBackend.normSheetCell(" \t "));
            assertEquals("Mars", GoogleSheetsBackend.normSheetCell("  Mars  "));
        }
    }

    @Nested
    class LocationsCompatible {
        @Test
        void exactMatch_afterTrim() {
            assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("  Sol ", " Mars\t", "Sol", "Mars"));
        }

        @Test
        void blankIncoming_matchesAnyExisting() {
            assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("", "", "Any", "Body"));
            assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("  ", " - ", "X", "Y"));
        }

        @Test
        void incomingFillsOnlyWhenExistingBlank() {
            assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "2", "", ""));
            assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "2", "-", "-"));
            assertFalse(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "2", "Other", ""));
            assertFalse(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "2", "", "Moon"));
        }

        @Test
        void mismatchWhenBothSidesHaveValues() {
            assertFalse(GoogleSheetsBackend.locationsCompatibleForUpsert("A", "B", "A", "C"));
            assertFalse(GoogleSheetsBackend.locationsCompatibleForUpsert("A", "B", "C", "B"));
        }
    }

    @Nested
    class FindProspectorUpsertRowIndex {
        @Test
        void nullOrHeaderOnly_returnsMinusOne() {
            assertEquals(-1, GoogleSheetsBackend.findProspectorUpsertRowIndex(null, 1, "A", "T", "C", "S", "B"));
            assertEquals(-1, GoogleSheetsBackend.findProspectorUpsertRowIndex(List.of(), 1, "A", "T", "C", "S", "B"));
            assertEquals(
                    -1,
                    GoogleSheetsBackend.findProspectorUpsertRowIndex(
                            List.of(List.of("h")), 1, "A", "T", "C", "S", "B"));
        }

        @Test
        void nonBlankIncoming_firstCompatibleRow() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            values.add(dataRow(3, "A", "Painite", "Sol", "Ring", "Villunus"));
            values.add(dataRow(3, "B", "Tritium", "Sol", "Ring", "Villunus"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 3, "A", "Painite", "Villunus", "Sol", "Ring");
            assertEquals(1, idx);
        }

        @Test
        void nonBlankIncoming_skipsWhenLocationIncompatible() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            values.add(dataRow(3, "A", "Painite", "Alpha", "R1", "Villunus"));
            values.add(dataRow(3, "A", "Painite", "Beta", "R2", "Villunus"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 3, "A", "Painite", "Villunus", "Beta", "R2");
            assertEquals(2, idx);
        }

        @Test
        void nonBlankIncoming_noMatch_returnsMinusOne() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            values.add(dataRow(3, "A", "Painite", "Sol", "Ring", "Villunus"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 9, "A", "Painite", "Villunus", "Sol", "Ring");
            assertEquals(-1, idx);
        }

        @Test
        void shortRow_stillMatchesWhenCoreColumnsPresent() {
            // Commander at index 12 missing → treated as "" per upsertCoreKeyMatches; must match commander "-"
            List<Object> shortRow = Arrays.asList(5, "A", "t", "Opal", 0, 0, 0, 0, "-", 0, "S", "B");
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("h"));
            values.add(shortRow);
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 5, "A", "Opal", "-", "S", "B");
            assertEquals(1, idx);
        }

        @Test
        void shortRow_wrongCommander_noMatch() {
            List<Object> shortRow = Arrays.asList(5, "A", "t", "Opal", 0, 0, 0, 0, "-", 0, "S", "B");
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("h"));
            values.add(shortRow);
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 5, "A", "Opal", "Villunus", "S", "B");
            assertEquals(-1, idx);
        }

        @Test
        void blankIncoming_prefersRowWithPopulatedLocation() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            values.add(dataRow(8, "A", "Tritium", "-", "-", "UkeBard"));
            values.add(dataRow(8, "A", "Tritium", "Phraa", "6 B Ring", "UkeBard"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 8, "A", "Tritium", "UkeBard", "", "");
            assertEquals(2, idx);
        }

        @Test
        void blankIncoming_onlyBlankLocationRows_returnsFirstMatchInSecondPass() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            values.add(dataRow(2, "C", "Gold", "", "", "Z"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 2, "C", "Gold", "Z", "", "");
            assertEquals(1, idx);
        }

        @Test
        void asteroidAndMaterialTrimmedForMatch() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("h"));
            values.add(dataRow(1, " A ", " Painite ", "S", "B", "C"));
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 1, "A", "Painite", "C", "S", "B");
            assertEquals(1, idx);
        }

        @Test
        void duplicateMatches_prefersNewestTimestampRow() {
            List<List<Object>> values = new ArrayList<>();
            values.add(List.of("Run", "A", "T", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
            List<Object> older = dataRow(3, "D", "Tritium", "Byua", "6", "Villunus");
            older.set(2, "4/30/2026 12:00:00");
            List<Object> newer = dataRow(3, "D", "Tritium", "Byua", "6", "Villunus");
            newer.set(2, "4/30/2026 12:10:00");
            values.add(older);
            values.add(newer);
            int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 3, "D", "Tritium", "Villunus", "Byua", "6");
            assertEquals(2, idx);
        }
    }

    @Nested
    class FullBodyName {
        @Test
        void roundTrip_systemAndBody() {
            String enc = GoogleSheetsBackend.buildFullBodyNameForProspectorRow("Sys A", "3 B Ring");
            assertEquals("Sys A > 3 B Ring", enc);
            String[] p = GoogleSheetsBackend.splitSystemAndBody(enc);
            assertEquals("Sys A", p[0]);
            assertEquals("3", p[1]);
        }

        @Test
        void bodyOnly_noSystem() {
            String enc = GoogleSheetsBackend.buildFullBodyNameForProspectorRow("", "Custom Body Name");
            assertEquals("Custom Body Name", enc);
            String[] p = GoogleSheetsBackend.splitSystemAndBody(enc);
            assertEquals("", p[0]);
            assertEquals("Custom Body Name", p[1]);
        }

        @Test
        void bothEmpty() {
            assertEquals("", GoogleSheetsBackend.buildFullBodyNameForProspectorRow("", ""));
            assertEquals("", GoogleSheetsBackend.buildFullBodyNameForProspectorRow(null, null));
        }

        @Test
        void systemOnly_usesSentinelForSplit() {
            String enc = GoogleSheetsBackend.buildFullBodyNameForProspectorRow("Juenae BC-B d1-7935", "");
            String[] p = GoogleSheetsBackend.splitSystemAndBody(enc);
            assertEquals("Juenae BC-B d1-7935", p[0]);
            assertEquals("", p[1]);
        }
    }

    @Nested
    class ParseTimestampCell {
        @Test
        void null_returnsNull() {
            assertNull(GoogleSheetsBackend.parseTimestampCell(null));
        }

        @Test
        void isoInstantString() {
            Instant t = GoogleSheetsBackend.parseTimestampCell("2026-04-06T14:30:00Z");
            assertEquals(Instant.parse("2026-04-06T14:30:00Z"), t);
        }

        @Test
        void nanAndNonFinite_numeric_returnsNull() {
            assertNull(GoogleSheetsBackend.parseTimestampCell(Double.NaN));
            assertNull(GoogleSheetsBackend.parseTimestampCell(Double.POSITIVE_INFINITY));
        }

        @Test
        void numericTooSmall_returnsNull() {
            assertNull(GoogleSheetsBackend.parseTimestampCell(1000.0));
        }

        @Test
        void unixSeconds_numeric() {
            Instant t = GoogleSheetsBackend.parseTimestampCell(1_700_000_000.0);
            assertEquals(Instant.ofEpochSecond(1_700_000_000L), t);
        }
    }

    @Nested
    class ProspectorRowWidthNormalization {
        private List<Object> modernHeader16() {
            return List.of(
                    "Run",
                    "Asteroid",
                    "Timestamp",
                    "Type",
                    "%",
                    "Before",
                    "After",
                    "Actual",
                    "Core",
                    "Duds",
                    "System",
                    "Body",
                    "Commander",
                    "Ship",
                    "Start time",
                    "End time");
        }

        @Test
        void headerDefinesShip_whenColumnNIsShip() {
            assertTrue(GoogleSheetsBackend.headerDefinesProspectorShipColumn(modernHeader16()));
            assertFalse(GoogleSheetsBackend.headerDefinesProspectorShipColumn(
                    List.of("Run", "A", "T", "M", "0", "0", "0", "0", "c", "0", "S", "B", "C", "Start time", "End time")));
        }

        @Test
        void modernLayout_trailingEndOmittedFromApi_padOnly_keepsShipAndStartColumns() {
            List<Object> header = modernHeader16();
            List<Object> row = new ArrayList<>();
            row.add(38);
            row.add("L");
            row.add("4/12/2026 9:18:27");
            row.add("Tritium");
            row.add(0.0);
            row.add(0);
            row.add(0);
            row.add(0);
            row.add("-");
            row.add(0);
            row.add("Eol Prou LW-L d");
            row.add("Hotspot");
            row.add("6 UkeBard");
            row.add("lakonminer");
            row.add("4/12/2026 12:29:00");
            GoogleSheetsBackend.normalizeProspectorDataRowForHeader(header, row);
            assertEquals(16, row.size());
            assertEquals("lakonminer", row.get(13).toString());
            assertEquals("4/12/2026 12:29:00", row.get(14).toString());
            assertEquals("", row.get(15).toString());
        }

        @Test
        void recoverMisplacedShip_movesNameFromEndWhenShipSlotEmpty() {
            List<Object> header = modernHeader16();
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < 13; i++) {
                row.add(i == 0 ? 3 : (i == 1 ? "B" : 0));
            }
            row.add("");
            row.add("");
            row.add("lakonminer");
            GoogleSheetsBackend.normalizeProspectorDataRowForHeader(header, row);
            assertEquals("lakonminer", row.get(13).toString());
            assertEquals("", row.get(14).toString());
            assertEquals("", row.get(15).toString());
        }

        @Test
        void recoverMisplacedShip_movesNameFromStartWhenEndEmpty() {
            List<Object> header = modernHeader16();
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < 13; i++) {
                row.add(i < 2 ? (i == 0 ? 3 : "C") : 0);
            }
            row.add("");
            row.add("lakonminer");
            row.add("");
            GoogleSheetsBackend.normalizeProspectorDataRowForHeader(header, row);
            assertEquals("lakonminer", row.get(13).toString());
            assertEquals("", row.get(14).toString());
            assertEquals("", row.get(15).toString());
        }

        @Test
        void repairDuplicateShip_clearsStartEndWhenSameAsShipAndNotTimestamps() {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < 13; i++) {
                row.add(i == 0 ? 1 : (i == 1 ? "A" : 0));
            }
            row.add("lakonminer");
            row.add("lakonminer");
            row.add("lakonminer");
            GoogleSheetsBackend.repairDuplicateShipInRunTimeColumns(row);
            assertEquals("lakonminer", row.get(13).toString());
            assertEquals("", row.get(14).toString());
            assertEquals("", row.get(15).toString());
        }

        @Test
        void normalizeSheetInPlace_upgradesFifteenWideCommanderStartEndRows() {
            List<List<Object>> values = new ArrayList<>();
            values.add(new ArrayList<>(List.of(
                    "Run",
                    "Asteroid",
                    "Timestamp",
                    "Type",
                    "%",
                    "Before",
                    "After",
                    "Actual",
                    "Core",
                    "Duds",
                    "System",
                    "Body",
                    "Commander",
                    "Start time",
                    "End time")));
            List<Object> row = new ArrayList<>();
            row.add(1);
            row.add("A");
            row.add("4/1/2026 10:00:00");
            row.add("Painite");
            row.add(0.0);
            row.add(0);
            row.add(0);
            row.add(0);
            row.add("-");
            row.add(0);
            row.add("Sol");
            row.add("Ring");
            row.add("Cmdr");
            row.add("4/1/2026 11:00:00");
            row.add("4/1/2026 12:00:00");
            values.add(row);
            GoogleSheetsBackend.normalizeProspectorSheetValuesInPlace(values);
            assertTrue(GoogleSheetsBackend.headerDefinesProspectorShipColumn(values.get(0)));
            assertEquals(16, values.get(1).size());
            assertEquals("Cmdr", values.get(1).get(12).toString());
            assertEquals("", values.get(1).get(13).toString());
            assertEquals("4/1/2026 11:00:00", values.get(1).get(14).toString());
            assertEquals("4/1/2026 12:00:00", values.get(1).get(15).toString());
        }

        @Test
        void normalizeSheetInPlace_wrongHeaderButSixteenWideRows_replacesHeaderOnly() {
            List<List<Object>> values = new ArrayList<>();
            values.add(new ArrayList<>(List.of(
                    "Run", "A", "T", "M", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Commander", "Start time", "End time", "X")));
            List<Object> row = new ArrayList<>();
            row.add(3);
            row.add("A");
            row.add("4/30/2026 10:00:00");
            row.add("Tritium");
            row.add(0.0);
            row.add(0);
            row.add(0);
            row.add(0);
            row.add("-");
            row.add(0);
            row.add("S");
            row.add("B");
            row.add("Villunus");
            row.add("lakonminer");
            row.add("4/30/2026 11:00:00");
            row.add("4/30/2026 11:50");
            values.add(row);
            GoogleSheetsBackend.normalizeProspectorSheetValuesInPlace(values);
            assertTrue(GoogleSheetsBackend.headerDefinesProspectorShipColumn(values.get(0)));
            assertEquals("lakonminer", values.get(1).get(13).toString());
            assertEquals("4/30/2026 11:00:00", values.get(1).get(14).toString());
            assertEquals("4/30/2026 11:50", values.get(1).get(15).toString());
        }
    }
}
