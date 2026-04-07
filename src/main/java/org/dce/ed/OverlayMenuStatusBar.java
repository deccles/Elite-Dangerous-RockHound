package org.dce.ed;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;

import org.dce.ed.ui.EdoUi;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.util.GithubMsiUpdater;

/**
 * Shared menu bar layout (glue, status label, Menu) for {@link OverlayFrame} pass-through mode
 * and {@link DecoratedOverlayDialog}.
 */
public final class OverlayMenuStatusBar {

    public static final Color MENU_POPUP_BG = EdoUi.Internal.DARK_14;
    public static final Color MENU_POPUP_FG = EdoUi.Internal.MENU_FG_LIGHT;

    public static final class Result {
        public final JMenuBar menuBar;
        public final JLabel statusLabel;

        public Result(JMenuBar menuBar, JLabel statusLabel) {
            this.menuBar = menuBar;
            this.statusLabel = statusLabel;
        }
    }

    private OverlayMenuStatusBar() {
    }

    public static Color opaquePlate(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    public static Result build(Window parent, String clientKey) {
        JMenuBar bar = new JMenuBar();
        bar.setOpaque(true);
        bar.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
        bar.setBorder(new EmptyBorder(2, 6, 2, 6));

        JMenu overlayMenu = new JMenu("Menu");
        overlayMenu.setForeground(EdoUi.Internal.MENU_ACCENT);

        JMenuItem prefs = new JMenuItem("Preferences...");
        styleMenuItem(prefs);
        prefs.addActionListener(e -> PreferencesDialog.show(parent, clientKey));
        overlayMenu.add(prefs);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setForeground(EdoUi.Internal.MENU_ACCENT);
        addSortedToolsMenuItems(toolsMenu, parent);
        overlayMenu.add(toolsMenu);

        JPopupMenu popup = overlayMenu.getPopupMenu();
        popup.setOpaque(true);
        popup.setBackground(MENU_POPUP_BG);
        popup.setBorder(new EmptyBorder(4, 4, 4, 4));

        JLabel statusLabel = new JLabel("");
        statusLabel.setOpaque(false);
        statusLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        if (parent != null) {
            statusLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    String txt = statusLabel.getText();
                    if (txt != null && txt.contains("New version")) {
                        GithubMsiUpdater.checkAndUpdate(parent);
                    }
                }
            });
        }

        bar.add(Box.createHorizontalGlue());
        bar.add(statusLabel);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(overlayMenu);
        return new Result(bar, statusLabel);
    }

    public static void refreshAccentColors(JMenuBar menuBar) {
        if (menuBar == null) {
            return;
        }
        menuBar.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
        Color accent = EdoUi.Internal.MENU_ACCENT;
        menuBar.setForeground(accent);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu m = menuBar.getMenu(i);
            if (m != null) {
                m.setForeground(accent);
            }
        }
    }

    /**
     * Re-applies accent colors and popup item styling after theme or overlay background changes.
     */
    public static void refreshMenuBarTheme(JMenuBar menuBar) {
        if (menuBar == null) {
            return;
        }
        menuBar.setOpaque(true);
        refreshAccentColors(menuBar);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu m = menuBar.getMenu(i);
            if (m != null) {
                JPopupMenu pm = m.getPopupMenu();
                if (pm != null) {
                    pm.setOpaque(true);
                    pm.setBackground(MENU_POPUP_BG);
                }
                int itemCount = m.getItemCount();
                for (int j = 0; j < itemCount; j++) {
                    styleMenuTree(m.getItem(j));
                }
            }
        }
    }

    public static void styleMenuItem(JMenuItem item) {
        item.setOpaque(true);
        item.setBackground(MENU_POPUP_BG);
        item.setForeground(MENU_POPUP_FG);
    }

    public static void styleMenuTree(JMenuItem node) {
        if (node == null) {
            return;
        }
        styleMenuItem(node);
        if (node instanceof JMenu) {
            JMenu sub = (JMenu) node;
            JPopupMenu subPopup = sub.getPopupMenu();
            if (subPopup != null) {
                subPopup.setOpaque(true);
                subPopup.setBackground(MENU_POPUP_BG);
            }
            for (int i = 0; i < sub.getItemCount(); i++) {
                styleMenuTree(sub.getItem(i));
            }
        }
    }

    private static void addSortedToolsMenuItems(JMenu toolsMenu, Window parent) {
        JMenuItem updates = new JMenuItem("Check for Updates");
        styleMenuItem(updates);
        updates.addActionListener(e -> OverlayToolsLaunchers.checkForUpdates(parent));
        toolsMenu.add(updates);

        JMenuItem exoDbg = new JMenuItem("Exo Prediction Debugger");
        styleMenuItem(exoDbg);
        exoDbg.addActionListener(e -> OverlayToolsLaunchers.launchExoPredictionDebugger(parent));
        toolsMenu.add(exoDbg);

        JMenuItem journal = new JMenuItem("Journal Monitor");
        styleMenuItem(journal);
        journal.addActionListener(e -> OverlayToolsLaunchers.launchJournalMonitor(parent));
        toolsMenu.add(journal);

        JMenuItem edsm = new JMenuItem("Run EDSM Query Tools");
        styleMenuItem(edsm);
        edsm.addActionListener(e -> OverlayToolsLaunchers.launchEdsmQueryTools(parent));
        toolsMenu.add(edsm);

        JMenuItem sqlite = new JMenuItem("SQLite cache browser…");
        styleMenuItem(sqlite);
        sqlite.addActionListener(e -> OverlayToolsLaunchers.launchSqliteCacheBrowser(parent));
        toolsMenu.add(sqlite);

        JMenuItem console = new JMenuItem("Show console");
        styleMenuItem(console);
        console.addActionListener(e -> OverlayToolsLaunchers.showConsole());
        toolsMenu.add(console);
    }
}
