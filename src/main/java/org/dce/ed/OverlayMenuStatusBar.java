package org.dce.ed;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.dce.ed.ui.EdoUi;

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
 * Shared menu bar layout (glue + status label, optional hammer/gear/pass-through entry) for {@link OverlayFrame}
 * and {@link DecoratedOverlayDialog}.
 * <p>
 * {@link OverlayFrame} uses a custom {@link TitleBarPanel} for tools/preferences/pass-through, so it
 * passes {@code includeToolbarIcons=false} to avoid duplicating those controls on the status row.
 * {@link DecoratedOverlayDialog} has no custom title strip (only the OS caption), so it passes
 * {@code true} plus a runnable that switches to the pass-through {@link OverlayFrame}.
 */
public final class OverlayMenuStatusBar {

    /**
     * Decorated mode’s status row includes 24px toolbar controls, which inflates {@link JMenuBar} height.
     * Pass-through mode uses the same bar without those controls, so Swing would pick a shorter height;
     * we normalize both to at least this value so the strip matches visually.
     */
    private static final int STATUS_BAR_MIN_HEIGHT_PX = 32;

    public static final Color MENU_POPUP_BG = EdoUi.Internal.DARK_14;
    public static final Color MENU_POPUP_FG = EdoUi.Internal.MENU_FG_LIGHT;

    public static final class Result {
        public final JMenuBar menuBar;
        public final JLabel statusLabel;
        /** Standalone Tools menu (not shown on menu bar); used by hammer button and title bar. */
        public final JMenu toolsMenu;

        public Result(JMenuBar menuBar, JLabel statusLabel, JMenu toolsMenu) {
            this.menuBar = menuBar;
            this.statusLabel = statusLabel;
            this.toolsMenu = toolsMenu;
        }
    }

    private OverlayMenuStatusBar() {
    }

    public static Color opaquePlate(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    /**
     * @param onRequestPassThrough when non-null and {@code includeToolbarIcons} is true, a
     *                             pass-through entry button is shown (decorated window only).
     */
    public static Result build(
            Window parent,
            String clientKey,
            boolean includeToolbarIcons,
            Runnable onRequestPassThrough) {
        JMenuBar bar = new JMenuBar();
        bar.setOpaque(true);
        bar.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
        bar.setBorder(new EmptyBorder(2, 6, 2, 6));

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setForeground(EdoUi.Internal.MENU_ACCENT);
        addSortedToolsMenuItems(toolsMenu, parent);
        JPopupMenu toolsPopup = toolsMenu.getPopupMenu();
        toolsPopup.setOpaque(true);
        toolsPopup.setBackground(MENU_POPUP_BG);
        toolsPopup.setBorder(new EmptyBorder(4, 4, 4, 4));
        styleMenuTree(toolsMenu);

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
        bar.add(Box.createHorizontalStrut(includeToolbarIcons ? 10 : 4));
        if (includeToolbarIcons) {
            bar.add(createDecoratedToolbar(parent, clientKey, toolsMenu, onRequestPassThrough));
        }
        applyStatusBarRowHeight(bar);
        return new Result(bar, statusLabel, toolsMenu);
    }

    private static void applyStatusBarRowHeight(JMenuBar bar) {
        Dimension pref = bar.getPreferredSize();
        int h = Math.max(pref.height, STATUS_BAR_MIN_HEIGHT_PX);
        bar.setPreferredSize(new Dimension(pref.width, h));
        bar.setMinimumSize(new Dimension(0, h));
    }

    private static javax.swing.JPanel createDecoratedToolbar(
            Window parent,
            String clientKey,
            JMenu toolsMenu,
            Runnable onRequestPassThrough) {
        javax.swing.JPanel strip = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 2));
        strip.setOpaque(false);

        TitleBarPanel.HammerButton hammer = new TitleBarPanel.HammerButton();
        hammer.setToolTipText(TitleBarPanel.TOOLTIP_HAMMER);
        hammer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    showToolsPopup(hammer, toolsMenu);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hammer.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hammer.setHover(false);
            }
        });

        TitleBarPanel.SettingsButton gear = new TitleBarPanel.SettingsButton();
        gear.setToolTipText(TitleBarPanel.TOOLTIP_GEAR);
        gear.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    PreferencesDialog.show(parent, clientKey);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                gear.setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                gear.setHover(false);
            }
        });

        strip.add(hammer);
        strip.add(gear);

        if (onRequestPassThrough != null) {
            TitleBarPanel.PassThroughToggleButton pt = new TitleBarPanel.PassThroughToggleButton();
            pt.setPassThroughActive(false);
            pt.setPassThroughToolTipOverride(TitleBarPanel.TOOLTIP_PASS_THROUGH_SWITCH_TO_PT);
            pt.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        onRequestPassThrough.run();
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    pt.setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    pt.setHover(false);
                }
            });
            strip.add(pt);
        }

        return strip;
    }

    public static void showToolsPopup(java.awt.Component invoker, JMenu toolsMenu) {
        if (invoker == null || toolsMenu == null) {
            return;
        }
        toolsMenu.getPopupMenu().show(invoker, 0, invoker.getHeight());
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
