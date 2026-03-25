package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.swing.JPanel;

public class OverlayContentPanel extends JPanel {

    private final BooleanSupplier passThroughEnabledSupplier;
    private EliteOverlayTabbedPane tabbedPane;

    public OverlayContentPanel(BooleanSupplier passThroughEnabledSupplier) {
        this.passThroughEnabledSupplier = Objects.requireNonNull(passThroughEnabledSupplier, "passThroughEnabledSupplier");

        setOpaque(false);
        setLayout(new BorderLayout());

        tabbedPane = new EliteOverlayTabbedPane(() -> this.passThroughEnabledSupplier.getAsBoolean());
        add(tabbedPane, BorderLayout.CENTER);
    }
    public void rebuildTabbedPane() {
        EliteOverlayTabbedPane old = tabbedPane;

        EliteOverlayTabbedPane next = new EliteOverlayTabbedPane(() -> this.passThroughEnabledSupplier.getAsBoolean());
        tabbedPane = next;

        if (old != null) {
            remove(old);
        }
        add(next, BorderLayout.CENTER);

        // Reapply current overlay background + font prefs to the new pane
        java.awt.Color bg = getBackground();
        boolean treatAsTransparent = (bg != null && bg.getAlpha() < 255);
        next.applyOverlayBackground(bg, treatAsTransparent);
        next.applyUiFontPreferences();

        revalidate();
        repaint();
    }



    public EliteOverlayTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public void applyOverlayTransparency(boolean transparent) {
        // Legacy path: treat "transparent" as fully transparent.
        applyOverlayBackground(new java.awt.Color(0, 0, 0, transparent ? 0 : 255), transparent);
    }

    public void applyOverlayBackground(java.awt.Color bgWithAlpha, boolean treatAsTransparent) {
        setOpaque(false);
        setBackground(bgWithAlpha);

        tabbedPane.applyOverlayBackground(bgWithAlpha, treatAsTransparent);

        revalidate();
        repaint();
    }

    public void applyUiFontPreferences() {
        tabbedPane.applyUiFontPreferences();
        revalidate();
        repaint();
    }

    public void applyUiFont(java.awt.Font font) {
        tabbedPane.applyUiFont(font);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 1000);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}
