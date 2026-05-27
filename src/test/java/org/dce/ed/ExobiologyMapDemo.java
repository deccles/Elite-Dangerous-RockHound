package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
 * Visual demo for the biology-tab radar map.
 * <p>
 * Run from the project root:
 * {@code mvn -q test-compile exec:java -Dexec.mainClass=org.dce.ed.ExobiologyMapDemo -Dexec.classpathScope=test}
 * <p>
 * Or run {@link #main} from the IDE. Simulated timeline (5 s per tick):
 * <ol>
 *   <li>Ship parks at the landing site and drives a short triangle (sample scans at two corners).</li>
 *   <li>Ship returns and parks at the landing site.</li>
 *   <li><b>Disembark</b> — Status switches from {@code inMainShip} to {@code onFoot} at the same fix.</li>
 *   <li>Commander walks away; white dot stays centered, red ship glyph moves to the parked fix.</li>
 *   <li>Commander reboards; ship anchor updates again.</li>
 * </ol>
 */
public final class ExobiologyMapDemo {

    private static final String DEMO_BODY_NAME = "Demo Planet";
    private static final double PLANET_RADIUS_M = 1_000_000.0;
    private static final int TICK_MS = 5_000;

    /** Park / land site (ship parks here before disembark). */
    private static final double[] LAND = { 0.0, 0.0 };
    /** Triangle leg A — sample + walk target. */
    private static final double[] SITE_A = { 0.004, 0.0 };
    /** Triangle leg B — second sample. */
    private static final double[] SITE_B = { 0.002, 0.00346 };

    /** hasLatLong | inMainShip — updates parked-ship map anchor. */
    private static final int STATUS_FLAGS_SHIP = 0x00200000 | 0x01000000;
    /** hasLatLong | onFoot | onFootOnPlanet — commander position; ship stays parked. */
    private static final int STATUS_FLAGS2_ON_FOOT = 0x00000001 | 0x00000010;

    private static final int STEP_PARK = 0;
    private static final int STEP_DRIVE_A_END = 2;
    private static final int STEP_SCAN_A = 3;
    private static final int STEP_AT_B = 5;
    private static final int STEP_SCAN_B = 6;
    private static final int STEP_RETURN_END = 8;
    private static final int STEP_DISEMBARK = 9;
    private static final int STEP_WALK_END = 15;
    private static final int STEP_REEMBARK = 16;
    private static final int STEPS_PER_CYCLE = 17;

    private final JFrame frame;
    private final BiologyTabPanel biologyTab;
    private final SystemState demoState;
    private final DemoSystemTabPanel demoSystemTab;
    private final boolean prevOverlayTransparent;
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

        prevOverlayTransparent = OverlayPreferences.isOverlayTransparent();
        OverlayPreferences.setOverlayTransparent(false);

        biologyTab = new BiologyTabPanel();
        biologyTab.setSystemTabPanel(demoSystemTab);

        frame = new JFrame("Exobiology Map Demo — In ship");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                OverlayPreferences.setOverlayTransparent(prevOverlayTransparent);
            }
        });
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(Color.BLACK);
        content.add(biologyTab, BorderLayout.CENTER);
        biologyTab.setOpaque(true);
        biologyTab.setBackground(Color.BLACK);
        frame.setContentPane(content);
        frame.getContentPane().setBackground(Color.BLACK);
        frame.setSize(600, 500);
        stepIndex = 0;
    }

    private void start() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(() ->
                biologyTab.handleLogEvent(buildShipStatusEvent(LAND[0], LAND[1], bearingDeg(LAND, SITE_A))));
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

        if (step == STEP_SCAN_A) {
            bodyRecordSampleAndFireScanOrganic(SITE_A[0], SITE_A[1], "Bacterium Alpha");
            setPhaseTitle("In ship — scan at site A");
            return;
        }
        if (step == STEP_SCAN_B) {
            bodyRecordSampleAndFireScanOrganic(SITE_B[0], SITE_B[1], "Bacterium Beta");
            setPhaseTitle("In ship — scan at site B");
            return;
        }

        if (step == STEP_DISEMBARK) {
            setPhaseTitle("Disembarked — ship parked at landing site");
            // Same fix as parked ship; onFoot flag stops updating ship anchor.
            fireStatus(buildOnFootStatusEvent(LAND[0], LAND[1], bearingDeg(LAND, SITE_A)));
            return;
        }

        if (step > STEP_DISEMBARK && step <= STEP_WALK_END) {
            int walkStep = step - STEP_DISEMBARK;
            int walkTotal = STEP_WALK_END - STEP_DISEMBARK;
            double t = walkStep / (double) walkTotal;
            double lat = LAND[0] + t * (SITE_A[0] - LAND[0]);
            double lon = LAND[1] + t * (SITE_A[1] - LAND[1]);
            setPhaseTitle(String.format(
                    "On foot — walking to site A (%d/%d)", walkStep, walkTotal));
            fireStatus(buildOnFootStatusEvent(lat, lon, bearingDeg(lat, lon, SITE_A[0], SITE_A[1])));
            return;
        }

        if (step == STEP_REEMBARK) {
            setPhaseTitle("Reboarded — ship at landing site");
            fireStatus(buildShipStatusEvent(LAND[0], LAND[1], bearingDeg(LAND, SITE_A)));
            return;
        }

        double lat;
        double lon;
        double headingDeg;
        String phaseTitle;

        if (step == STEP_PARK) {
            lat = LAND[0];
            lon = LAND[1];
            headingDeg = bearingDeg(LAND, SITE_A);
            phaseTitle = "In ship — parked at landing site";
        } else if (step <= STEP_DRIVE_A_END) {
            double t = (step + 1) / (double) (STEP_DRIVE_A_END + 1);
            lat = LAND[0] + t * (SITE_A[0] - LAND[0]);
            lon = LAND[1] + t * (SITE_A[1] - LAND[1]);
            headingDeg = bearingDeg(lat, lon, SITE_A[0], SITE_A[1]);
            phaseTitle = "In ship — driving to site A";
        } else if (step == STEP_AT_B - 1) {
            double t = 0.5;
            lat = SITE_A[0] + t * (SITE_B[0] - SITE_A[0]);
            lon = SITE_A[1] + t * (SITE_B[1] - SITE_A[1]);
            headingDeg = bearingDeg(lat, lon, SITE_B[0], SITE_B[1]);
            phaseTitle = "In ship — driving to site B";
        } else if (step == STEP_AT_B) {
            lat = SITE_B[0];
            lon = SITE_B[1];
            headingDeg = bearingDeg(lat, lon, LAND[0], LAND[1]);
            phaseTitle = "In ship — at site B";
        } else {
            // steps 7–8: SITE_B → LAND
            double t = (step - (STEP_SCAN_B + 1)) / (double) (STEP_RETURN_END - STEP_SCAN_B);
            lat = SITE_B[0] + t * (LAND[0] - SITE_B[0]);
            lon = SITE_B[1] + t * (LAND[1] - SITE_B[1]);
            headingDeg = bearingDeg(lat, lon, LAND[0], LAND[1]);
            phaseTitle = "In ship — returning to landing site";
        }

        setPhaseTitle(phaseTitle);
        fireStatus(buildShipStatusEvent(lat, lon, headingDeg));
    }

    private void setPhaseTitle(String phase) {
        SwingUtilities.invokeLater(() -> frame.setTitle("Exobiology Map Demo — " + phase));
    }

    private void fireStatus(StatusEvent event) {
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

    private static StatusEvent buildShipStatusEvent(double lat, double lon, double headingDeg) {
        return buildStatusEvent(lat, lon, headingDeg, STATUS_FLAGS_SHIP, 0);
    }

    private static StatusEvent buildOnFootStatusEvent(double lat, double lon, double headingDeg) {
        return buildStatusEvent(lat, lon, headingDeg, 0x00200000, STATUS_FLAGS2_ON_FOOT);
    }

    private static StatusEvent buildStatusEvent(double lat, double lon, double headingDeg, int flags, int flags2) {
        return new StatusEvent(
                Instant.now(),
                new JsonObject(),
                flags,
                flags2,
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
                Math.toRadians(headingDeg),
                DEMO_BODY_NAME,
                PLANET_RADIUS_M,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static double bearingDeg(double[] from, double[] to) {
        return bearingDeg(from[0], from[1], to[0], to[1]);
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
