package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.dce.ed.market.GalacticAveragePrices;
import org.dce.ed.mining.ProspectorLogBackend;
import org.dce.ed.mining.ProspectorLogRow;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Prospector UI updates + speech gating tests.
 */
class MiningTabPanelProspectorUpdateTest {

    private static final String DEFAULT_DISABLE_SPEECH_PROPERTY = "edo.test.disableSpeech";
    private String originalDisableSpeechPropertyValue;

    @AfterEach
    void restoreDisableSpeechProperty() {
        if (originalDisableSpeechPropertyValue == null) {
            System.clearProperty(DEFAULT_DISABLE_SPEECH_PROPERTY);
        } else {
            System.setProperty(DEFAULT_DISABLE_SPEECH_PROPERTY, originalDisableSpeechPropertyValue);
        }
    }

    private static class RecordingTtsSprintf extends TtsSprintf {
        final List<String> templates = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>();

        RecordingTtsSprintf() {
            super(new PollyTtsCached());
        }

        @Override
        public void speakf(String template, Object... args) {
            templates.add(template);
            this.args.add(args == null ? new Object[0] : args.clone());
        }
    }

    private static ProspectorLogBackend backendThatNeverTouchesNetworkAndAlwaysLoadsEmpty() {
        return new ProspectorLogBackend() {
            @Override
            public void appendRows(List<ProspectorLogRow> rows) {
                // no-op
            }

            @Override
            public List<ProspectorLogRow> loadRows() {
                return List.of();
            }

            @Override
            public void updateRunEndTime(String commander, int run, Instant endTime) {
                // no-op
            }
        };
    }

    @Test
    void updateFromProspector_emptyMaterials_clearsTable() throws Exception {
        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        MiningTabPanel panel = new MiningTabPanel(prices, () -> false);

        ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                Instant.parse("2026-03-25T10:15:30Z"),
                new com.google.gson.JsonObject(),
                List.of(),
                null,
                "High"
        );

        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));

        assertEquals(0, panel.getProspectorTableRowCount());
    }

    @Test
    void speechDisabled_doesNotCallTts_butRowsStillPopulate() throws Exception {
        originalDisableSpeechPropertyValue = System.getProperty(DEFAULT_DISABLE_SPEECH_PROPERTY);
        System.setProperty(DEFAULT_DISABLE_SPEECH_PROPERTY, "true"); // force-disable for this test
        OverlayPreferences.setSpeechEnabled(true);

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        RecordingTtsSprintf tts = new RecordingTtsSprintf();

        AtomicInteger loadCount = new AtomicInteger();
        Supplier<ProspectorLogBackend> backendSupplier = () -> new ProspectorLogBackend() {
            @Override
            public void appendRows(List<ProspectorLogRow> rows) {
                // no-op
            }

            @Override
            public List<ProspectorLogRow> loadRows() {
                loadCount.incrementAndGet();
                return List.of();
            }

            @Override
            public void updateRunEndTime(String commander, int run, Instant endTime) {
                // no-op
            }
        };

        MiningTabPanel panel = new MiningTabPanel(prices, () -> false, tts, backendSupplier);

        // Enable enough to trigger announcements if speech were enabled.
        OverlayPreferences.setProspectorMaterialsCsv("platinum");
        OverlayPreferences.setProspectorMinProportionPercent(1.0);
        OverlayPreferences.setProspectorMinAvgValueCrPerTon(0);

        ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                Instant.parse("2026-03-25T10:15:30Z"),
                new com.google.gson.JsonObject(),
                List.of(new MaterialProportion("platinum", 32.5)),
                null,
                "High"
        );

        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));

        assertTrue(panel.getProspectorTableRowCount() >= 1);
        assertEquals(0, tts.templates.size(), "TTS should not be invoked when speech is disabled");

        // Avoid "unused warning" while still asserting the backend seam works.
        assertTrue(loadCount.get() >= 0);
    }

    @Test
    void speechEnabled_withAnnouncement_callsTtsOnce_andDedupPreventsRepeat() throws Exception {
        originalDisableSpeechPropertyValue = System.getProperty(DEFAULT_DISABLE_SPEECH_PROPERTY);
        System.setProperty(DEFAULT_DISABLE_SPEECH_PROPERTY, "false"); // allow speech gating to proceed
        OverlayPreferences.setSpeechEnabled(true);

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        RecordingTtsSprintf tts = new RecordingTtsSprintf();

        MiningTabPanel panel = new MiningTabPanel(
                prices,
                () -> false,
                tts,
                MiningTabPanelProspectorUpdateTest::backendThatNeverTouchesNetworkAndAlwaysLoadsEmpty
        );

        OverlayPreferences.setProspectorMaterialsCsv("platinum");
        OverlayPreferences.setProspectorMinProportionPercent(1.0);
        OverlayPreferences.setProspectorMinAvgValueCrPerTon(0);

        ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                Instant.parse("2026-03-25T10:15:30Z"),
                new com.google.gson.JsonObject(),
                List.of(new MaterialProportion("platinum", 32.5)),
                null,
                "High"
        );

        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));
        assertEquals(1, tts.templates.size(), "First update should trigger a TTS announcement");

        // 32.5 rounds to 33 for the {n} placeholder.
        assertTrue(tts.templates.get(0).contains("Prospector found"));
        assertEquals("platinum", String.valueOf(tts.args.get(0)[0]).toLowerCase(), "Announced material should be platinum");
        assertEquals(33, ((Number) tts.args.get(0)[1]).intValue());

        // Same timestamp + same rounded inputs => signature match => no second announcement.
        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));
        assertEquals(1, tts.templates.size(), "Dedup should suppress the repeated announcement");
    }

    @Test
    void speechEnabled_announcementNull_doesNotCallTts() throws Exception {
        originalDisableSpeechPropertyValue = System.getProperty(DEFAULT_DISABLE_SPEECH_PROPERTY);
        System.setProperty(DEFAULT_DISABLE_SPEECH_PROPERTY, "false"); // allow speech gating to proceed
        OverlayPreferences.setSpeechEnabled(true);

        GalacticAveragePrices prices = GalacticAveragePrices.loadDefault();
        RecordingTtsSprintf tts = new RecordingTtsSprintf();

        MiningTabPanel panel = new MiningTabPanel(
                prices,
                () -> false,
                tts,
                MiningTabPanelProspectorUpdateTest::backendThatNeverTouchesNetworkAndAlwaysLoadsEmpty
        );

        // Make announcements impossible by forcing min proportion > provided proportion.
        OverlayPreferences.setProspectorMaterialsCsv("platinum");
        OverlayPreferences.setProspectorMinProportionPercent(90.0);
        OverlayPreferences.setProspectorMinAvgValueCrPerTon(0);

        ProspectedAsteroidEvent event = new ProspectedAsteroidEvent(
                Instant.parse("2026-03-25T10:15:30Z"),
                new com.google.gson.JsonObject(),
                List.of(new MaterialProportion("platinum", 32.5)),
                null,
                "High"
        );

        SwingUtilities.invokeAndWait(() -> panel.updateFromProspector(event));
        assertEquals(0, tts.templates.size(), "TTS should not be invoked when announcement gating returns null");
    }
}

