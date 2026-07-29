package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.time.Instant;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

import org.dce.ed.binds.EliteBindsLoader;
import org.dce.ed.binds.EliteKeyBinding;
import org.dce.ed.mission.MissionCategory;
import org.dce.ed.mission.MissionRecord;
import org.dce.ed.mission.MissionTracker;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.OverlayTransparentChrome;
import org.dce.ed.ui.TransparentTableHeader;
import org.dce.ed.ui.TransparentTableHeaderUI;
import org.dce.ed.util.EliteKeySender;

/**
 * Combat overlay tab — summary chips, shared bounty tables, kills/missions, fighter orders.
 */
public final class CombatTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int BUTTON_HOVER_DELAY_MS = 500;
    private static final int TABLE_MAX_VISIBLE_ROWS = 5;
    /** Blank-line gap between Combat sections (after table/block, before next header). */
    private static final int SECTION_GAP = 22;
    /** Prefer this many columns when width allows the full "Label (bind)" text. */
    private static final int COMMAND_GRID_MAX_COLS = 4;
    private static final int COMMAND_GRID_HGAP = 4;
    private static final int COMMAND_GRID_VGAP = 4;
    private static final String TOOLTIP_UNBOUND_COMMAND = "Add a key binding to enable this command";

    private final BooleanSupplier passThroughEnabledSupplier;
    private final ContentPanel content = new ContentPanel();
    private final JScrollPane scroll;

    private final StatChip earnedChip = new StatChip("EARNED");
    private final StatChip creditsPerHourChip = new StatChip("CREDITS/HOUR");
    private CombatSessionTracker combatSessionTracker;
    private final Timer creditsRateTimer = new Timer(1_000, e -> refreshFromTrackers());

    private final BountyRowTableModel targetModel = new BountyRowTableModel();
    private final JTable targetTable = new JTable(targetModel);
    private final JScrollPane targetScroll;

    private final BountyRowTableModel scannedModel = new BountyRowTableModel();
    private final JTable scannedTable = new JTable(scannedModel);
    private final JScrollPane scannedScroll;

    private final BountyRowTableModel killsModel = new BountyRowTableModel();
    private final JTable killsTable = new JTable(killsModel);
    private final JScrollPane killsScroll;

    private final MissionsTableModel missionsModel = new MissionsTableModel();
    private final JTable missionsTable = new JTable(missionsModel);
    private final JScrollPane missionsScroll;

    private final JLabel fighterPilotLabel = valueLabel("—", false);
    private final JPanel fighterButtonsRow = new JPanel(new GridLayout(0, 1, COMMAND_GRID_HGAP, COMMAND_GRID_VGAP));
    private final List<JButton> fighterButtons = new ArrayList<>();
    private final JPanel targetingButtonsRow = new JPanel(new GridLayout(0, 1, COMMAND_GRID_HGAP, COMMAND_GRID_VGAP));
    private final List<JButton> targetingButtons = new ArrayList<>();
    private final JPanel fighterBlock = new JPanel(new BorderLayout(6, 2));
    private final JPanel targetingBlock = new JPanel(new BorderLayout(6, 2));
    private final JPanel fighterTop = new JPanel(new BorderLayout());

    private final JLabel targetHeader = sectionHeader("TARGET");
    private final JLabel scannedHeader = sectionHeader("SCANNED");
    private final JLabel killsHeader = sectionHeader("KILLS");
    private final JLabel missionsHeader = sectionHeader("MISSIONS");
    private final JLabel fighterHeader = sectionHeader("FIGHTER");
    private final JLabel targetingHeader = sectionHeader("TARGETING");

    private MissionTracker missionTracker;
    private LongSupplier unclaimedBountyCreditsSupplier = () -> 0L;
    private Map<String, EliteKeyBinding> fighterBindings = Map.of();
    private Map<String, EliteKeyBinding> targetingBindings = Map.of();
    private final EliteKeySender keySender = new EliteKeySender(40, true);

    public CombatTabPanel(BooleanSupplier passThroughEnabledSupplier) {
        super(new BorderLayout(0, 0));
        this.passThroughEnabledSupplier = passThroughEnabledSupplier != null
                ? passThroughEnabledSupplier
                : () -> false;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        summary.setOpaque(false);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setBorder(new EmptyBorder(0, 0, 6, 0));
        summary.add(earnedChip);
        summary.add(creditsPerHourChip);
        // BoxLayout needs an explicit cap, but it must accommodate scaled two-line metric text.
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                summaryHeightForPreferredContent(summary.getPreferredSize().height)));

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(0, 8, 0, 8));

        styleCombatTable(targetTable);
        styleCombatTable(scannedTable);
        styleCombatTable(killsTable);
        styleCombatTable(missionsTable);
        targetTable.setDefaultRenderer(Object.class, new BountyRowRenderer(targetModel));
        scannedTable.setDefaultRenderer(Object.class, new BountyRowRenderer(scannedModel));
        killsTable.setDefaultRenderer(Object.class, new BountyRowRenderer(killsModel));
        missionsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
                setOpaque(false);
                setBackground(EdoUi.Internal.TRANSPARENT);
                setForeground(EdoUi.User.MAIN_TEXT);
                setBorder(new EmptyBorder(3, 6, 3, 6));
                setHorizontalAlignment(column == 1 ? SwingConstants.CENTER : SwingConstants.LEFT);
                if (c instanceof JComponent jc) {
                    jc.setOpaque(false);
                }
                return c;
            }
        });

        targetScroll = wrapTable(targetTable, 1);
        scannedScroll = wrapTable(scannedTable, TABLE_MAX_VISIBLE_ROWS);
        killsScroll = wrapTable(killsTable, TABLE_MAX_VISIBLE_ROWS);
        missionsScroll = wrapTable(missionsTable, TABLE_MAX_VISIBLE_ROWS);

        fighterButtonsRow.setOpaque(false);
        fighterButtonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fighterButtonsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        targetingButtonsRow.setOpaque(false);
        targetingButtonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetingButtonsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        fighterBlock.setOpaque(false);
        fighterBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        fighterBlock.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        fighterTop.setOpaque(false);
        fighterTop.setAlignmentX(Component.LEFT_ALIGNMENT);
        // One full-width border box: title on the left, pilot on the right.
        fighterHeader.setBorder(null);
        fighterTop.setBorder(sectionHeaderBorder());
        fighterTop.add(fighterHeader, BorderLayout.WEST);
        fighterTop.add(fighterPilotLabel, BorderLayout.EAST);
        syncSectionHeaderBarSize(fighterTop);
        fighterBlock.add(fighterTop, BorderLayout.NORTH);
        fighterBlock.add(fighterButtonsRow, BorderLayout.CENTER);

        targetingBlock.setOpaque(false);
        targetingBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetingBlock.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        targetingBlock.add(targetingHeader, BorderLayout.NORTH);
        targetingBlock.add(targetingButtonsRow, BorderLayout.CENTER);

        content.add(summary);
        content.add(targetHeader);
        content.add(Box.createVerticalStrut(2));
        content.add(targetScroll);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(scannedHeader);
        content.add(Box.createVerticalStrut(2));
        content.add(scannedScroll);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(killsHeader);
        content.add(Box.createVerticalStrut(2));
        content.add(killsScroll);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(missionsHeader);
        content.add(Box.createVerticalStrut(2));
        content.add(missionsScroll);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(targetingBlock);
        content.add(Box.createVerticalStrut(SECTION_GAP));
        content.add(fighterBlock);
        content.add(Box.createVerticalGlue());

        content.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayoutAllCommandGrids();
            }
        });

        scroll = new JScrollPane(content);
        OverlayTransparentChrome.configureScrollPane(scroll);
        OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
        add(scroll, BorderLayout.CENTER);

        reloadCombatCommandBindings();
        CombatTargetTracker.getInstance().addListener(this::requestRefresh);
        NpcCrewTracker.getInstance().addListener(this::requestRefresh);
        refreshFromTrackers();
    }

    public void setMissionTracker(MissionTracker tracker) {
        this.missionTracker = tracker;
        requestRefresh();
    }

    public void setUnclaimedBountyCreditsSupplier(LongSupplier supplier) {
        this.unclaimedBountyCreditsSupplier = supplier != null ? supplier : () -> 0L;
        requestRefresh();
    }

    public void setCombatSessionTracker(CombatSessionTracker tracker) {
        this.combatSessionTracker = tracker;
        if (tracker != null) {
            tracker.addListener(this::requestRefresh);
        }
        requestRefresh();
    }

    public void reloadFighterBindings() {
        reloadCombatCommandBindings();
    }

    /** Reloads Elite binds and rebuilds fighter/targeting buttons from Combat-tab visibility prefs. */
    public void reloadCombatCommandBindings() {
        fighterBindings = EliteBindsLoader.loadFighterOrderBindings();
        targetingBindings = EliteBindsLoader.loadTargetingBindings();
        rebuildFighterButtons();
        rebuildTargetingButtons();
        requestRefresh();
    }

    public void handleLogEvent(org.dce.ed.logreader.EliteLogEvent event) {
        if (event != null) {
            requestRefresh();
        }
    }

    public boolean isPointerOverInteractiveRegion(Point screenPoint) {
        if (!isShowing() || screenPoint == null) {
            return false;
        }
        for (JButton button : fighterButtons) {
            if (button != null && button.isShowing() && containsScreenPoint(button, screenPoint)) {
                return true;
            }
        }
        for (JButton button : targetingButtons) {
            if (button != null && button.isShowing() && containsScreenPoint(button, screenPoint)) {
                return true;
            }
        }
        return isPointerOverScrollBar(screenPoint);
    }

    public boolean isPointerOverScrollBar(Point screenPoint) {
        if (scroll == null || !scroll.isShowing() || screenPoint == null) {
            return false;
        }
        Component vsb = scroll.getVerticalScrollBar();
        Component hsb = scroll.getHorizontalScrollBar();
        return (vsb != null && vsb.isShowing() && containsScreenPoint(vsb, screenPoint))
                || (hsb != null && hsb.isShowing() && containsScreenPoint(hsb, screenPoint))
                || overTableScroll(targetScroll, screenPoint)
                || overTableScroll(scannedScroll, screenPoint)
                || overTableScroll(killsScroll, screenPoint)
                || overTableScroll(missionsScroll, screenPoint);
    }

    private static boolean overTableScroll(JScrollPane sp, Point screenPoint) {
        if (sp == null || !sp.isShowing()) {
            return false;
        }
        Component vsb = sp.getVerticalScrollBar();
        return vsb != null && vsb.isShowing() && containsScreenPoint(vsb, screenPoint);
    }

    public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        setOpaque(opaque);
        if (opaque && bgWithAlpha != null) {
            setBackground(bgWithAlpha);
        }
        applyUiFont(OverlayPreferences.getUiFont());
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }
        setFont(font);
        Font bold = font.deriveFont(Font.BOLD);
        Font tiny = font.deriveFont(Font.PLAIN, Math.max(9f, font.getSize2D() - 1f));
        for (JLabel h : List.of(targetHeader, scannedHeader, killsHeader, missionsHeader,
                targetingHeader)) {
            h.setFont(bold);
            h.setForeground(EdoUi.User.MAIN_TEXT);
            h.setBorder(sectionHeaderBorder());
            syncSectionHeaderBarSize(h);
        }
        fighterHeader.setFont(bold);
        fighterHeader.setForeground(EdoUi.User.MAIN_TEXT);
        fighterHeader.setBorder(null);
        fighterPilotLabel.setFont(font);
        fighterTop.setBorder(sectionHeaderBorder());
        syncSectionHeaderBarSize(fighterTop);
        earnedChip.applyFont(font);
        creditsPerHourChip.applyFont(font);
        styleCombatTable(targetTable);
        styleCombatTable(scannedTable);
        styleCombatTable(killsTable);
        styleCombatTable(missionsTable);
        if (targetScroll != null) {
            targetScroll.setColumnHeaderView(targetTable.getTableHeader());
            OverlayTransparentChrome.configureScrollPane(targetScroll);
        }
        if (scannedScroll != null) {
            scannedScroll.setColumnHeaderView(scannedTable.getTableHeader());
            OverlayTransparentChrome.configureScrollPane(scannedScroll);
        }
        if (killsScroll != null) {
            killsScroll.setColumnHeaderView(killsTable.getTableHeader());
            OverlayTransparentChrome.configureScrollPane(killsScroll);
        }
        if (missionsScroll != null) {
            missionsScroll.setColumnHeaderView(missionsTable.getTableHeader());
            OverlayTransparentChrome.configureScrollPane(missionsScroll);
        }
        for (JButton button : fighterButtons) {
            OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(button, tiny);
        }
        for (JButton button : targetingButtons) {
            OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(button, tiny);
        }
        relayoutAllCommandGrids();
        revalidate();
        repaint();
    }

    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    private void requestRefresh() {
        if (SwingUtilities.isEventDispatchThread()) {
            refreshFromTrackers();
        } else {
            SwingUtilities.invokeLater(this::refreshFromTrackers);
        }
    }

    private void refreshFromTrackers() {
        CombatTargetTracker tracker = CombatTargetTracker.getInstance();
        CombatSessionTracker.Snapshot session = combatSessionTracker != null
                ? combatSessionTracker.snapshot(Instant.now()) : null;
        earnedChip.setValue(session != null && session.hasDisplayedSession()
                ? formatCompact(session.earnedCredits()) : "—");
        creditsPerHourChip.setValue(session != null && session.hasDisplayedSession()
                ? formatCompact(session.creditsPerHour()) : "—");
        syncCreditsRateTimer(session != null && session.active());

        updateTargetTable(tracker.getLockedTarget());
        updateScannedTable(tracker.getScannedWantedShips());
        updateKillsTable(tracker.getKills());
        updateMissionsModel();
        resizeTableViewport(targetScroll, targetTable, 1);
        resizeTableViewport(scannedScroll, scannedTable, TABLE_MAX_VISIBLE_ROWS);
        resizeTableViewport(killsScroll, killsTable, TABLE_MAX_VISIBLE_ROWS);
        resizeTableViewport(missionsScroll, missionsTable, TABLE_MAX_VISIBLE_ROWS);
        updateFighterSection();
        revalidate();
        repaint();
    }

    private void syncCreditsRateTimer(boolean activeSession) {
        boolean shouldRun = shouldRunCreditsRateTimer(isShowing(), activeSession);
        if (shouldRun && !creditsRateTimer.isRunning()) {
            creditsRateTimer.start();
        } else if (!shouldRun && creditsRateTimer.isRunning()) {
            creditsRateTimer.stop();
        }
    }

    static boolean shouldRunCreditsRateTimer(boolean combatTabVisible, boolean activeSession) {
        return combatTabVisible && activeSession;
    }

    static int summaryHeightForPreferredContent(int preferredHeight) {
        return Math.max(56, Math.max(0, preferredHeight) + 6);
    }

    private void updateTargetTable(CombatTargetTracker.LockedTarget target) {
        if (target == null) {
            targetModel.setRows(List.of());
            return;
        }
        targetModel.setRows(List.of(BountyRow.fromLocked(target)));
    }

    private void updateScannedTable(List<CombatTargetTracker.ScannedWantedShip> ships) {
        if (ships == null || ships.isEmpty()) {
            scannedModel.setRows(List.of());
            return;
        }
        List<BountyRow> rows = new ArrayList<>(ships.size());
        for (CombatTargetTracker.ScannedWantedShip ship : ships) {
            rows.add(BountyRow.fromScanned(ship));
        }
        scannedModel.setRows(rows);
    }

    private void updateKillsTable(List<CombatTargetTracker.KillVictim> kills) {
        if (kills == null || kills.isEmpty()) {
            killsModel.setRows(List.of());
            return;
        }
        List<BountyRow> rows = new ArrayList<>(kills.size());
        for (CombatTargetTracker.KillVictim kill : kills) {
            rows.add(BountyRow.fromKill(kill));
        }
        killsModel.setRows(rows);
    }

    private void updateMissionsModel() {
        List<MissionRow> rows = new ArrayList<>();
        if (missionTracker != null) {
            for (MissionRecord r : missionTracker.getActive()) {
                if (r.getCategory() != MissionCategory.COMBAT || r.isRedirected()) {
                    continue;
                }
                String name = r.getLocalisedName();
                if (name == null || name.isBlank()) {
                    name = r.getName();
                }
                String progress = r.getKillCount() > 0
                        ? r.getKillsCompleted() + "/" + r.getKillCount()
                        : "—";
                String target = r.getTarget();
                if (target == null || target.isBlank()) {
                    target = r.getTargetFaction();
                }
                rows.add(new MissionRow(dash(name), progress, dash(target)));
            }
        }
        missionsModel.setRows(rows);
    }

    private void updateFighterSection() {
        NpcCrewTracker crew = NpcCrewTracker.getInstance();
        String pilot = crew.getActiveNpcCrewName();
        fighterPilotLabel.setText(pilot != null ? pilot : "No pilot");
        fighterPilotLabel.setForeground(pilot != null ? EdoUi.User.MAIN_TEXT : EdoUi.Internal.MAIN_TEXT_ALPHA_140);
        boolean hasPilot = crew.hasActiveNpcCrew();
        for (JButton button : fighterButtons) {
            EliteKeyBinding binding = (EliteKeyBinding) button.getClientProperty("edo.fighterBinding");
            String bindName = (String) button.getClientProperty("edo.combatBindName");
            // Unbound keys stay visible but disabled; bound keys need an Active pilot.
            boolean enabled = binding != null && hasPilot;
            button.setEnabled(enabled);
            button.setToolTipText(tooltipForCommandButton(binding, bindName, true, enabled));
        }
        for (JButton button : targetingButtons) {
            EliteKeyBinding binding = (EliteKeyBinding) button.getClientProperty("edo.combatBinding");
            String bindName = (String) button.getClientProperty("edo.combatBindName");
            boolean enabled = binding != null;
            button.setEnabled(enabled);
            button.setToolTipText(tooltipForCommandButton(binding, bindName, false, enabled));
        }
    }

    private static String tooltipForCommandButton(
            EliteKeyBinding binding, String bindName, boolean requirePilot, boolean enabled) {
        if (binding == null) {
            return TOOLTIP_UNBOUND_COMMAND;
        }
        if (!enabled && requirePilot) {
            return "Assign an active fighter pilot to enable this command";
        }
        String name = bindName != null && !bindName.isBlank() ? bindName : "command";
        return "Send " + name + " (" + binding.getDisplayLabel() + ")";
    }

    private void rebuildFighterButtons() {
        if (fighterBindings == null || fighterBindings.isEmpty()) {
            fighterBindings = EliteBindsLoader.loadFighterOrderBindings();
        }
        rebuildCommandButtons(
                fighterButtonsRow,
                fighterButtons,
                CombatTabCommands.FIGHTER,
                fighterBindings,
                "edo.fighterBinding",
                true);
        fighterBlock.setVisible(fighterButtonsRow.getComponentCount() > 0);
    }

    private void rebuildTargetingButtons() {
        if (targetingBindings == null || targetingBindings.isEmpty()) {
            targetingBindings = EliteBindsLoader.loadTargetingBindings();
        }
        rebuildCommandButtons(
                targetingButtonsRow,
                targetingButtons,
                CombatTabCommands.TARGETING,
                targetingBindings,
                "edo.combatBinding",
                false);
        targetingBlock.setVisible(targetingButtonsRow.getComponentCount() > 0);
    }

    private void rebuildCommandButtons(
            JPanel row,
            List<JButton> buttons,
            List<CombatTabCommands.Command> catalog,
            Map<String, EliteKeyBinding> bindings,
            String bindingProperty,
            boolean requirePilot) {
        row.removeAll();
        buttons.clear();
        Font font = OverlayPreferences.getUiFont();
        Font tiny = font.deriveFont(Font.PLAIN, Math.max(9f, font.getSize2D() - 1f));
        for (CombatTabCommands.Command command : catalog) {
            if (!OverlayPreferences.isCombatTabCommandVisible(command.bindName())) {
                continue;
            }
            EliteKeyBinding binding = bindings.get(command.bindName());
            String text = binding != null
                    ? command.label() + " (" + binding.getDisplayLabel() + ")"
                    : command.label();
            JButton button = new JButton(text);
            button.putClientProperty(bindingProperty, binding);
            button.putClientProperty("edo.combatBindName", command.bindName());
            boolean enabled = binding != null && (!requirePilot || NpcCrewTracker.getInstance().hasActiveNpcCrew());
            button.setEnabled(enabled);
            button.setToolTipText(tooltipForCommandButton(binding, command.bindName(), requirePilot, enabled));
            OverlayOutlineButtonStyle.applyPrimaryHitSafeCompact(button, tiny);
            button.addActionListener(e -> sendCombatBinding(binding));
            HoverClickPoller.register(
                    button,
                    BUTTON_HOVER_DELAY_MS,
                    () -> sendCombatBinding(binding),
                    passThroughEnabledSupplier,
                    () -> button.isShowing() && button.isEnabled());
            buttons.add(button);
            row.add(button);
        }
        relayoutCommandButtonGrid(row, buttons);
        row.revalidate();
        row.repaint();
    }

    private void relayoutAllCommandGrids() {
        relayoutCommandButtonGrid(fighterButtonsRow, fighterButtons);
        relayoutCommandButtonGrid(targetingButtonsRow, targetingButtons);
    }

    /**
     * Shrink column count (more rows) until each cell is wide enough for the full
     * {@code Label (binding)} text — bindings must remain visible.
     */
    private void relayoutCommandButtonGrid(JPanel row, List<JButton> buttons) {
        if (row == null || buttons == null || buttons.isEmpty()) {
            return;
        }
        int maxPref = 0;
        for (JButton button : buttons) {
            if (button == null) {
                continue;
            }
            maxPref = Math.max(maxPref, commandButtonPreferredWidth(button));
        }
        int available = commandGridAvailableWidth(row);
        int cols = commandGridColumns(available, maxPref, buttons.size(), COMMAND_GRID_HGAP);
        row.setLayout(new GridLayout(0, cols, COMMAND_GRID_HGAP, COMMAND_GRID_VGAP));
        int rows = Math.max(1, (buttons.size() + cols - 1) / cols);
        // Match compact hit-safe button height (~70% of full primary).
        int rowH = Math.max(20, Math.round((OverlayPreferences.getUiFontSize() + 18) * 0.7f));
        row.setMaximumSize(new Dimension(
                Integer.MAX_VALUE,
                rows * rowH + Math.max(0, rows - 1) * COMMAND_GRID_VGAP));
        row.revalidate();
    }

    private int commandGridAvailableWidth(JPanel row) {
        int w = row.getWidth();
        if (w <= 0 && content.isShowing()) {
            w = content.getWidth();
            Insets in = content.getInsets();
            if (in != null) {
                w -= in.left + in.right;
            }
        }
        if (w <= 0 && scroll != null && scroll.getViewport() != null) {
            w = scroll.getViewport().getWidth();
        }
        return Math.max(0, w);
    }

    private static int commandButtonPreferredWidth(JButton button) {
        Dimension pref = button.getPreferredSize();
        if (pref != null && pref.width > 0) {
            return pref.width;
        }
        Font font = button.getFont();
        if (font == null) {
            font = OverlayPreferences.getUiFont();
        }
        FontMetrics fm = button.getFontMetrics(font);
        String text = button.getText();
        int textW = text != null ? fm.stringWidth(text) : 0;
        Insets insets = button.getInsets();
        int pad = insets != null ? insets.left + insets.right : 36;
        return textW + pad + 4;
    }

    /**
     * Largest column count (≤ {@link #COMMAND_GRID_MAX_COLS}) where each cell fits
     * {@code maxButtonPreferredWidth}. Falls back to 1 column when width is unknown or tight.
     */
    static int commandGridColumns(int availableWidth, int maxButtonPreferredWidth, int buttonCount, int hgap) {
        if (buttonCount <= 0) {
            return 1;
        }
        int maxCols = Math.min(COMMAND_GRID_MAX_COLS, buttonCount);
        if (availableWidth <= 0 || maxButtonPreferredWidth <= 0) {
            return 1;
        }
        int gap = Math.max(0, hgap);
        for (int cols = maxCols; cols >= 1; cols--) {
            int cellW = (availableWidth - gap * (cols - 1)) / cols;
            if (cellW >= maxButtonPreferredWidth) {
                return cols;
            }
        }
        return 1;
    }

    private void sendCombatBinding(EliteKeyBinding binding) {
        if (binding == null) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                keySender.tapBinding(binding);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
            }
        }, "edo-combat-command");
        t.setDaemon(true);
        t.start();
    }

    private void styleCombatTable(JTable table) {
        Font base = OverlayPreferences.getUiFont();
        int rowH = Math.max(22, OverlayPreferences.getUiFontSize() + 10);
        boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(this);
        table.setOpaque(false);
        table.setBorder(null);
        table.setBackground(EdoUi.Internal.TRANSPARENT);
        table.setForeground(EdoUi.User.MAIN_TEXT);
        table.setSelectionBackground(EdoUi.Internal.TRANSPARENT);
        table.setSelectionForeground(EdoUi.User.MAIN_TEXT);
        table.setShowGrid(false);
        table.setShowHorizontalLines(false);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setGridColor(EdoUi.Internal.TRANSPARENT);
        table.setRowHeight(rowH);
        table.setFont(base.deriveFont(Font.PLAIN, OverlayPreferences.getUiFontSize()));
        table.setFillsViewportHeight(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.setFocusable(false);
        table.setRequestFocusEnabled(false);
        table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        if (!(table.getTableHeader() instanceof TransparentTableHeader)) {
            table.setTableHeader(new TransparentTableHeader(table.getColumnModel()));
        }
        JTableHeader th = table.getTableHeader();
        if (th != null) {
            th.setUI(TransparentTableHeaderUI.createUI(th));
            th.setOpaque(!transparent);
            th.setForeground(EdoUi.User.MAIN_TEXT);
            th.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
            th.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize()));
            th.setBorder(null);
            th.setReorderingAllowed(false);
            th.setFocusable(false);
            th.putClientProperty("JTableHeader.focusCellBackground", null);
            th.putClientProperty("JTableHeader.cellBorder", null);
            th.setDefaultRenderer(new CombatHeaderRenderer());
            th.setPreferredSize(new Dimension(Math.max(1, th.getPreferredSize().width), rowH));
        }
    }

    private JScrollPane wrapTable(JTable table, int maxRows) {
        JScrollPane sp = new JScrollPane(table);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (table.getTableHeader() != null) {
            sp.setColumnHeaderView(table.getTableHeader());
        }
        // Non-UIResource empty border so JTable cannot reinstall Table.scrollPaneBorder.
        OverlayTransparentChrome.configureScrollPane(sp);
        OverlayScrollPaneSupport.installSubtleScrollBars(sp);
        resizeTableViewport(sp, table, maxRows);
        return sp;
    }

    private static void resizeTableViewport(JScrollPane sp, JTable table, int maxRows) {
        if (sp == null || table == null) {
            return;
        }
        int rows = Math.max(1, Math.min(maxRows, Math.max(1, table.getRowCount())));
        int headerH = table.getTableHeader() != null ? table.getTableHeader().getPreferredSize().height : 0;
        int h = headerH + rows * table.getRowHeight() + 4;
        sp.setPreferredSize(new Dimension(100, h));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        sp.setMinimumSize(new Dimension(50, headerH + table.getRowHeight()));
    }

    static String formatCompact(long credits) {
        long c = Math.abs(credits);
        String sign = credits < 0 ? "-" : "";
        if (c >= 1_000_000_000L) {
            return sign + trimDecimal(c / 1_000_000_000.0) + "B";
        }
        if (c >= 1_000_000L) {
            return sign + trimDecimal(c / 1_000_000.0) + "M";
        }
        if (c >= 10_000L) {
            return sign + (c / 1_000L) + "K";
        }
        if (c >= 1_000L) {
            return sign + trimDecimal(c / 1_000.0) + "K";
        }
        return sign + Long.toString(c);
    }

    static String formatRemote(long remoteBounty, boolean warrantScanned) {
        String token = CombatTargetTracker.remoteDisplayToken(remoteBounty, warrantScanned);
        if (token != null) {
            return token;
        }
        return formatCompact(remoteBounty);
    }

    private static String trimDecimal(double v) {
        String s = String.format(java.util.Locale.US, "%.1f", v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static JLabel sectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(false);
        label.setForeground(EdoUi.User.MAIN_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(sectionHeaderBorder());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncSectionHeaderBarSize(label);
        return label;
    }

    /** Outline plate around section titles (TARGET / SCANNED / …). */
    private static javax.swing.border.Border sectionHeaderBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.User.MAIN_TEXT, 1),
                new EmptyBorder(4, 8, 4, 8));
    }

    /** Stretch horizontally to the content edge; keep natural height so BoxLayout does not grow it. */
    private static void syncSectionHeaderBarSize(JComponent bar) {
        if (bar == null) {
            return;
        }
        int h = Math.max(1, bar.getPreferredSize().height);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    private static JLabel tinyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(false);
        label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_140);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 9f));
        return label;
    }

    private static JLabel valueLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setOpaque(false);
        label.setForeground(EdoUi.User.MAIN_TEXT);
        if (bold) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        return label;
    }

    private static boolean containsScreenPoint(Component c, Point screenPoint) {
        if (c == null || screenPoint == null || !c.isShowing()) {
            return false;
        }
        try {
            Point loc = c.getLocationOnScreen();
            return new Rectangle(loc.x, loc.y, c.getWidth(), c.getHeight()).contains(screenPoint);
        } catch (IllegalComponentStateException ignored) {
            return false;
        }
    }

    private static final class StatChip extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JLabel caption;
        private final JLabel value;

        StatChip(String captionText) {
            super(new BorderLayout());
            setOpaque(false);
            caption = tinyLabel(captionText);
            caption.setHorizontalAlignment(SwingConstants.CENTER);
            value = new JLabel("—", SwingConstants.CENTER);
            value.setOpaque(false);
            value.setForeground(EdoUi.User.MAIN_TEXT);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 14f));
            add(caption, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, EdoUi.Internal.separatorLine()),
                    new EmptyBorder(2, 4, 4, 4)));
            setPreferredSize(new Dimension(112, getPreferredSize().height));
        }

        void setValue(String text) {
            value.setText(text != null ? text : "—");
        }

        void applyFont(Font font) {
            caption.setFont(font.deriveFont(Font.PLAIN, Math.max(8f, font.getSize2D() - 2f)));
            value.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 2f));
        }
    }

    /** Shared row for TARGET, SCANNED, and KILLS tables. */
    private static final class BountyRow {
        private final String pilot;
        private final String ship;
        private final String local;
        private final String remote;
        private final String total;
        private final long bountyCredits;
        private final boolean player;

        BountyRow(String pilot, String ship, String local, String remote, String total,
                long bountyCredits, boolean player) {
            this.pilot = pilot;
            this.ship = ship;
            this.local = local;
            this.remote = remote;
            this.total = total;
            this.bountyCredits = Math.max(0L, bountyCredits);
            this.player = player;
        }

        static BountyRow fromLocked(CombatTargetTracker.LockedTarget t) {
            long bountyCredits = t.getBounty() != null ? Math.max(0L, t.getBounty().longValue()) : 0L;
            String local = t.getLocalBounty() != null && t.getLocalBounty() > 0L
                    ? formatCompact(t.getLocalBounty())
                    : "—";
            String total = bountyCredits > 0L
                    ? formatCompact(bountyCredits)
                    : "—";
            String remote = (t.getLocalBounty() != null && t.getLocalBounty() > 0L)
                    ? formatRemote(t.getRemoteBounty(), t.isWarrantScanned())
                    : "—";
            return new BountyRow(
                    dash(t.getPilotName()),
                    dash(t.getShipDisplay()),
                    local,
                    remote,
                    total,
                    bountyCredits,
                    t.isPlayer());
        }

        static BountyRow fromScanned(CombatTargetTracker.ScannedWantedShip s) {
            return new BountyRow(
                    dash(s.getPilotName()),
                    dash(s.getShipDisplay()),
                    formatCompact(s.getFirstBounty()),
                    formatRemote(s.getRemoteBounty(), s.isWarrantScanned()),
                    formatCompact(s.getCurrentBounty()),
                    s.getCurrentBounty(),
                    s.isPlayer());
        }

        static BountyRow fromKill(CombatTargetTracker.KillVictim k) {
            long total = Math.max(0L, k.getTotalReward());
            long remote = Math.max(0L, k.getOtherReward());
            long local = Math.max(0L, total - remote);
            String pilot = k.getPilotName();
            if (pilot == null || pilot.isBlank()) {
                pilot = k.getVictimFaction();
            }
            return new BountyRow(
                    dash(pilot),
                    dash(k.getShipDisplay()),
                    local > 0L ? formatCompact(local) : "—",
                    remote > 0L ? formatCompact(remote) : "—",
                    total > 0L ? formatCompact(total) : "—",
                    total,
                    false);
        }
    }

    private static final class BountyRowTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLS = { "Pilot", "Ship", "Local", "Remote", "Total" };
        private List<BountyRow> rows = List.of();

        void setRows(List<BountyRow> next) {
            rows = next != null ? List.copyOf(next) : List.of();
            fireTableDataChanged();
        }

        BountyRow rowAt(int row) {
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        @Override public int getRowCount() { return Math.max(1, rows.size()); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int column) { return COLS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rows.isEmpty()) {
                return columnIndex == 0 ? "—" : "";
            }
            BountyRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.pilot;
                case 1 -> r.ship;
                case 2 -> r.local;
                case 3 -> r.remote;
                case 4 -> r.total;
                default -> "";
            };
        }
    }

    private record MissionRow(String name, String progress, String target) {}

    private static final class MissionsTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLS = { "Mission", "Kills", "Target" };
        private List<MissionRow> rows = List.of();

        void setRows(List<MissionRow> next) {
            rows = next != null ? List.copyOf(next) : List.of();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return Math.max(1, rows.size()); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int column) { return COLS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rows.isEmpty()) {
                return columnIndex == 0 ? "—" : "";
            }
            MissionRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.name();
                case 1 -> r.progress();
                case 2 -> r.target();
                default -> "";
            };
        }
    }

    private static final class CombatHeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        CombatHeaderRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            if (c instanceof JLabel label) {
                boolean transparent = OverlayPreferences.overlayChromeRequestsTransparency(label);
                label.setOpaque(!transparent);
                label.setBackground(transparent ? EdoUi.Internal.TRANSPARENT : EdoUi.User.BACKGROUND);
                label.setForeground(EdoUi.User.MAIN_TEXT);
                label.setBorder(new EmptyBorder(2, 6, 4, 6));
                label.setHorizontalAlignment(column >= 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                if (table != null && table.getModel() instanceof MissionsTableModel) {
                    label.setHorizontalAlignment(column == 1 ? SwingConstants.CENTER : SwingConstants.LEFT);
                }
            }
            return c;
        }
    }

    private static final class BountyRowRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final BountyRowTableModel model;

        BountyRowRenderer(BountyRowTableModel model) {
            this.model = model;
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(false);
            setBackground(EdoUi.Internal.TRANSPARENT);
            setBorder(new EmptyBorder(3, 6, 3, 6));
            setHorizontalAlignment(column >= 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            BountyRow bountyRow = model.rowAt(row);
            if (bountyRow != null && bountyRow.bountyCredits > 0L) {
                long highValue = OverlayPreferences.getCombatHighValueBountyCredits();
                if (bountyRow.bountyCredits >= highValue) {
                    setForeground(EdoUi.User.SECONDARY_HIGHLIGHT);
                } else {
                    setForeground(EdoUi.User.PRIMARY_HIGHLIGHT);
                }
            } else if (bountyRow != null && bountyRow.player) {
                setForeground(EdoUi.User.CORE_BLUE);
            } else {
                setForeground(EdoUi.User.MAIN_TEXT);
            }
            if (c instanceof JComponent jc) {
                jc.setOpaque(false);
            }
            return c;
        }
    }

    private static final class ContentPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(16, visibleRect.height - 16)
                    : Math.max(16, visibleRect.width - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
