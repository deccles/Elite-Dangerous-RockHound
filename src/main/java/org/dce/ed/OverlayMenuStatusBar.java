package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.MarqueeStatusScrollPane;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
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

    /** Client property on the fleet badge host: last measured {@link Dimension} when the countdown badge was visible. */
    public static final String CLIENT_KEY_FLEET_BADGE_SLOT_DIM = "edo.fleetBadgeSlotDim";

    public static final Color MENU_POPUP_BG = EdoUi.Internal.DARK_14;
    public static final Color MENU_POPUP_FG = EdoUi.Internal.MENU_FG_LIGHT;

    public static final class Result {
        public final JMenuBar menuBar;
        public final JLabel statusLabel;
        /**
         * Bordered strip at the left of the status row showing only the fleet jump / cooldown token (e.g. {@code T-5:00}).
         * Hidden when neither countdown is active.
         */
        public final JPanel fleetCarrierTimeBadgeHost;
        public final JLabel fleetCarrierTimeLabel;
        /**
         * Static fleet-carrier announcement ({@code FC jump → …} / {@code Cooldown}) to the right of the time badge.
         * Stays fixed while {@link #statusLabel} may marquee-scroll Bio/Geo/Bounties and other messages.
         */
        public final JPanel fleetCarrierAnnouncementHost;
        public final JLabel fleetCarrierAnnouncementLabel;
        /** Standalone Tools menu (not shown on menu bar); used by hammer button and title bar. */
        public final JMenu toolsMenu;

        public Result(
                JMenuBar menuBar,
                JLabel statusLabel,
                JPanel fleetCarrierTimeBadgeHost,
                JLabel fleetCarrierTimeLabel,
                JPanel fleetCarrierAnnouncementHost,
                JLabel fleetCarrierAnnouncementLabel,
                JMenu toolsMenu) {
            this.menuBar = menuBar;
            this.statusLabel = statusLabel;
            this.fleetCarrierTimeBadgeHost = fleetCarrierTimeBadgeHost;
            this.fleetCarrierTimeLabel = fleetCarrierTimeLabel;
            this.fleetCarrierAnnouncementHost = fleetCarrierAnnouncementHost;
            this.fleetCarrierAnnouncementLabel = fleetCarrierAnnouncementLabel;
            this.toolsMenu = toolsMenu;
        }
    }

    private OverlayMenuStatusBar() {
    }

    public static Color opaquePlate(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    /**
     * Bold font for the status row (main status label + fleet time badge): uses {@link OverlayPreferences}
     * name/size, including font preview while the preferences dialog is open.
     */
    public static Font statusRowFontFromPreferences() {
        Font base = OverlayPreferences.getUiFont();
        if (base == null) {
            return new JLabel().getFont().deriveFont(Font.BOLD);
        }
        return base.deriveFont(Font.BOLD);
    }

    /**
     * Inner padding for the fleet-carrier time badge. Slightly more top than bottom offsets cap-height
     * glyphs (e.g. {@code T-15:14}) so they read visually centered in the bordered box.
     */
    public static EmptyBorder fleetTimeBadgeInnerPadding() {
        return new EmptyBorder(5, 4, 4, 4);
    }

    /**
     * Minimum slot size when no live measurement exists yet (matches status font; wide sample for {@code T-h:mm:ss}).
     */
    public static Dimension computeFleetBadgeSlotSize(Font font) {
        JLabel probe = new JLabel("T-0:00:00");
        if (font != null) {
            probe.setFont(font);
        }
        Dimension ps = probe.getPreferredSize();
        int w = ps.width + 10;
        int h = ps.height + 11;
        return new Dimension(Math.max(w, 52), Math.max(h, 24));
    }

    public static void clearFleetBadgeSlotCache(JPanel host) {
        if (host != null) {
            host.putClientProperty(CLIENT_KEY_FLEET_BADGE_SLOT_DIM, null);
        }
    }

    public static void cacheFleetBadgeSlotFromPreferred(JPanel host) {
        if (host == null) {
            return;
        }
        Dimension d = host.getPreferredSize();
        if (d != null && d.width > 0 && d.height > 0) {
            host.putClientProperty(CLIENT_KEY_FLEET_BADGE_SLOT_DIM, new Dimension(d.width, d.height));
        }
    }

    /**
     * When no jump/cooldown token is shown: hide the badge so it uses no horizontal space (status text starts
     * at the left of the status region). Menu bar height stays {@link #STATUS_BAR_MIN_HEIGHT_PX} from
     * {@link #applyStatusBarRowHeight} and the marquee strip.
     */
    public static void applyFleetBadgeCollapsedLayout(JPanel host, JLabel label) {
        if (host == null || label == null) {
            return;
        }
        label.setFont(statusRowFontFromPreferences());
        label.setText("");
        host.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        host.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
        host.setPreferredSize(null);
        host.setMinimumSize(null);
        host.setVisible(false);
        host.revalidate();
    }

    /**
     * Hide the static FC announcement so Bio/Geo/Bounties (and other marquee text) start at the left of the
     * scroll region when no jump/cooldown is active.
     */
    public static void applyFleetAnnouncementCollapsedLayout(JPanel host, JLabel label) {
        if (host == null || label == null) {
            return;
        }
        label.setFont(statusRowFontFromPreferences());
        label.setText("");
        host.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        host.setPreferredSize(null);
        host.setMinimumSize(null);
        host.setVisible(false);
        host.revalidate();
    }

    /**
     * {@link JMenuBar} uses horizontal {@link javax.swing.BoxLayout}; default {@link JPanel} maximum width is
     * unbounded, so the badge would absorb extra space. This panel stays exactly as wide as its content.
     */
    private static final class StatusRowFixedPanel extends JPanel {
        StatusRowFixedPanel() {
            super(new BorderLayout(0, 0));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
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
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setVerticalAlignment(SwingConstants.CENTER);
        Font statusRowFont = statusRowFontFromPreferences();
        statusLabel.setFont(statusRowFont);

        MarqueeStatusScrollPane statusScroll = new MarqueeStatusScrollPane(statusLabel);
        statusScroll.setAlignmentY(Component.CENTER_ALIGNMENT);

        StatusRowFixedPanel fleetCarrierTimeBadgeHost = new StatusRowFixedPanel();
        fleetCarrierTimeBadgeHost.setOpaque(true);
        fleetCarrierTimeBadgeHost.setBackground(opaquePlate(EdoUi.User.BACKGROUND));
        JLabel fleetCarrierTimeLabel = new JLabel("");
        fleetCarrierTimeLabel.setOpaque(false);
        fleetCarrierTimeLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        fleetCarrierTimeLabel.setFont(statusRowFont);
        fleetCarrierTimeLabel.setHorizontalAlignment(SwingConstants.LEADING);
        fleetCarrierTimeLabel.setVerticalAlignment(SwingConstants.CENTER);
        fleetCarrierTimeBadgeHost.add(fleetCarrierTimeLabel, BorderLayout.CENTER);
        applyFleetBadgeCollapsedLayout(fleetCarrierTimeBadgeHost, fleetCarrierTimeLabel);

        StatusRowFixedPanel fleetCarrierAnnouncementHost = new StatusRowFixedPanel();
        fleetCarrierAnnouncementHost.setOpaque(false);
        JLabel fleetCarrierAnnouncementLabel = new JLabel("");
        fleetCarrierAnnouncementLabel.setOpaque(false);
        fleetCarrierAnnouncementLabel.setForeground(EdoUi.Internal.MENU_FG_LIGHT);
        fleetCarrierAnnouncementLabel.setFont(statusRowFont);
        fleetCarrierAnnouncementLabel.setHorizontalAlignment(SwingConstants.LEADING);
        fleetCarrierAnnouncementLabel.setVerticalAlignment(SwingConstants.CENTER);
        fleetCarrierAnnouncementHost.add(fleetCarrierAnnouncementLabel, BorderLayout.CENTER);
        applyFleetAnnouncementCollapsedLayout(fleetCarrierAnnouncementHost, fleetCarrierAnnouncementLabel);

        if (parent != null) {
            MouseAdapter statusClick = new MouseAdapter() {
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
            };
            statusLabel.addMouseListener(statusClick);
            statusScroll.addGhostMouseListener(statusClick);
        }

        bar.add(fleetCarrierTimeBadgeHost);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(fleetCarrierAnnouncementHost);
        // Scroll region fills space between FC announcement and toolbar; scrolls when HTML status is wider than the slot.
        bar.add(statusScroll);
        bar.add(Box.createHorizontalStrut(includeToolbarIcons ? 10 : 4));
        if (includeToolbarIcons) {
            bar.add(createDecoratedToolbar(parent, clientKey, toolsMenu, onRequestPassThrough));
        }
        applyStatusBarRowHeight(bar);
        return new Result(
                bar,
                statusLabel,
                fleetCarrierTimeBadgeHost,
                fleetCarrierTimeLabel,
                fleetCarrierAnnouncementHost,
                fleetCarrierAnnouncementLabel,
                toolsMenu);
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

        JMenuItem rescanJournalFull = new JMenuItem("Rescan journal (full)…");
        styleMenuItem(rescanJournalFull);
        rescanJournalFull.addActionListener(e -> OverlayToolsLaunchers.rescanJournalFull(parent));
        toolsMenu.add(rescanJournalFull);

        JMenuItem rescanCurrentSystem = new JMenuItem("Rescan current system from journal…");
        styleMenuItem(rescanCurrentSystem);
        rescanCurrentSystem.addActionListener(e -> OverlayToolsLaunchers.rescanCurrentSystemFromJournal(parent));
        toolsMenu.add(rescanCurrentSystem);

        JMenuItem edsm = new JMenuItem("Run EDSM Query Tools");
        styleMenuItem(edsm);
        edsm.addActionListener(e -> OverlayToolsLaunchers.launchEdsmQueryTools(parent));
        toolsMenu.add(edsm);

        JMenuItem ardent = new JMenuItem("Ardent market query tool…");
        styleMenuItem(ardent);
        ardent.addActionListener(e -> OverlayToolsLaunchers.launchArdentQueryTool(parent));
        toolsMenu.add(ardent);

        JMenuItem sqlite = new JMenuItem("SQLite cache browser…");
        styleMenuItem(sqlite);
        sqlite.addActionListener(e -> OverlayToolsLaunchers.launchSqliteCacheBrowser(parent));
        toolsMenu.add(sqlite);

        JMenuItem hierarchyGraph = new JMenuItem("System hierarchy graph…");
        styleMenuItem(hierarchyGraph);
        hierarchyGraph.addActionListener(e -> OverlayToolsLaunchers.launchSystemHierarchyGraph(parent));
        toolsMenu.add(hierarchyGraph);

        JMenuItem console = new JMenuItem("Show console");
        styleMenuItem(console);
        console.addActionListener(e -> OverlayToolsLaunchers.showConsole());
        toolsMenu.add(console);
    }
}
