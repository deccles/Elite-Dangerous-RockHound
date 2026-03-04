package org.dce.ed;

import java.awt.BorderLayout;
import java.time.Instant;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

import com.google.gson.JsonObject;

/**
 * Demo for the exobiology map: grid, scale label, and radar sweep.
 * Fires one simulated event every 5 seconds; ship follows a triangle path
 * (center → A → B → center), scans at A and B, then repeats.
 */
public final class ExobiologyMapDemo {

    private static final String DEMO_BODY_NAME = "Demo Planet";
    private static final double PLANET_RADIUS_M = 1_000_000.0;
    private static final int TICK_MS = 5_000;
    private static final int STEPS_PER_CYCLE = 11;

    // Triangle vertices (lat, lon) in degrees
    private static final double[] W0 = { 0.0, 0.0 };
    private static final double[] W1 = { 0.004, 0.0 };
    private static final double[] W2 = { 0.002, 0.00346 };

    // Flags: hasLatLong = 0x00200000 so biology/position are used
    private static final int STATUS_FLAGS = 0x00200000;

    private final JFrame frame;
    private final BiologyTabPanel biologyTab;
    private final SystemState demoState;
    private final DemoSystemTabPanel demoSystemTab;
    private int stepIndex;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ExobiologyMapDemo demo = new ExobiologyMapDemo();
            demo.start();
        });
    }

    public ExobiologyMapDemo() {
        demoState = new SystemState();
        demoState.setSystemName("Demo System");
        demoState.setSystemAddress(1L);

        BodyInfo body = new BodyInfo();
        body.setBodyName(DEMO_BODY_NAME);
        body.setHasBio(true);
        body.setBodyId(1);
        demoState.getBodies().put(1, body);

        demoSystemTab = new DemoSystemTabPanel(demoState);

        biologyTab = new BiologyTabPanel();
        biologyTab.setSystemTabPanel(demoSystemTab);

        frame = new JFrame("Exobiology Map Demo");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        content.add(biologyTab, BorderLayout.CENTER);
        frame.setContentPane(content);
        frame.setSize(600, 500);
        stepIndex = 0;
    }

    private void start() {
        frame.setVisible(true);
        // Initial position at center so map shows ship and body immediately
        SwingUtilities.invokeLater(() ->
                biologyTab.handleLogEvent(buildStatusEvent(W0[0], W0[1], bearingDeg(W0[0], W0[1], W1[0], W1[1]))));
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        Timer t = new Timer(TICK_MS, e -> {
            fireOneEvent();
            scheduleNextTick();
        });
        t.setRepeats(false);
        t.start();
    }

    private void fireOneEvent() {
        int step = stepIndex % STEPS_PER_CYCLE;
        stepIndex++;

        if (step == 4) {
            // Scan at A (W1)
            bodyRecordSampleAndFireScanOrganic(W1[0], W1[1], "Bacterium Alpha");
            return;
        }
        if (step == 7) {
            // Scan at B (W2)
            bodyRecordSampleAndFireScanOrganic(W2[0], W2[1], "Bacterium Beta");
            return;
        }

        double lat;
        double lon;
        double headingDeg;

        if (step <= 3) {
            // W0 → W1
            double t = (step + 1) / 4.0;
            lat = W0[0] + t * (W1[0] - W0[0]);
            lon = W0[1] + t * (W1[1] - W0[1]);
            headingDeg = bearingDeg(lat, lon, W1[0], W1[1]);
        } else if (step == 5 || step == 6) {
            // W1 → W2
            double t = (step == 5) ? 0.5 : 1.0;
            lat = W1[0] + t * (W2[0] - W1[0]);
            lon = W1[1] + t * (W2[1] - W1[1]);
            headingDeg = (step == 5) ? bearingDeg(lat, lon, W2[0], W2[1]) : bearingDeg(lat, lon, W0[0], W0[1]);
        } else {
            // step 8,9,10: W2 → W0
            double t = (step - 7) / 3.0;
            lat = W2[0] + t * (W0[0] - W2[0]);
            lon = W2[1] + t * (W0[1] - W2[1]);
            headingDeg = (step < 10) ? bearingDeg(lat, lon, W0[0], W0[1]) : bearingDeg(lat, lon, W1[0], W1[1]);
        }

        StatusEvent event = buildStatusEvent(lat, lon, headingDeg);
        SwingUtilities.invokeLater(() -> biologyTab.handleLogEvent(event));
    }

    private void bodyRecordSampleAndFireScanOrganic(double lat, double lon, String displayName) {
        BodyInfo body = demoState.getBodies().get(1);
        if (body != null) {
            body.addObservedGenusPrefix(displayName.split(" ")[0]);
            body.addObservedBioDisplayName(displayName);
            body.recordBioSamplePoint(displayName, "Log", lat, lon);
        }
        String[] parts = displayName.split(" ", 2);
        String genus = parts[0];
        String species = parts.length > 1 ? parts[1] : "";
        ScanOrganicEvent event = new ScanOrganicEvent(
                Instant.now(),
                new JsonObject(),
                1L,
                DEMO_BODY_NAME,
                1,
                "Log",
                genus,
                genus,
                species,
                species
        );
        SwingUtilities.invokeLater(() -> biologyTab.handleLogEvent(event));
    }

    private static StatusEvent buildStatusEvent(double lat, double lon, double headingDeg) {
        return new StatusEvent(
                Instant.now(),
                new JsonObject(),
                STATUS_FLAGS,
                0,
                new int[] { 4, 4, 4 },
                0,
                0,
                0.5,
                0.5,
                0,
                "Clean",
                0L,
                lat,
                lon,
                0.0,
                headingDeg,
                DEMO_BODY_NAME,
                PLANET_RADIUS_M,
                null,
                null,
                null,
                null
        );
    }

    private static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        double theta = Math.atan2(y, x);
        double deg = Math.toDegrees(theta);
        return (deg + 360.0) % 360.0;
    }

    /**
     * Mock SystemTabPanel that returns the demo state so BiologyTabPanel can resolve the body and table rows.
     */
    private static final class DemoSystemTabPanel extends SystemTabPanel {
        private final SystemState demoState;

        DemoSystemTabPanel(SystemState demoState) {
            this.demoState = demoState;
        }

        @Override
        public SystemState getState() {
            return demoState;
        }
    }
}
