package org.dce.ed.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteStatusPresentationTest {

    @Test
    void pendingStatusUsesEmptyCircleAndRemainsQueryable() {
        RouteStatusPresentation presentation = RouteStatusPresentation.forStatus(RouteScanStatus.PENDING);

        assertEquals("", presentation.symbol());
        assertFalse(presentation.filled());
        assertTrue(RouteScanStatus.PENDING.needsEdsmQuery());
    }

    @Test
    void resolvedUnknownUsesQuestionMarkAndDoesNotQueryAgain() {
        RouteStatusPresentation presentation = RouteStatusPresentation.forStatus(RouteScanStatus.UNKNOWN);

        assertEquals("?", presentation.symbol());
        assertTrue(presentation.filled());
        assertFalse(RouteScanStatus.UNKNOWN.needsEdsmQuery());
    }

    @Test
    void deferredStatusUsesEmptyCircleAndDoesNotQuery() {
        RouteStatusPresentation presentation = RouteStatusPresentation.forStatus(RouteScanStatus.DEFERRED);

        assertEquals("", presentation.symbol());
        assertFalse(presentation.filled());
        assertFalse(RouteScanStatus.DEFERRED.needsEdsmQuery());
        assertTrue(RouteScanStatus.DEFERRED.isUnresolved());
        assertTrue(RouteScanStatus.PENDING.isUnresolved());
        assertFalse(RouteScanStatus.UNKNOWN.isUnresolved());
    }

    @Test
    void resolvedGlowRampsInThenFadesWithinHalfASecond() {
        assertEquals(0.0f, RouteStatusPresentation.glowAlpha(0L));
        assertEquals(0.5f, RouteStatusPresentation.glowAlpha(50L), 0.001f);
        assertEquals(1.0f, RouteStatusPresentation.glowAlpha(100L), 0.001f);
        assertEquals(0.5f, RouteStatusPresentation.glowAlpha(300L), 0.001f);
        assertEquals(0.0f, RouteStatusPresentation.glowAlpha(500L));
        assertEquals(0.0f, RouteStatusPresentation.glowAlpha(700L));
    }
}
