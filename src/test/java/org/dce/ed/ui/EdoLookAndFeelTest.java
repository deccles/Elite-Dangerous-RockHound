package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

class EdoLookAndFeelTest {

    @Test
    void installPutsDarkChromeDefaultsAndRegistersDelegates() {
        EdoLookAndFeel.install();
        assertTrue(EdoLookAndFeel.isInstalled());
        assertEquals(EdoUi.User.BACKGROUND, UIManager.getColor("Panel.background"));
        assertEquals(EdoUi.User.MAIN_TEXT, UIManager.getColor("Label.foreground"));
        assertEquals(EdoUi.User.BACKGROUND, UIManager.getColor("TitlePane.background"));
        assertEquals(OverlaySliderUI.class.getName(), UIManager.getString("SliderUI"));
        assertEquals(OverlayComboBoxStyle.OverlayComboBoxUI.class.getName(), UIManager.getString("ComboBoxUI"));
        assertEquals(EdoUi.User.PANEL_BG, UIManager.getColor("ToolTip.background"));
    }

    @Test
    void htmlHexMatchesFactoryDefaults() {
        assertEquals("#FF8C00", EdoUi.htmlHex(EdoUi.Defaults.MAIN_TEXT));
        assertEquals("#161616", EdoUi.htmlHex(EdoUi.Internal.DARK_22));
    }

    @Test
    void surfaceDefaultsToDialogAndWalksAncestors() {
        JPanel root = new JPanel();
        JLabel child = new JLabel("x");
        root.add(child);
        assertEquals(EdoSurface.DIALOG, EdoSurface.resolve(child));

        EdoSurface.markOverlay(root);
        assertTrue(EdoSurface.isOverlay(child));
        assertFalse(EdoSurface.isDialog(child));

        EdoSurface.markDocument(root);
        assertTrue(EdoSurface.isDocument(child));
    }

    @Test
    void outlineButtonTagsRoleAndHitSafeProperties() {
        EdoLookAndFeel.install();
        javax.swing.JButton b = new javax.swing.JButton("Go");
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(b, OverlayPreferencesFont());
        assertEquals(EdoLookAndFeel.BUTTON_ROLE_PRIMARY, b.getClientProperty(EdoLookAndFeel.BUTTON_ROLE_KEY));
        assertEquals(Boolean.TRUE, b.getClientProperty(EdoLookAndFeel.HIT_SAFE_KEY));
    }

    private static java.awt.Font OverlayPreferencesFont() {
        return new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12);
    }
}
