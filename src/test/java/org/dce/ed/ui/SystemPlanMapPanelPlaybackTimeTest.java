package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SystemPlanMapPanelPlaybackTimeTest {

    @Test
    void formatSimulationElapsedTPlus_days() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        Instant t = base.plusSeconds(Math.round(15.0 * 86400));
        assertEquals("T+15 days", SystemPlanMapPanel.formatSimulationElapsedTPlus(base, t));
    }

    @Test
    void formatSimulationElapsedTPlus_daysAtMonthThreshold() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        Instant t = base.plusSeconds(Math.round(42.0 * 86400));
        assertEquals("T+1.4 months", SystemPlanMapPanel.formatSimulationElapsedTPlus(base, t));
    }

    @Test
    void formatSimulationElapsedTPlus_months() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        Instant t = base.plusSeconds(Math.round(97.0 * 86400));
        assertEquals("T+3.2 months", SystemPlanMapPanel.formatSimulationElapsedTPlus(base, t));
    }

    @Test
    void formatSimulationElapsedTPlus_years() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        Instant t = base.plusSeconds(Math.round(584.0 * 86400));
        assertEquals("T+1.6 years", SystemPlanMapPanel.formatSimulationElapsedTPlus(base, t));
    }

    @Test
    void formatSimulationElapsedTPlus_inactiveEpochs_empty() {
        assertEquals("", SystemPlanMapPanel.formatSimulationElapsedTPlus(null, Instant.now()));
        assertEquals("", SystemPlanMapPanel.formatSimulationElapsedTPlus(Instant.now(), null));
    }
}
