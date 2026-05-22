package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.dce.ed.MiningTabPanel;
import org.dce.ed.TestEnvironment;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.junit.jupiter.api.Test;

/**
 * JUnit tests using actual ProspectedAsteroid journal lines from test resources.
 * Validates that parsing and MiningTabPanel display the correct information.
 */
class ProspectedAsteroidJournalTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    private static final String SAMPLE_JOURNAL = "/org/dce/ed/logreader/sample_prospector_journal.log";

    /** Load sample ProspectedAsteroid lines from test resource. */
    static List<String> loadSampleJournalLines() throws Exception {
        List<String> lines = new ArrayList<>();
        try (var in = ProspectedAsteroidJournalTest.class.getResourceAsStream(SAMPLE_JOURNAL)) {
            assertNotNull(in, "Sample journal resource not found: " + SAMPLE_JOURNAL);
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && line.contains("\"event\":\"ProspectedAsteroid\"")) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }

    @Test
    void parseActualJournalLines_parsesAllProspectedAsteroidEvents() throws Exception {
        List<String> lines = loadSampleJournalLines();
        assertTrue(lines.size() >= 3, "Expected at least 3 ProspectedAsteroid lines in sample");

        EliteLogParser parser = new EliteLogParser();

        // First line: High content, platinum motherlode, 3 materials
        ProspectedAsteroidEvent e1 = (ProspectedAsteroidEvent) parser.parseRecord(lines.get(0));
        assertNotNull(e1);
        assertEquals("High", e1.getContent());
        assertEquals("platinum", e1.getMotherlodeMaterial());
        assertEquals(3, e1.getMaterials().size());
        assertEquals("platinum", e1.getMaterials().get(0).getName());
        assertEquals(32.5, e1.getMaterials().get(0).getProportion(), 1e-6);
        assertEquals("palladium", e1.getMaterials().get(1).getName());
        assertEquals(18.2, e1.getMaterials().get(1).getProportion(), 1e-6);
        assertEquals("gold", e1.getMaterials().get(2).getName());
        assertEquals(12.1, e1.getMaterials().get(2).getProportion(), 1e-6);

        // Second line: Medium content, no motherlode, 2 materials
        ProspectedAsteroidEvent e2 = (ProspectedAsteroidEvent) parser.parseRecord(lines.get(1));
        assertNotNull(e2);
        assertEquals("Medium", e2.getContent());
        assertTrue(e2.getMotherlodeMaterial() == null || e2.getMotherlodeMaterial().isEmpty());
        assertEquals(2, e2.getMaterials().size());
        assertEquals("tritium", e2.getMaterials().get(0).getName());
        assertEquals(25.0, e2.getMaterials().get(0).getProportion(), 1e-6);
        assertEquals("water", e2.getMaterials().get(1).getName());
        assertEquals(15.5, e2.getMaterials().get(1).getProportion(), 1e-6);

        // Third line: Low content, painite motherlode, 2 materials
        ProspectedAsteroidEvent e3 = (ProspectedAsteroidEvent) parser.parseRecord(lines.get(2));
        assertNotNull(e3);
        assertEquals("Low", e3.getContent());
        assertEquals("painite", e3.getMotherlodeMaterial());
        assertEquals(2, e3.getMaterials().size());
        assertEquals("painite", e3.getMaterials().get(0).getName());
        assertEquals(8.0, e3.getMaterials().get(0).getProportion(), 1e-6);
        assertEquals("platinum", e3.getMaterials().get(1).getName());
        assertEquals(4.2, e3.getMaterials().get(1).getProportion(), 1e-6);
    }

    @Test
    void updateFromProspector_firstLine_populatesDialogWithMaterialsAndCore() throws Exception {
        List<String> lines = loadSampleJournalLines();
        assertTrue(lines.size() >= 1);

        EliteLogParser parser = new EliteLogParser();
        ProspectedAsteroidEvent event = (ProspectedAsteroidEvent) parser.parseRecord(lines.get(0));
        assertNotNull(event);

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        MiningTabPanel panel = new MiningTabPanel(prices, () -> false);

        SwingUtilities.invokeAndWait(() -> {
            panel.updateFromProspector(event);
        });

        // First line: 3 materials + 1 motherlode (Core) row = 4 rows
        int rowCount = panel.getProspectorTableRowCount();
        assertEquals(4, rowCount, "Prospector table should have 4 rows (3 materials + 1 core)");

        // Column 0 = Material name. Panel sorts: core first, then by value/name. So we expect all four names to appear.
        List<String> materialCol = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            String cell = panel.getProspectorTableValueAt(r, 0);
            if (cell != null) materialCol.add(cell);
        }
        assertTrue(materialCol.stream().anyMatch(s -> s.contains("Platinum") || s.contains("platinum")),
            "Table should show Platinum: " + materialCol);
        assertTrue(materialCol.stream().anyMatch(s -> s.contains("Palladium") || s.contains("palladium")),
            "Table should show Palladium: " + materialCol);
        assertTrue(materialCol.stream().anyMatch(s -> s.contains("Gold") || s.contains("gold")),
            "Table should show Gold: " + materialCol);
        assertTrue(materialCol.stream().anyMatch(s -> s.contains("Core")),
            "Table should show motherlode Core row: " + materialCol);

        // Column 1 = Percent; at least one row should have a percentage
        boolean hasPercent = false;
        for (int r = 0; r < rowCount; r++) {
            String pct = panel.getProspectorTableValueAt(r, 1);
            if (pct != null && !pct.isEmpty()) hasPercent = true;
        }
        assertTrue(hasPercent, "At least one row should show percent");
    }

    @Test
    void updateFromProspector_mediumContentNoMotherlode_populatesTwoMaterials() throws Exception {
        List<String> lines = loadSampleJournalLines();
        assertTrue(lines.size() >= 2);

        EliteLogParser parser = new EliteLogParser();
        ProspectedAsteroidEvent event = (ProspectedAsteroidEvent) parser.parseRecord(lines.get(1));
        assertNotNull(event);

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        MiningTabPanel panel = new MiningTabPanel(prices, () -> false);

        SwingUtilities.invokeAndWait(() -> {
            panel.updateFromProspector(event);
        });

        int rowCount = panel.getProspectorTableRowCount();
        assertEquals(2, rowCount, "Prospector table should have 2 rows (no motherlode)");

        List<String> materialCol = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            String cell = panel.getProspectorTableValueAt(r, 0);
            if (cell != null) materialCol.add(cell);
        }
        assertTrue(materialCol.stream().anyMatch(s -> s.toLowerCase().contains("tritium")),
            "Table should show Tritium: " + materialCol);
        assertTrue(materialCol.stream().anyMatch(s -> s.toLowerCase().contains("water")),
            "Table should show Water: " + materialCol);
    }

    @Test
    void updateFromProspector_null_clearsTable() throws Exception {
        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        MiningTabPanel panel = new MiningTabPanel(prices, () -> false);

        List<String> lines = loadSampleJournalLines();
        ProspectedAsteroidEvent event = (ProspectedAsteroidEvent) new EliteLogParser().parseRecord(lines.get(0));
        SwingUtilities.invokeAndWait(() -> {
            panel.updateFromProspector(event);
        });
        assertEquals(4, panel.getProspectorTableRowCount());

        SwingUtilities.invokeAndWait(() -> {
            panel.updateFromProspector(null);
        });
        assertEquals(0, panel.getProspectorTableRowCount(), "Null event should clear prospector table");
    }
}
