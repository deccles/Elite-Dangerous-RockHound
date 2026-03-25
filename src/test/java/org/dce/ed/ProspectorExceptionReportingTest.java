package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ProspectorExceptionReportingTest {

    @Test
    void prospectorUpdateException_surfacesInTitleBarLeftStatus() throws Exception {
        TitleBarPanel titleBar = new TitleBarPanel(null, "Test");

        CountDownLatch reported = new CountDownLatch(1);
        ExceptionReporting.setReporter((t, context) -> {
            // Mimic the real UI behavior: "red alert" in the left status label.
            titleBar.setLeftStatusText("ERROR: " + context);
            reported.countDown();
        });

        try {
            // Force buildProspectorAnnouncement() to reach event.getTimestamp().toString()
            // by ensuring we have at least one "match" row (so it doesn't return null early).
            OverlayPreferences.setProspectorMaterialsCsv("platinum");
            OverlayPreferences.setProspectorMinProportionPercent(1.0);
            OverlayPreferences.setProspectorMinAvgValueCrPerTon(0);

            EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);

            // Force an exception inside MiningTabPanel.updateFromProspector():
            // buildProspectorAnnouncement calls event.getTimestamp().toString().
            ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                    null, // timestamp
                    new JsonObject(),
                    List.of(new MaterialProportion("platinum", 20.0)),
                    null,
                    "High"
            );

            tabs.processJournalEvent(event);

            assertTrue(reported.await(2, TimeUnit.SECONDS), "Expected exception reporter to be invoked");

            // Flush pending EDT updates from TitleBarPanel#setLeftStatusText.
            SwingUtilities.invokeAndWait(() -> {
            });

            Field f = TitleBarPanel.class.getDeclaredField("leftStatusLabel");
            f.setAccessible(true);
            JLabel label = (JLabel) f.get(titleBar);

            assertTrue(label.isVisible(), "ERROR label should be visible");
            assertEquals("ERROR: Prospector update", label.getText());
        } finally {
            ExceptionReporting.setReporter((t, context) -> {});
        }
    }
}

