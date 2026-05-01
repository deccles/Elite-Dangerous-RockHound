package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class GoogleSheetsBackendSplitAndTimestampTest {

    @Test
    void locationsCompatibleForUpsert_blankIncoming_matchesAnyExisting() {
        assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("", "", "Sol", "Mars"));
        assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("", "", "-", "-"));
    }

    @Test
    void locationsCompatibleForUpsert_exactMatch() {
        assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "Mars", "Sol", "Mars"));
        assertTrue(GoogleSheetsBackend.locationsCompatibleForUpsert("Sol", "Mars", "Sol", "Phobos"));
    }

    @Test
    void findProspectorUpsertRowIndex_prefersRowWithLocationWhenIncomingBlank() {
        List<List<Object>> values = new java.util.ArrayList<>();
        values.add(Arrays.asList("Run", "A", "Time", "Mat", "0", "0", "0", "0", "c", "0", "Sys", "Body", "Cmdr"));
        values.add(Arrays.asList(8, "A", "t", "Tritium", 0, 0, 0, 0, "-", 0, "-", "-", "Villunus"));
        values.add(Arrays.asList(8, "A", "t", "Tritium", 0, 0, 0, 0, "-", 0, "Phraa", "Ring 1", "Villunus"));
        int idx = GoogleSheetsBackend.findProspectorUpsertRowIndex(values, 8, "A", "Tritium", "Villunus", "", "");
        assertEquals(2, idx);
    }

    @Test
    void buildFullBodyNameForProspectorRow_systemOnly_splitsToSystemColumnNotBody() {
        String enc = GoogleSheetsBackend.buildFullBodyNameForProspectorRow("Phraa Flyuae", "");
        String[] p = GoogleSheetsBackend.splitSystemAndBody(enc);
        assertEquals("Phraa Flyuae", p[0]);
        assertEquals("", p[1]);
    }

    @Test
    void splitSystemAndBody_doesNotStripSingleDigitSystemFromRingName() {
        // Regression: old logic used body.startsWith(system) and turned "1 B Ring" into "B".
        String[] p = GoogleSheetsBackend.splitSystemAndBody("1 > 1 B Ring");
        assertEquals("1", p[0]);
        assertEquals("1", p[1]);
    }

    @Test
    void splitSystemAndBody_stripsDuplicateLongSystemPrefixBeforeRing() {
        String[] p = GoogleSheetsBackend.splitSystemAndBody("Achenar > Achenar 3 A Ring");
        assertEquals("Achenar", p[0]);
        assertEquals("3", p[1]);
    }

    @Test
    void stripEliteAbRingSuffix_stripsInnerOrOuterBeltOnly() {
        assertEquals("6", GoogleSheetsBackend.stripEliteAbRingSuffix("6 B Ring"));
        assertEquals("6", GoogleSheetsBackend.stripEliteAbRingSuffix("6 A Ring"));
        assertEquals("3", GoogleSheetsBackend.stripEliteAbRingSuffix("3 a ring"));
        // Moons (lowercase letter, no " Ring") unchanged
        assertEquals("6 b", GoogleSheetsBackend.stripEliteAbRingSuffix("6 b"));
    }

    @Test
    void parseTimestampCell_sheetsSerial_returnsInstant() {
        Instant t = GoogleSheetsBackend.parseTimestampCell(45_000.25);
        assertNotNull(t);
        assertTrue(t.isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }

    @Test
    void parseTimestampCell_unixMillis() {
        Instant expected = Instant.ofEpochMilli(1_700_000_000_000L);
        assertEquals(expected, GoogleSheetsBackend.parseTimestampCell(1_700_000_000_000.0));
    }

    /**
     * When System column was left blank but Body holds "Sys > body", infer system and normalize duplicate body prefix.
     */
    @Test
    void parseSheetDataRows_infersSystemFromCombinedBodyColumn() throws Exception {
        var m = GoogleSheetsBackend.class.getDeclaredMethod(
                "parseSheetDataRows", List.class, String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ProspectorLogRow> rows = (List<ProspectorLogRow>) m.invoke(
                new GoogleSheetsBackend("https://docs.google.com/spreadsheets/d/x/edit"),
                Arrays.asList(
                        Arrays.asList(
                                "Run", "Asteroid", "Time", "Mat", "0", "0", "0", "0", "c", "0",
                                "System", "Body", "Cmdr", "Start", "End"),
                        Arrays.asList(
                                1,
                                "A",
                                "2026-04-01 10:00:00",
                                "Painite",
                                10.0,
                                0.0,
                                1.0,
                                1.0,
                                "-",
                                0,
                                "",
                                "UkeBard > UkeBard 2 A Ring",
                                "UkeBard",
                                "",
                                "")),
                "CMDR UkeBard");
        assertEquals(1, rows.size());
        assertEquals("UkeBard > 2", rows.get(0).getFullBodyName());
    }
}
