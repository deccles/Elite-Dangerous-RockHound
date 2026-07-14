package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProspectorBackendFailureDoesNotBlockUiTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @AfterEach
    void clearStatusSinks() {
        MiningTabPanel.clearMiningSheetsStatusSinksForTests();
    }

    @Test
    void backendFailure_doesNotWipeLastSpreadsheetOrProspectorRows() throws Exception {
        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();

        AtomicInteger loadCalls = new AtomicInteger(0);
        AtomicReference<String> lastError = new AtomicReference<>();
        AtomicInteger clearCount = new AtomicInteger();
        MiningTabPanel.setMiningSheetsStatusSinksForTests(
                lastError::set,
                clearCount::incrementAndGet);
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

        // Wait for the initial spreadsheet refresh (constructor triggers a SwingWorker).
        // Pump the EDT so done() can apply rows — under a full suite the EventQueue can sit idle
        // otherwise and this wait times out even though loadRows already returned.
        long deadlineMs = System.currentTimeMillis() + 5_000;
        int spreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        while (System.currentTimeMillis() < deadlineMs && spreadsheetRows <= 0) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> {
            });
            spreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        }
        assertTrue(spreadsheetRows > 0,
                "Spreadsheet should load last-good rows initially (loadCalls=" + loadCalls.get() + ")");

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

        // Stop the periodic refresh timer — only the two explicit loads matter for this test.
        javax.swing.Timer timer = findSpreadsheetRefreshTimer(panel);
        if (timer != null) {
            timer.stop();
        }

        // Trigger backend failure: second refreshSpreadsheetFromBackend() loadRows call throws.
        panel.refreshSpreadsheetFromBackend();

        deadlineMs = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadlineMs
                && (loadCalls.get() < 2 || lastError.get() == null || lastError.get().isBlank())) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> {
            });
        }
        SwingUtilities.invokeAndWait(() -> {
        });

        // On ERROR, MiningTabPanel keeps showing last-good rows.
        int afterSpreadsheetRows = panel.getProspectorSpreadsheetRowCountForTests();
        assertEquals(spreadsheetRows, afterSpreadsheetRows, "Spreadsheet row count should remain unchanged on backend failure");
        assertEquals(prospectorRows, panel.getProspectorTableRowCount(), "Prospector table should not be wiped by backend errors");

        String err = lastError.get();
        assertTrue(err != null && !err.isBlank(),
                "Expected mining status error text when refresh fails (loadCalls=" + loadCalls.get() + ")");
        assertTrue(err.contains("backend boom") || err.contains("Could not load mining log"),
                "Error text should surface failure: " + err);
        assertTrue(clearCount.get() >= 1, "Successful initial load should clear mining status at least once");
    }

    private static javax.swing.Timer findSpreadsheetRefreshTimer(MiningTabPanel panel) throws Exception {
        java.lang.reflect.Field f = MiningTabPanel.class.getDeclaredField("spreadsheetRefreshTimer");
        f.setAccessible(true);
        Object v = f.get(panel);
        return v instanceof javax.swing.Timer t ? t : null;
    }
}

