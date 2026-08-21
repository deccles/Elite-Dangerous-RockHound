package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TitleBarButtonTooltipTest {

    @Test
    void everyTitleBarButtonExplainsItsAction() {
        assertTooltip(new TitleBarPanel.HammerButton());
        assertTooltip(new TitleBarPanel.SettingsButton());
        assertTooltip(new TitleBarPanel.PassThroughToggleButton());
        assertTooltip(new TitleBarPanel.MinimizeAllButton());
        assertTooltip(new TitleBarPanel.MinimizeButton());
        assertTooltip(new TitleBarPanel.CloseButton());
    }

    private static void assertTooltip(javax.swing.JComponent component) {
        assertFalse(component.getToolTipText() == null || component.getToolTipText().isBlank(),
                component.getClass().getSimpleName() + " must explain its action");
    }
}
