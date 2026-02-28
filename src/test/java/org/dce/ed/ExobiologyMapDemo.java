package org.dce.ed;

import java.awt.BorderLayout;
import java.time.Instant;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

import com.google.gson.JsonObject;

/**
 * Demo that simulates a player on the exobiology map: initial position, first scan,
 * movement toward a second point, second scan that triggers a scale-tier change.
 * Run to see grid, scale label, radar sweep, and scale-change zoom animation.
 */
public final class ExobiologyMapDemo {

    private static final String BODY_NAME = "Demo Planet";
    private static final double PLANET_RADIUS_M = 600_000.0; // 600 km
    private static final double LAT0 = 45.0;
    private static final double LON0 = 10.0;
    // Second point ~4 km away (north) to cross from 500m to 2km scale tier
    private static final double LAT1 = LAT0 + 0.382;
    private static final double LON1 = LON0;
    private static final String BIO_DISPLAY_NAME = "Stratum Paleas";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SystemState state = new SystemState();
            state.setSystemName("Demo System");
            BodyInfo body = new BodyInfo();
            body.setBodyId(1);
            body.setBodyName(BODY_NAME);
            body.setRadius(PLANET_RADIUS_M);
            body.setHasBio(true);
            body.addObservedBioDisplayName(BIO_DISPLAY_NAME);
            state.getBodies().put(Integer.valueOf(1), body);

            DemoSystemTabPanel systemTab = new DemoSystemTabPanel(state);
            BiologyTabPanel biologyTab = new BiologyTabPanel();
            biologyTab.setSystemTabPanel(systemTab);

            JFrame frame = new JFrame("Exobiology Map Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(biologyTab, BorderLayout.CENTER);
            frame.setSize(600, 500);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            runTimeline(biologyTab, state, body);
        });
    }

    private static final long MS_BETWEEN_EVENTS = 5000;
    private static final long TIMER_INTERVAL_MS = 500;

    private static void runTimeline(BiologyTabPanel biologyTab, SystemState state, BodyInfo body) {
        Instant start = Instant.now();
        long[] lastStep = { -1L };
        Timer timer = new Timer((int) TIMER_INTERVAL_MS, e -> {
            long elapsedMs = java.time.Duration.between(start, Instant.now()).toMillis();
            long step = elapsedMs / MS_BETWEEN_EVENTS;

            if (step == 0) {
                fireStatus(biologyTab, LAT0, LON0, 0.0);
            } else if (step == 1) {
                if (lastStep[0] < 1) {
                    lastStep[0] = 1;
                    body.recordBioSamplePoint(BIO_DISPLAY_NAME, "Log", LAT0, LON0);
                    fireScanOrganic(biologyTab);
                }
                fireStatus(biologyTab, LAT0, LON0, 0.0);
            } else if (step >= 2 && step < 4) {
                long tStep = elapsedMs - 2 * MS_BETWEEN_EVENTS;
                long tMax = 2 * MS_BETWEEN_EVENTS;
                double t = Math.min(1.0, Math.max(0.0, (double) tStep / tMax));
                double lat = LAT0 + t * (LAT1 - LAT0);
                double lon = LON0 + t * (LON1 - LON0);
                double heading = t * 30.0;
                fireStatus(biologyTab, lat, lon, heading);
            } else if (step == 4) {
                if (lastStep[0] < 4) {
                    lastStep[0] = 4;
                    body.recordBioSamplePoint(BIO_DISPLAY_NAME, "Log", LAT1, LON1);
                    fireScanOrganic(biologyTab);
                }
                fireStatus(biologyTab, LAT1, LON1, 0.0);
            } else if (step >= 5) {
                fireStatus(biologyTab, LAT1, LON1, 0.0);
            }
        });
        timer.start();
    }

    private static void fireStatus(BiologyTabPanel biologyTab, double lat, double lon, double headingDeg) {
        JsonObject raw = new JsonObject();
        StatusEvent e = new StatusEvent(
            Instant.now(),
            raw,
            0, 0, new int[] { 2, 2, 2 },
            0, 0,
            0.5, 0.5, 0,
            "Clean", 0L,
            Double.valueOf(lat), Double.valueOf(lon), Double.valueOf(0.0), Double.valueOf(headingDeg),
            BODY_NAME, Double.valueOf(PLANET_RADIUS_M),
            null, null, null, null
        );
        biologyTab.handleLogEvent(e);
    }

    private static void fireScanOrganic(BiologyTabPanel biologyTab) {
        JsonObject raw = new JsonObject();
        ScanOrganicEvent e = new ScanOrganicEvent(
            Instant.now(),
            raw,
            0L, BODY_NAME, 1,
            "Log",
            "Stratum", "Stratum", "Paleas", "Paleas"
        );
        biologyTab.handleLogEvent(e);
    }

    private static final class DemoSystemTabPanel extends SystemTabPanel {
        private final SystemState demoState;

        DemoSystemTabPanel(SystemState state) {
            super();
            this.demoState = state;
        }

        @Override
        public SystemState getState() {
            return demoState;
        }
    }
}
