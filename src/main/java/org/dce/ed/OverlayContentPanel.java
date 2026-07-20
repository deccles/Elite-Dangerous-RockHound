package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Window;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.dce.ed.ui.tabdock.TabDockingController;

public class OverlayContentPanel extends JPanel {

    private final BooleanSupplier passThroughEnabledSupplier;
    private Runnable onTabbedPaneRebuilt;
    private EliteOverlayTabbedPane tabbedPane;
    private TabDockingController tabDockingController;
    private Supplier<Window> mainWindowSupplier = () -> SwingUtilities.getWindowAncestor(this);

    public OverlayContentPanel(BooleanSupplier passThroughEnabledSupplier) {
        this.passThroughEnabledSupplier = Objects.requireNonNull(passThroughEnabledSupplier, "passThroughEnabledSupplier");

        setOpaque(false);
        setLayout(new BorderLayout());

        tabbedPane = new EliteOverlayTabbedPane(() -> this.passThroughEnabledSupplier.getAsBoolean());
        add(tabbedPane, BorderLayout.CENTER);
        installTabDocking(tabbedPane);
    }

    public void setMainWindowSupplier(Supplier<Window> mainWindowSupplier) {
        this.mainWindowSupplier = Objects.requireNonNull(mainWindowSupplier, "mainWindowSupplier");
    }

    private void installTabDocking(EliteOverlayTabbedPane pane) {
        if (tabDockingController != null) {
            tabDockingController.disposeAll();
        }
        tabDockingController = new TabDockingController(pane, mainWindowSupplier);
        // Restore after the host window is showing so float bounds land on-screen.
        SwingUtilities.invokeLater(() -> {
            if (tabDockingController != null && pane == tabbedPane) {
                tabDockingController.restoreSavedLayout();
            }
        });
    }

    public void rebuildTabbedPane() {
        EliteOverlayTabbedPane old = tabbedPane;
        if (tabDockingController != null) {
            tabDockingController.disposeAll();
            tabDockingController = null;
        }

        EliteOverlayTabbedPane next = new EliteOverlayTabbedPane(() -> this.passThroughEnabledSupplier.getAsBoolean());
        tabbedPane = next;

        if (old != null) {
            remove(old);
        }
        add(next, BorderLayout.CENTER);
        installTabDocking(next);

        // Reapply current overlay background + font prefs to the new pane
        java.awt.Color bg = getBackground();
        boolean treatAsTransparent = (bg != null && bg.getAlpha() < 255);
        next.applyOverlayBackground(bg, treatAsTransparent);
        next.applyUiFontPreferences();

        revalidate();
        repaint();

        Runnable rebuilt = onTabbedPaneRebuilt;
        if (rebuilt != null) {
            rebuilt.run();
        }
    }

    public EliteOverlayTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public TabDockingController getTabDockingController() {
        return tabDockingController;
    }

    public void disposeTabDocking() {
        if (tabDockingController != null) {
            tabDockingController.disposeAll();
            tabDockingController = null;
        }
    }

    public void setOnTabbedPaneRebuilt(Runnable onTabbedPaneRebuilt) {
        this.onTabbedPaneRebuilt = onTabbedPaneRebuilt;
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
