package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EngineeringTradeVisibilityLockTest {

    @Test
    void keepsEngineeringSelectedUntilTradeEnds() {
        EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);

        tabs.setEngineeringTradeVisibilityLocked(true);
        assertEquals("ENGINEERING", tabs.getVisibleCardName());

        tabs.getTabButton("ROUTE").doClick();
        assertEquals("ENGINEERING", tabs.getVisibleCardName());

        tabs.setEngineeringTradeVisibilityLocked(false);
        tabs.getTabButton("ROUTE").doClick();
        assertEquals("ROUTE", tabs.getVisibleCardName());
    }
}
