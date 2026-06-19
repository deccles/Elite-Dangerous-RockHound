package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusWarningSpanTest {

    @Test
    void loneFighterPilotWarningHasNoLeadingSeparator() {
        String span = OverlayFrame.buildStatusWarningSpan(true, false, true);
        assertTrue(span.contains(NpcCrewTracker.FIGHTER_PILOT_STATUS_WARNING));
        assertFalse(span.contains("  |  "), span);
    }

    @Test
    void loneLimpetWarningHasNoLeadingSeparator() {
        String span = OverlayFrame.buildStatusWarningSpan(true, true, false);
        assertTrue(span.contains("Low Limpet Warning!"));
        assertFalse(span.contains("  |  "), span);
    }

    @Test
    void warningAfterRightContentUsesSeparator() {
        String span = OverlayFrame.buildStatusWarningSpan(false, false, true);
        assertTrue(span.startsWith("<span"), span);
        assertTrue(span.contains("  |  " + NpcCrewTracker.FIGHTER_PILOT_STATUS_WARNING), span);
    }

    @Test
    void decoratedStatusWithEmptyRightHtmlOmitsLeadingSeparator() {
        String html = OverlayFrame.buildDecoratedMenuStatusHtml(
                "<html><span style='color:rgb(0,0,0);'></span></html>",
                false,
                true);
        assertTrue(html.contains(NpcCrewTracker.FIGHTER_PILOT_STATUS_WARNING), html);
        assertFalse(html.contains("  |  "), html);
    }
}
