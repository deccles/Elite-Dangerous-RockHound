package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.mining.ProspectorLogBackend;
import org.dce.ed.mining.ProspectorLogRow;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ProspectorBackendFailureDoesNotBlockUiTest {

    @Test
    void backendFailure_doesNotWipeLastSpreadsheetOrProspectorRows() throws Exception {
        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();

        AtomicInteger loadCalls = new AtomicInteger(0);
        List<ProspectorLogRow> goodRows = List.of(new ProspectorLogRow(
                1,
                "Sol > Earth",
                Instant.parse("2026-03-25T10:15:30Z"),
                "platinum",
                20.0,
                1.0,
                2.0,
                1.0,
                "Commander"
        ));

        ProspectorLogBackend backend = new ProspectorLogBackend() {
            @Override
            public void appendRows(List<ProspectorLogRow> rows) {
                // no-op for this test
            }

            @Override
            public List<ProspectorLogRow> loadRows() {
                int call = loadCalls.incrementAndGet();
                if (call == 1) {
                    return goodRows;
                }
                throw new RuntimeException("backend boom");
            }

            @Override
            public void updateRunEndTime(String commander, int run, Instant endTime) {
                // no-op for this test
            }
        };

        Supplier<ProspectorLogBackend> backendSupplier = () -> backend;

        // Speech is globally disabled during tests (see surefire); tts should never be called.
        TtsSprintf tts = new TtsSprintf(new PollyTtsCached());

        MiningTabPanel panel = new MiningTabPanel(prices, () -> false, tts, backendSupplier);

        // Wait for the initial spreadsheet refresh (constructor triggers one).
        long deadlineMs = System.currentTimeMillis() + 2_000;
        int spreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        while (System.currentTimeMillis() < deadlineMs && spreadsheetRows <= 0) {
            Thread.sleep(20);
            spreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        }
        assertTrue(spreadsheetRows > 0, "Spreadsheet should load last-good rows initially");

        // Now populate the prospector table.
        ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                Instant.parse("2026-03-25T10:15:30Z"),
                new JsonObject(),
                List.of(new MaterialProportion("platinum", 20.0)),
                null,
                "High"
        );

        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));
        int prospectorRows = panel.getProspectorTableRowCount();
        assertTrue(prospectorRows > 0, "Prospector table should populate");

        // Trigger backend failure: second refreshSpreadsheetFromBackend() loadRows call throws.
        panel.refreshSpreadsheetFromBackend();

        deadlineMs = System.currentTimeMillis() + 2_000;
        int afterSpreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        while (System.currentTimeMillis() < deadlineMs && loadCalls.get() < 2) {
            Thread.sleep(20);
        }

        // On ERROR, MiningTabPanel keeps showing last-good rows.
        afterSpreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        assertEquals(spreadsheetRows, afterSpreadsheetRows, "Spreadsheet row count should remain unchanged on backend failure");
        assertEquals(prospectorRows, panel.getProspectorTableRowCount(), "Prospector table should not be wiped by backend errors");
    }
}

