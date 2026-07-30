package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringCraftStore;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringJournalBlueprintResolver;
import org.dce.ed.engineering.EngineeringMaterialKeys;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.engineering.MaterialRequirement;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.ui.AlwaysOnTopPopupFactory;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Modal picker for adding or editing an engineering goal (blueprint, grade, quantity, experimental).
 */
final class EngineeringGoalDialog extends JDialog {

    private enum Mode { ADD, EDIT }

    private static final int MAX_QUANTITY = 16;

    private static final int HOVER_CLICK_DELAY_MS = 500;
    private static final int BLUEPRINT_AC_MIN_CHARS = 1;
    private static final int BLUEPRINT_AC_VISIBLE_ROWS = 10;

    private final EngineeringDatabase database;
    private final BooleanSupplier passThroughEnabledSupplier;
    private final Mode mode;
    private final EngineeringGoal editSource;
    private final JLabel blueprintSummaryLabel = new JLabel(" ");
    /** Min 0 so Edit can set quantity to 0 and confirm deletion on Save. */
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 0, MAX_QUANTITY, 1));
    private final JTextField blueprintField = new JTextField(28);
    private final JCheckBox installedOnlyCheck = new JCheckBox("Installed only");
    private final JLabel effectsLabel = new JLabel(" ");
    private final JLabel materialsLabel = new JLabel(" ");
    private final JComboBox<String> gradeCombo = new JComboBox<>();
    private final JComboBox<String> experimentalCombo = new JComboBox<>();
    private final JComboBox<EngineeringShipRef> shipCombo = new JComboBox<>();
    private final EngineeringShipCatalog shipCatalog;
    private List<EngineeringShipRef> shipChoices;
    private final EngineeringShipRef defaultShip;
    private final AddPrefill addPrefill;
    private boolean applyingPrefill;
    private boolean suppressBlueprintFieldEvents;
    private List<BlueprintOption> blueprintCatalog = List.of();
    private BlueprintOption selectedBlueprint;
    private JPopupMenu blueprintPopup;
    private JList<BlueprintOption> blueprintSuggestList;
    private String originalBlueprintFieldText;

    private EngineeringGoal result;
    /** Set when Edit Save with quantity 0 and the user confirms deletion. */
    private boolean deleted;

    private EngineeringGoalDialog(Window owner,
                                  EngineeringDatabase database,
                                  BooleanSupplier passThroughEnabledSupplier,
                                  Mode mode,
                                  EngineeringGoal editSource,
                                  EngineeringShipCatalog shipCatalog,
                                  EngineeringShipRef defaultShip,
                                  AddPrefill addPrefill) {
        super(owner, mode == Mode.EDIT ? "Edit engineering goal" : "Add engineering goal", ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.editSource = editSource;
        this.database = database;
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        this.shipCatalog = shipCatalog;
        this.shipChoices = shipCatalog != null ? shipCatalog.listSorted() : List.of();
        this.defaultShip = defaultShip;
        this.addPrefill = addPrefill != null ? addPrefill : AddPrefill.EMPTY;
        buildUi();
        if (mode == Mode.ADD) {
            selectShipInCombo(defaultShip);
            reloadBlueprintCatalog();
            applyAddPrefill();
        } else {
            prefillFromGoal(editSource);
        }
        equalizeGradeQtyWidths();
        pack();
        setMinimumSize(new Dimension(520, mode == Mode.EDIT ? 280 : 360));
        setLocationRelativeTo(owner);
        // Loadout and other engineering dialogs are always-on-top; without this, Edit/Add Goal
        // can open underneath them (especially when parented to the overlay frame).
        setAlwaysOnTop(true);
        AlwaysOnTopPopupFactory.installWhileShowing(this);
    }

    /** Prefill options when opening Add Goal from build progress / elsewhere. */
    static record AddPrefill(
            String moduleType,
            String blueprintName,
            String searchText,
            String experimentalName,
            int quantity,
            int preferredTargetGrade) {
        static final AddPrefill EMPTY = new AddPrefill("", "", "", "", 1, 0);

        AddPrefill {
            moduleType = moduleType != null ? moduleType.trim() : "";
            blueprintName = blueprintName != null ? blueprintName.trim() : "";
            searchText = searchText != null ? searchText.trim() : "";
            experimentalName = experimentalName != null ? experimentalName.trim() : "";
            quantity = Math.max(1, quantity);
            preferredTargetGrade = Math.max(0, preferredTargetGrade);
        }

        AddPrefill(String moduleType, String blueprintName, String searchText, String experimentalName, int quantity) {
            this(moduleType, blueprintName, searchText, experimentalName, quantity, 0);
        }

        static AddPrefill of(String moduleType, String blueprintName) {
            return new AddPrefill(moduleType, blueprintName, "", "", 1, 0);
        }
    }

    private static volatile EngineeringGoalDialog activeInstance;

    /** Refresh ship list/labels from the live catalog (e.g. after {@code SetUserShipName}). */
    void refreshShipsFromCatalog() {
        if (!isDisplayable()) {
            return;
        }
        EngineeringShipRef selected = (EngineeringShipRef) shipCombo.getSelectedItem();
        long keepId = selected != null && selected.isKnown()
                ? selected.getShipId()
                : (defaultShip != null && defaultShip.isKnown() ? defaultShip.getShipId() : -1L);
        shipChoices = shipCatalog != null ? shipCatalog.listSorted() : List.of();
        populateShipCombo();
        if (keepId >= 0) {
            for (int i = 0; i < shipCombo.getItemCount(); i++) {
                EngineeringShipRef s = shipCombo.getItemAt(i);
                if (s != null && s.getShipId() == keepId) {
                    shipCombo.setSelectedIndex(i);
                    shipCombo.repaint();
                    return;
                }
            }
        }
        shipCombo.repaint();
    }

    static void refreshActiveShipChoices() {
        EngineeringGoalDialog dialog = activeInstance;
        if (dialog != null) {
            dialog.refreshShipsFromCatalog();
        }
    }

@Deprecated
    static EngineeringGoal showForAdd(Window owner,
                                      EngineeringDatabase database,
                                      BooleanSupplier passThroughEnabledSupplier,
                                      EngineeringShipCatalog shipCatalog,
                                      EngineeringShipRef defaultShip) {
        return showForAdd(owner, database, passThroughEnabledSupplier, shipCatalog, defaultShip, AddPrefill.EMPTY);
    }

    static EngineeringGoal showForAdd(Window owner,
                                      EngineeringDatabase database,
                                      BooleanSupplier passThroughEnabledSupplier,
                                      EngineeringShipCatalog shipCatalog,
                                      EngineeringShipRef defaultShip,
                                      String preferredModuleType) {
        return showForAdd(owner, database, passThroughEnabledSupplier, shipCatalog, defaultShip,
                AddPrefill.of(preferredModuleType, null));
    }

    static EngineeringGoal showForAdd(Window owner,
                                      EngineeringDatabase database,
                                      BooleanSupplier passThroughEnabledSupplier,
                                      EngineeringShipCatalog shipCatalog,
                                      EngineeringShipRef defaultShip,
                                      String preferredModuleType,
                                      String preferredBlueprintName) {
        return showForAdd(owner, database, passThroughEnabledSupplier, shipCatalog, defaultShip,
                AddPrefill.of(preferredModuleType, preferredBlueprintName));
    }

    static EngineeringGoal showForAdd(Window owner,
                                      EngineeringDatabase database,
                                      BooleanSupplier passThroughEnabledSupplier,
                                      EngineeringShipCatalog shipCatalog,
                                      EngineeringShipRef defaultShip,
                                      AddPrefill prefill) {
        if (database == null) {
            return null;
        }
        EngineeringGoalDialog dialog = new EngineeringGoalDialog(
                owner, database, passThroughEnabledSupplier, Mode.ADD, null, shipCatalog, defaultShip, prefill);
        activeInstance = dialog;
        try {
            dialog.setVisible(true);
            return dialog.result;
        } finally {
            activeInstance = null;
        }
    }

    /**
     * Outcome of {@link #showForEdit}: saved goal, confirmed deletion, or cancel/close.
     */
    static record EditResult(EngineeringGoal goal, boolean deleted) {
        static EditResult cancelled() {
            return new EditResult(null, false);
        }

        static EditResult saved(EngineeringGoal goal) {
            return new EditResult(goal, false);
        }

        static EditResult deleteConfirmed() {
            return new EditResult(null, true);
        }

        boolean wasSaved() {
            return goal != null && !deleted;
        }
    }

@Deprecated
    static EditResult showForEdit(Window owner,
                                  EngineeringDatabase database,
                                  BooleanSupplier passThroughEnabledSupplier,
                                  EngineeringGoal existing,
                                  EngineeringShipCatalog shipCatalog) {
        return showForEdit(owner, database, passThroughEnabledSupplier, existing, shipCatalog, AddPrefill.EMPTY);
    }

    /**
     * @param prefill optional Loadout context (e.g. fitted experimental) used when the saved goal
     *                has no experimental yet
     */
    static EditResult showForEdit(Window owner,
                                  EngineeringDatabase database,
                                  BooleanSupplier passThroughEnabledSupplier,
                                  EngineeringGoal existing,
                                  EngineeringShipCatalog shipCatalog,
                                  AddPrefill prefill) {
        if (database == null || existing == null) {
            return EditResult.cancelled();
        }
        EngineeringShipRef def = null;
        if (existing.hasShip()) {
            if (shipCatalog != null) {
                def = shipCatalog.get(existing.getShipId());
            }
            if (def == null) {
                def = new EngineeringShipRef(existing.getShipId(), "", existing.getShipLabel(), "");
            }
        }
        EngineeringGoalDialog dialog = new EngineeringGoalDialog(
                owner, database, passThroughEnabledSupplier, Mode.EDIT, existing, shipCatalog, def, prefill);
        activeInstance = dialog;
        try {
            dialog.setVisible(true);
            if (dialog.deleted) {
                return EditResult.deleteConfirmed();
            }
            if (dialog.result != null) {
                return EditResult.saved(dialog.result);
            }
            return EditResult.cancelled();
        } finally {
            activeInstance = null;
        }
    }

    /** @deprecated use {@link #showForAdd} with ship catalog */
    @Deprecated
    static EngineeringGoal showForAdd(Window owner,
                                      EngineeringDatabase database,
                                      BooleanSupplier passThroughEnabledSupplier) {
        return showForAdd(owner, database, passThroughEnabledSupplier,
                (EngineeringShipCatalog) null, null);
    }

    /** @deprecated use {@link #showForEdit} with ship catalog */
    @Deprecated
    static EditResult showForEdit(Window owner,
                                  EngineeringDatabase database,
                                  BooleanSupplier passThroughEnabledSupplier,
                                  EngineeringGoal existing) {
        return showForEdit(owner, database, passThroughEnabledSupplier, existing,
                (EngineeringShipCatalog) null);
    }

    /** @deprecated use {@link #showForAdd} */
@Deprecated
    static EngineeringGoal show(Window owner, EngineeringDatabase database, BooleanSupplier passThroughEnabledSupplier) {
        return showForAdd(owner, database, passThroughEnabledSupplier);
    }

    private void prefillFromGoal(EngineeringGoal goal) {
        if (goal == null) {
            return;
        }
        blueprintSummaryLabel.setText(goal.getModuleType() + ": " + goal.getBlueprintName());
        quantitySpinner.setValue(goal.getQuantity());
        gradeCombo.removeAllItems();
        experimentalCombo.removeAllItems();
        experimentalCombo.addItem("(none)");
        int maxGrade = database.gradesFor(goal.getModuleType(), goal.getBlueprintName()).stream()
                .filter(g -> !g.isExperimental())
                .mapToInt(BlueprintGrade::getGrade)
                .max()
                .orElse(goal.getTargetGrade());
        for (int g = 1; g <= maxGrade; g++) {
            gradeCombo.addItem("G" + g);
        }
        gradeCombo.setSelectedItem("G" + goal.getTargetGrade());
        for (BlueprintGrade exp : database.experimentalsFor(goal.getModuleType(), goal.getBlueprintName())) {
            experimentalCombo.addItem(exp.getName());
        }
        if (goal.getExperimentalId().isBlank()) {
            // Loadout may pass the fitted experimental when the saved goal has none yet.
            if (addPrefill != null && !addPrefill.experimentalName().isBlank()) {
                selectExperimentalFuzzy(addPrefill.experimentalName());
            } else {
                experimentalCombo.setSelectedItem("(none)");
            }
        } else {
            database.findById(goal.getExperimentalId())
                    .ifPresentOrElse(bp -> experimentalCombo.setSelectedItem(bp.getName()),
                            () -> selectExperimentalFuzzy(goal.getExperimentalId()));
        }
        populateShipCombo();
        if (goal.hasShip()) {
            selectShipInCombo(new EngineeringShipRef(goal.getShipId(), "", goal.getShipLabel(), ""));
        } else {
            selectShipInCombo(defaultShip);
        }
        updateGradeDetails();
        equalizeGradeQtyWidths();
        syncExperimentalComboTooltip();
    }

    private void populateShipCombo() {
        shipCombo.removeAllItems();
        for (EngineeringShipRef ship : shipChoices) {
            if (ship != null && ship.isKnown()) {
                shipCombo.addItem(ship);
            }
        }
        if (defaultShip != null && defaultShip.isKnown()) {
            boolean present = false;
            for (int i = 0; i < shipCombo.getItemCount(); i++) {
                EngineeringShipRef s = shipCombo.getItemAt(i);
                if (s != null && s.getShipId() == defaultShip.getShipId()) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                shipCombo.addItem(defaultShip);
            }
        }
        if (editSource != null && editSource.hasShip()) {
            boolean present = false;
            for (int i = 0; i < shipCombo.getItemCount(); i++) {
                EngineeringShipRef s = shipCombo.getItemAt(i);
                if (s != null && s.getShipId() == editSource.getShipId()) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                shipCombo.addItem(new EngineeringShipRef(
                        editSource.getShipId(), "", editSource.getShipLabel(), ""));
            }
        }
    }

    private void selectShipInCombo(EngineeringShipRef wanted) {
        populateShipCombo();
        if (wanted == null || !wanted.isKnown()) {
            if (shipCombo.getItemCount() > 0) {
                shipCombo.setSelectedIndex(0);
            }
            return;
        }
        for (int i = 0; i < shipCombo.getItemCount(); i++) {
            EngineeringShipRef s = shipCombo.getItemAt(i);
            if (s != null && s.getShipId() == wanted.getShipId()) {
                shipCombo.setSelectedIndex(i);
                return;
            }
        }
        shipCombo.addItem(wanted);
        shipCombo.setSelectedItem(wanted);
    }

    /**
     * Remember the Add Goal ship until the equipped loadout ship changes.
     */
    private void persistAddGoalShipChoice() {
        EngineeringShipRef selected = (EngineeringShipRef) shipCombo.getSelectedItem();
        if (selected == null || !selected.isKnown()) {
            return;
        }
        OverlayPreferences.setEngineeringAddGoalPreferredShipId(selected.getShipId());
        EngineeringShipRef equipped = EngineeringShipCatalog.fromLoadout(EliteOverlayTabbedPane.getLatestLoadout());
        if (equipped != null && equipped.isKnown()) {
            OverlayPreferences.setEngineeringAddGoalEquippedBaselineId(equipped.getShipId());
        }
    }

    private void buildUi() {
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setOpaque(true);
        root.setBackground(EdoUi.User.BACKGROUND);
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel intro = new JLabel(mode == Mode.EDIT
                ? "Change quantity, target grade, or experimental for this goal."
                : "Choose ship, then type a module or blueprint name to pick one.");
        intro.setFont(base.deriveFont(Font.PLAIN, fontSize));
        intro.setForeground(EdoUi.User.MAIN_TEXT);

        // Ship / target grade / quantity at the top (Add and Edit).
        styleCombo(shipCombo, base);
        constrainComboHeight(shipCombo);
        shipCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof EngineeringShipRef ship) {
                    setText(shipCatalog != null
                            ? shipCatalog.displayLabel(ship)
                            : EngineeringShipRef.displayLabelAmong(ship, shipChoices));
                }
                setForeground(EdoUi.User.MAIN_TEXT);
                setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
                return c;
            }
        });
        populateShipCombo();
        selectShipInCombo(defaultShip);
        if (mode == Mode.ADD) {
            shipCombo.addActionListener(e -> {
                persistAddGoalShipChoice();
                if (installedOnlyCheck.isSelected()) {
                    reloadBlueprintCatalog();
                    refreshBlueprintSuggestions();
                }
            });
        }
        styleCombo(gradeCombo, base);
        constrainComboHeight(gradeCombo);
        gradeCombo.addActionListener(e -> updateGradeDetails());
        styleQuantitySpinner(base);
        quantitySpinner.setToolTipText(mode == Mode.EDIT
                ? "How many modules to engineer. Set to 0 and Save to delete this goal."
                : "How many modules to engineer (e.g. four gimbal weapons)");

        JPanel topSettings = new JPanel(new GridBagLayout());
        topSettings.setOpaque(false);
        GridBagConstraints topGbc = new GridBagConstraints();
        topGbc.insets = new Insets(4, 4, 4, 8);
        topGbc.anchor = GridBagConstraints.WEST;
        topGbc.gridx = 0;
        topGbc.gridy = 0;
        topSettings.add(fieldLabel("Ship:", base, fontSize), topGbc);
        topGbc.gridx = 1;
        topGbc.fill = GridBagConstraints.HORIZONTAL;
        topGbc.weightx = 1;
        topSettings.add(shipCombo, topGbc);
        topGbc.gridx = 0;
        topGbc.gridy = 1;
        topGbc.weightx = 0;
        topGbc.fill = GridBagConstraints.NONE;
        topSettings.add(fieldLabel("Target grade:", base, fontSize), topGbc);
        topGbc.gridx = 1;
        topSettings.add(gradeCombo, topGbc);
        topGbc.gridx = 0;
        topGbc.gridy = 2;
        topSettings.add(fieldLabel("Quantity:", base, fontSize), topGbc);
        topGbc.gridx = 1;
        topSettings.add(quantitySpinner, topGbc);

        styleCombo(experimentalCombo, base);
        constrainComboHeight(experimentalCombo);
        experimentalCombo.addItem("(none)");
        installExperimentalEffectTooltips();
        if (mode == Mode.EDIT) {
            // Keep Experimental in the same grid so labels line up with Ship / Grade / Quantity.
            topGbc.gridx = 0;
            topGbc.gridy = 3;
            topGbc.weightx = 0;
            topGbc.fill = GridBagConstraints.NONE;
            topSettings.add(fieldLabel("Experimental:", base, fontSize), topGbc);
            topGbc.gridx = 1;
            topGbc.weightx = 1;
            topGbc.fill = GridBagConstraints.HORIZONTAL;
            topSettings.add(experimentalCombo, topGbc);
        }
        equalizeGradeQtyWidths();

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(intro, BorderLayout.NORTH);
        north.add(topSettings, BorderLayout.CENTER);
        root.add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setOpaque(false);
        if (mode == Mode.EDIT) {
            blueprintSummaryLabel.setFont(base.deriveFont(Font.BOLD, fontSize));
            blueprintSummaryLabel.setForeground(EdoUi.User.MAIN_TEXT);
            blueprintSummaryLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
            center.add(blueprintSummaryLabel, BorderLayout.NORTH);
        } else {
            styleTextField(blueprintField, base);
            blueprintField.setToolTipText("Type a module or blueprint name — pick from the popup");
            OverlayCheckBoxStyle.apply(installedOnlyCheck);
            installedOnlyCheck.setFont(base.deriveFont(Font.PLAIN, fontSize));
            installedOnlyCheck.setToolTipText(
                    "Show only blueprints for module types fitted on the selected ship");
            installedOnlyCheck.setSelected(OverlayPreferences.isEngineeringBlueprintPickerInstalledOnly());
            installedOnlyCheck.addItemListener(e -> {
                if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED
                        && e.getStateChange() != java.awt.event.ItemEvent.DESELECTED) {
                    return;
                }
                OverlayPreferences.setEngineeringBlueprintPickerInstalledOnly(installedOnlyCheck.isSelected());
                reloadBlueprintCatalog();
                refreshBlueprintSuggestions();
            });
            HoverClickPoller.register(
                    installedOnlyCheck,
                    HOVER_CLICK_DELAY_MS,
                    () -> installedOnlyCheck.setSelected(!installedOnlyCheck.isSelected()),
                    passThroughEnabledSupplier);

            JLabel blueprintSection = sectionHeader("Blueprint", base, fontSize);
            JPanel searchRow = new JPanel(new BorderLayout(8, 0));
            searchRow.setOpaque(false);
            searchRow.add(blueprintField, BorderLayout.CENTER);
            searchRow.add(installedOnlyCheck, BorderLayout.EAST);
            blueprintSummaryLabel.setFont(base.deriveFont(Font.BOLD, fontSize));
            blueprintSummaryLabel.setForeground(EdoUi.User.MAIN_TEXT);
            blueprintSummaryLabel.setBorder(new EmptyBorder(6, 0, 2, 0));
            blueprintSummaryLabel.setText(" ");
            JPanel searchBlock = new JPanel(new BorderLayout(0, 4));
            searchBlock.setOpaque(false);
            searchBlock.add(blueprintSection, BorderLayout.NORTH);
            searchBlock.add(searchRow, BorderLayout.CENTER);
            searchBlock.add(blueprintSummaryLabel, BorderLayout.SOUTH);
            center.add(searchBlock, BorderLayout.NORTH);

            wireBlueprintAutocomplete(base, fontSize);

            effectsLabel.setFont(base.deriveFont(Font.ITALIC, fontSize));
            effectsLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
            effectsLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
            materialsLabel.setFont(base.deriveFont(Font.PLAIN, fontSize));
            materialsLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
            JPanel detailPanel = new JPanel(new BorderLayout(0, 4));
            detailPanel.setOpaque(false);
            detailPanel.add(effectsLabel, BorderLayout.NORTH);
            detailPanel.add(materialsLabel, BorderLayout.SOUTH);
            center.add(detailPanel, BorderLayout.CENTER);
        }

        JPanel bottomForm = new JPanel(new GridBagLayout());
        bottomForm.setOpaque(false);
        if (mode == Mode.ADD) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 8);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            bottomForm.add(fieldLabel("Experimental:", base, fontSize), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            bottomForm.add(experimentalCombo, gbc);
            equalizeGradeQtyWidths();
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton addBtn = new JButton(mode == Mode.EDIT ? "Save" : "Add goal");
        OverlayOutlineButtonStyle.applyChip(addBtn, base, false);
        addBtn.addActionListener(e -> confirmSave());
        HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, this::confirmSave, passThroughEnabledSupplier);
        JButton cancelBtn = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyChip(cancelBtn, base, false);
        cancelBtn.addActionListener(e -> dispose());
        HoverClickPoller.register(cancelBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
        buttons.add(addBtn);
        buttons.add(cancelBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        if (mode == Mode.ADD) {
            south.add(bottomForm, BorderLayout.CENTER);
        }
        south.add(buttons, BorderLayout.SOUTH);
        if (mode == Mode.ADD) {
            root.add(center, BorderLayout.CENTER);
            root.add(south, BorderLayout.PAGE_END);
        } else {
            root.add(center, BorderLayout.CENTER);
            root.add(south, BorderLayout.PAGE_END);
        }

        setContentPane(root);
        getRootPane().setDefaultButton(addBtn);
    }

    private void wireBlueprintAutocomplete(Font base, int fontSize) {
        blueprintField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onBlueprintFieldTyping();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onBlueprintFieldTyping();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onBlueprintFieldTyping();
            }
        });
        blueprintField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                refreshBlueprintSuggestions();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Delay hide so list clicks can register first.
                SwingUtilities.invokeLater(() -> {
                    if (blueprintSuggestList != null && blueprintSuggestList.isFocusOwner()) {
                        return;
                    }
                    hideBlueprintPopup();
                });
            }
        });

        InputMap im = blueprintField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = blueprintField.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "blueprintAcHide");
        am.put("blueprintAcHide", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideBlueprintPopup();
            }
        });
        im.put(KeyStroke.getKeyStroke("DOWN"), "blueprintAcDown");
        am.put("blueprintAcDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (blueprintPopup == null || !blueprintPopup.isVisible()
                        || blueprintSuggestList == null
                        || blueprintSuggestList.getModel().getSize() == 0) {
                    refreshBlueprintSuggestions();
                    if (blueprintSuggestList == null || blueprintSuggestList.getModel().getSize() == 0) {
                        return;
                    }
                }
                originalBlueprintFieldText = blueprintField.getText();
                blueprintSuggestList.setSelectedIndex(0);
                blueprintSuggestList.ensureIndexIsVisible(0);
                blueprintSuggestList.requestFocusInWindow();
            }
        });
        im.put(KeyStroke.getKeyStroke("ENTER"), "blueprintAcEnter");
        am.put("blueprintAcEnter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (blueprintPopup != null && blueprintPopup.isVisible()
                        && blueprintSuggestList != null
                        && blueprintSuggestList.getSelectedValue() != null) {
                    applyBlueprintSelection(blueprintSuggestList.getSelectedValue());
                    return;
                }
                List<BlueprintOption> matches = matchingBlueprints(blueprintField.getText());
                if (matches.size() == 1) {
                    applyBlueprintSelection(matches.get(0));
                }
            }
        });

        ensureBlueprintPopup(base, fontSize);
    }

    private void ensureBlueprintPopup(Font base, int fontSize) {
        if (blueprintPopup != null) {
            return;
        }
        blueprintPopup = new JPopupMenu();
        blueprintPopup.setFocusable(false);
        blueprintPopup.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));

        blueprintSuggestList = new JList<>();
        blueprintSuggestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blueprintSuggestList.setFocusable(true);
        blueprintSuggestList.setFont(base.deriveFont(Font.PLAIN, fontSize));
        blueprintSuggestList.setForeground(EdoUi.User.MAIN_TEXT);
        blueprintSuggestList.setBackground(EdoUi.User.PANEL_BG);
        blueprintSuggestList.setSelectionForeground(EdoUi.User.MAIN_TEXT);
        blueprintSuggestList.setSelectionBackground(EdoUi.ED_ORANGE_LESS_TRANS);
        blueprintSuggestList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BlueprintOption opt) {
                    setText(opt.displayLabel());
                    setToolTipText(database.blueprintEffectTooltip(
                            opt.moduleType(), opt.blueprintName(), opt.maxGrade()));
                } else {
                    setToolTipText(null);
                }
                setForeground(EdoUi.User.MAIN_TEXT);
                setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
                return c;
            }
        });
        blueprintSuggestList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    applyBlueprintSelection(blueprintSuggestList.getSelectedValue());
                }
            }
        });

        JScrollPane sp = new JScrollPane(blueprintSuggestList);
        sp.setBorder(null);
        sp.setFocusable(false);
        OverlayScrollPaneSupport.installSubtleScrollBars(sp);
        blueprintPopup.add(sp);

        InputMap lim = blueprintSuggestList.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap lam = blueprintSuggestList.getActionMap();
        lim.put(KeyStroke.getKeyStroke("ENTER"), "blueprintAcAccept");
        lam.put("blueprintAcAccept", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyBlueprintSelection(blueprintSuggestList.getSelectedValue());
            }
        });
        lim.put(KeyStroke.getKeyStroke("ESCAPE"), "blueprintAcEscape");
        lam.put("blueprintAcEscape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hideBlueprintPopup();
                blueprintField.requestFocusInWindow();
                blueprintField.setCaretPosition(blueprintField.getText().length());
            }
        });
        lim.put(KeyStroke.getKeyStroke("UP"), "blueprintAcUp");
        lam.put("blueprintAcUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int idx = blueprintSuggestList.getSelectedIndex();
                if (idx > 0) {
                    blueprintSuggestList.setSelectedIndex(idx - 1);
                    blueprintSuggestList.ensureIndexIsVisible(idx - 1);
                } else {
                    String restore = originalBlueprintFieldText != null
                            ? originalBlueprintFieldText
                            : blueprintField.getText();
                    suppressBlueprintFieldEvents = true;
                    try {
                        blueprintField.setText(restore);
                    } finally {
                        suppressBlueprintFieldEvents = false;
                    }
                    hideBlueprintPopup();
                    blueprintField.requestFocusInWindow();
                    blueprintField.setCaretPosition(restore.length());
                    originalBlueprintFieldText = null;
                }
            }
        });
        lim.put(KeyStroke.getKeyStroke("DOWN"), "blueprintAcDownInList");
        lam.put("blueprintAcDownInList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int size = blueprintSuggestList.getModel().getSize();
                if (size == 0) {
                    return;
                }
                int idx = Math.max(0, blueprintSuggestList.getSelectedIndex());
                if (idx < size - 1) {
                    blueprintSuggestList.setSelectedIndex(idx + 1);
                    blueprintSuggestList.ensureIndexIsVisible(idx + 1);
                }
            }
        });
    }

    private void onBlueprintFieldTyping() {
        if (suppressBlueprintFieldEvents || applyingPrefill) {
            return;
        }
        if (!blueprintField.isFocusOwner()) {
            hideBlueprintPopup();
            return;
        }
        // Typing clears a prior pick until they choose again from the popup.
        if (selectedBlueprint != null) {
            String typed = blueprintField.getText().trim();
            if (!typed.equalsIgnoreCase(selectedBlueprint.fieldText())
                    && !typed.equalsIgnoreCase(selectedBlueprint.displayLabel())) {
                selectedBlueprint = null;
                blueprintSummaryLabel.setText(" ");
                updateSelectionDetails();
            }
        }
        refreshBlueprintSuggestions();
    }

    private void refreshBlueprintSuggestions() {
        if (mode != Mode.ADD || applyingPrefill || suppressBlueprintFieldEvents) {
            return;
        }
        if (!blueprintField.isFocusOwner()
                && (blueprintSuggestList == null || !blueprintSuggestList.isFocusOwner())) {
            return;
        }
        String q = blueprintField.getText().trim();
        if (q.length() < BLUEPRINT_AC_MIN_CHARS) {
            hideBlueprintPopup();
            return;
        }
        List<BlueprintOption> matches = matchingBlueprints(q);
        if (matches.isEmpty()) {
            hideBlueprintPopup();
            return;
        }
        ensureBlueprintPopup(
                OverlayPreferences.getUiFont() != null ? OverlayPreferences.getUiFont() : getFont(),
                OverlayPreferences.getUiFontSize());
        blueprintSuggestList.setListData(matches.toArray(BlueprintOption[]::new));
        blueprintSuggestList.setVisibleRowCount(Math.min(BLUEPRINT_AC_VISIBLE_ROWS, matches.size()));
        int width = Math.max(blueprintField.getWidth(), 320);
        int listHeight = blueprintSuggestList.getPreferredScrollableViewportSize().height;
        if (listHeight <= 0) {
            listHeight = Math.max(120, BLUEPRINT_AC_VISIBLE_ROWS * (OverlayPreferences.getUiFontSize() + 10));
        }
        blueprintPopup.setPopupSize(width, listHeight + 4);
        if (!blueprintPopup.isVisible()) {
            blueprintPopup.show(blueprintField, 0, blueprintField.getHeight());
        } else {
            // Keep open while filtering; reposition if needed.
            Rectangle bounds = blueprintField.getBounds();
            blueprintPopup.setLocation(
                    blueprintField.getLocationOnScreen().x,
                    blueprintField.getLocationOnScreen().y + bounds.height);
        }
        if (blueprintField.isFocusOwner()) {
            blueprintField.setCaretPosition(blueprintField.getText().length());
        }
    }

    private void hideBlueprintPopup() {
        if (blueprintPopup != null && blueprintPopup.isVisible()) {
            blueprintPopup.setVisible(false);
        }
    }

    private void applyBlueprintSelection(BlueprintOption option) {
        if (option == null) {
            return;
        }
        hideBlueprintPopup();
        suppressBlueprintFieldEvents = true;
        try {
            selectedBlueprint = option;
            blueprintField.setText(option.fieldText());
            blueprintField.setCaretPosition(blueprintField.getText().length());
            blueprintSummaryLabel.setText(option.moduleType() + ": " + option.blueprintName());
        } finally {
            suppressBlueprintFieldEvents = false;
        }
        updateSelectionDetails();
        selectPreferredOrMaxGrade();
        blueprintField.requestFocusInWindow();
    }

    private List<BlueprintOption> matchingBlueprints(String query) {
        String q = query != null ? query.trim() : "";
        List<BlueprintOption> out = new ArrayList<>();
        for (BlueprintOption opt : blueprintCatalog) {
            if (opt == null) {
                continue;
            }
            if (q.isEmpty()
                    || EngineeringJournalBlueprintResolver.matchesModuleSearch(
                            q, opt.moduleType(), opt.blueprintName())) {
                out.add(opt);
            }
        }
        out.sort(Comparator
                .comparing(BlueprintOption::moduleType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BlueprintOption::blueprintName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void reloadBlueprintCatalog() {
        Set<String> installedTypes = null;
        if (mode == Mode.ADD && installedOnlyCheck.isSelected()) {
            installedTypes = installedModuleTypesForSelectedShip();
        }
        Map<String, BlueprintOption> unique = new LinkedHashMap<>();
        for (BlueprintGrade bp : database.getAllBlueprints()) {
            if (bp.isExperimental()) {
                continue;
            }
            if (installedTypes != null && !moduleTypeMatchesInstalled(bp.getModuleType(), installedTypes)) {
                continue;
            }
            String key = EngineeringDatabase.groupKey(bp.getModuleType(), bp.getName());
            BlueprintOption existing = unique.get(key);
            if (existing == null) {
                unique.put(key, new BlueprintOption(bp.getModuleType(), bp.getName(), bp.getGrade()));
            } else {
                unique.put(key, existing.withMaxGrade(Math.max(existing.maxGrade(), bp.getGrade())));
            }
        }
        List<BlueprintOption> rows = new ArrayList<>(unique.values());
        rows.sort(Comparator
                .comparing(BlueprintOption::moduleType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BlueprintOption::blueprintName, String.CASE_INSENSITIVE_ORDER));
        blueprintCatalog = List.copyOf(rows);
        if (selectedBlueprint != null) {
            BlueprintOption keep = findCatalogOption(selectedBlueprint.moduleType(), selectedBlueprint.blueprintName());
            if (keep == null) {
                selectedBlueprint = null;
                blueprintSummaryLabel.setText(" ");
                updateSelectionDetails();
            } else {
                selectedBlueprint = keep;
            }
        }
    }

    private BlueprintOption findCatalogOption(String moduleType, String blueprintName) {
        for (BlueprintOption opt : blueprintCatalog) {
            if (opt.moduleType().equals(moduleType) && opt.blueprintName().equals(blueprintName)) {
                return opt;
            }
        }
        // Tolerant module-type match when catalog naming differs slightly.
        for (BlueprintOption opt : blueprintCatalog) {
            if (EngineeringJournalBlueprintResolver.sameModuleType(opt.moduleType(), moduleType)
                    && EngineeringJournalBlueprintResolver.normalizeToken(opt.blueprintName())
                            .equals(EngineeringJournalBlueprintResolver.normalizeToken(blueprintName))) {
                return opt;
            }
        }
        return null;
    }

    /**
     * Module types fitted on the currently selected ship (from the latest stored loadout), used by
     * {@code Installed only}.
     */
    private Set<String> installedModuleTypesForSelectedShip() {
        EngineeringShipRef ship = (EngineeringShipRef) shipCombo.getSelectedItem();
        if (ship == null || !ship.isKnown()) {
            return Set.of();
        }
        String clientKey = EliteDangerousOverlay.clientKey;
        if (clientKey == null || clientKey.isBlank()) {
            return Set.of();
        }
        LoadoutEvent loadout = EngineeringCraftStore.loadLatestLoadouts(clientKey).get(Long.valueOf(ship.getShipId()));
        if (loadout == null) {
            return Set.of();
        }
        Set<String> types = new HashSet<>();
        for (LoadoutEvent.Module module : loadout.getModules()) {
            if (module == null || module.getItem() == null || module.getItem().isBlank()) {
                continue;
            }
            String type = EngineeringJournalBlueprintResolver.moduleItemToModuleType(module.getItem());
            if (!type.isBlank()) {
                types.add(EngineeringJournalBlueprintResolver.normalizeToken(type));
            }
        }
        return types;
    }

    private static boolean moduleTypeMatchesInstalled(String moduleType, Set<String> installedTypesNormalized) {
        if (moduleType == null || moduleType.isBlank()
                || installedTypesNormalized == null || installedTypesNormalized.isEmpty()) {
            return false;
        }
        return installedTypesNormalized.contains(EngineeringJournalBlueprintResolver.normalizeToken(moduleType));
    }

    private void selectOption(BlueprintOption wanted) {
        if (wanted == null) {
            selectedBlueprint = null;
            blueprintSummaryLabel.setText(" ");
            updateSelectionDetails();
            return;
        }
        BlueprintOption match = findCatalogOption(wanted.moduleType(), wanted.blueprintName());
        if (match == null && !wanted.moduleType().isBlank() && wanted.blueprintName().isBlank()) {
            for (BlueprintOption opt : blueprintCatalog) {
                if (EngineeringJournalBlueprintResolver.sameModuleType(wanted.moduleType(), opt.moduleType())) {
                    match = opt;
                    break;
                }
            }
        }
        if (match == null) {
            selectedBlueprint = null;
            blueprintSummaryLabel.setText(" ");
            updateSelectionDetails();
            return;
        }
        suppressBlueprintFieldEvents = true;
        try {
            selectedBlueprint = match;
            blueprintField.setText(match.fieldText());
            blueprintSummaryLabel.setText(match.moduleType() + ": " + match.blueprintName());
        } finally {
            suppressBlueprintFieldEvents = false;
        }
        updateSelectionDetails();
    }

    /** Prefer search text, blueprint, quantity, and experimental from {@link #addPrefill}. */
    private void applyAddPrefill() {
        if (addPrefill.moduleType().isBlank()
                && addPrefill.blueprintName().isBlank()
                && addPrefill.searchText().isBlank()
                && addPrefill.experimentalName().isBlank()
                && addPrefill.quantity() <= 1
                && addPrefill.preferredTargetGrade() <= 0) {
            return;
        }
        applyingPrefill = true;
        try {
            if (!addPrefill.moduleType().isBlank() && !addPrefill.blueprintName().isBlank()) {
                selectOption(new BlueprintOption(addPrefill.moduleType(), addPrefill.blueprintName(), 1));
            } else if (!addPrefill.moduleType().isBlank()) {
                selectOption(new BlueprintOption(addPrefill.moduleType(), "", 1));
            } else if (!addPrefill.searchText().isBlank()) {
                suppressBlueprintFieldEvents = true;
                try {
                    blueprintField.setText(addPrefill.searchText());
                } finally {
                    suppressBlueprintFieldEvents = false;
                }
                List<BlueprintOption> matches = matchingBlueprints(addPrefill.searchText());
                if (!matches.isEmpty()) {
                    selectOption(matches.get(0));
                }
            }
            selectPreferredOrMaxGrade();
            if (addPrefill.quantity() > 1) {
                quantitySpinner.setValue(Integer.valueOf(addPrefill.quantity()));
            }
            if (!addPrefill.experimentalName().isBlank()
                    && !"(none)".equalsIgnoreCase(addPrefill.experimentalName())) {
                selectExperimentalFuzzy(addPrefill.experimentalName());
            }
            if (!addPrefill.searchText().isBlank() && selectedBlueprint == null) {
                suppressBlueprintFieldEvents = true;
                try {
                    blueprintField.setText(addPrefill.searchText());
                } finally {
                    suppressBlueprintFieldEvents = false;
                }
            }
        } finally {
            applyingPrefill = false;
        }
        SwingUtilities.invokeLater(() -> {
            selectPreferredOrMaxGrade();
            if (!addPrefill.experimentalName().isBlank()
                    && !"(none)".equalsIgnoreCase(addPrefill.experimentalName())) {
                selectExperimentalFuzzy(addPrefill.experimentalName());
            }
            equalizeGradeQtyWidths();
        });
    }

    /** Default target grade to the blueprint max (or an explicit prefill grade). */
    private void selectPreferredOrMaxGrade() {
        BlueprintOption selected = selectedOption();
        if (selected == null || gradeCombo.getItemCount() <= 0) {
            return;
        }
        int grade = addPrefill.preferredTargetGrade() > 0
                ? Math.min(addPrefill.preferredTargetGrade(), selected.maxGrade())
                : selected.maxGrade();
        grade = Math.max(1, grade);
        gradeCombo.setSelectedItem("G" + grade);
    }

    private BlueprintOption selectedOption() {
        return selectedBlueprint;
    }

    private void updateSelectionDetails() {
        BlueprintOption selected = selectedOption();
        gradeCombo.removeAllItems();
        experimentalCombo.removeAllItems();
        experimentalCombo.addItem("(none)");
        if (selected == null) {
            effectsLabel.setText(" ");
            materialsLabel.setText(" ");
            syncExperimentalComboTooltip();
            equalizeGradeQtyWidths();
            return;
        }
        for (int g = 1; g <= selected.maxGrade(); g++) {
            gradeCombo.addItem("G" + g);
        }
        gradeCombo.setSelectedItem("G" + selected.maxGrade());
        updateGradeDetails();

        for (BlueprintGrade exp : database.experimentalsFor(selected.moduleType(), selected.blueprintName())) {
            experimentalCombo.addItem(exp.getName());
        }
        syncExperimentalComboTooltip();
        equalizeGradeQtyWidths();
    }

    private void installExperimentalEffectTooltips() {
        experimentalCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setForeground(EdoUi.User.MAIN_TEXT);
                setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
                String name = value != null ? value.toString() : "";
                BlueprintOption selected = selectedOption();
                setToolTipText(selected != null
                        ? database.experimentalEffectTooltip(
                                selected.moduleType(), selected.blueprintName(), name)
                        : (mode == Mode.EDIT && editSource != null
                                ? database.experimentalEffectTooltip(
                                        editSource.getModuleType(),
                                        editSource.getBlueprintName(),
                                        name)
                                : null));
                return c;
            }
        });
        experimentalCombo.addActionListener(e -> syncExperimentalComboTooltip());
    }

    private void syncExperimentalComboTooltip() {
        String name = experimentalCombo.getSelectedItem() != null
                ? experimentalCombo.getSelectedItem().toString()
                : "";
        BlueprintOption selected = selectedOption();
        if (selected != null) {
            experimentalCombo.setToolTipText(database.experimentalEffectTooltip(
                    selected.moduleType(), selected.blueprintName(), name));
        } else if (mode == Mode.EDIT && editSource != null) {
            experimentalCombo.setToolTipText(database.experimentalEffectTooltip(
                    editSource.getModuleType(), editSource.getBlueprintName(), name));
        } else {
            experimentalCombo.setToolTipText(null);
        }
    }

    private void updateGradeDetails() {
        BlueprintOption selected = selectedOption();
        if (selected == null) {
            return;
        }
        int targetGrade = selectedTargetGrade(selected);
        BlueprintGrade preview = database.gradesFor(selected.moduleType(), selected.blueprintName()).stream()
                .filter(g -> g.getGrade() == targetGrade)
                .findFirst()
                .orElse(null);
        if (preview != null) {
            effectsLabel.setText("<html><body style='color:" + EdoUi.htmlHex(EdoUi.Internal.EMPTY_STATE_INK)
                    + "; width:420px'>G"
                    + preview.getGrade() + " effects: " + htmlEscape(preview.modifierSummary()) + "</body></html>");
        }
        materialsLabel.setText(buildMaterialsHtml(selected, targetGrade));
    }

    private int selectedTargetGrade(BlueprintOption selected) {
        Object gradeSel = gradeCombo.getSelectedItem();
        if (gradeSel != null) {
            return Integer.parseInt(gradeSel.toString().substring(1));
        }
        return selected.maxGrade();
    }

    private String buildMaterialsHtml(BlueprintOption selected, int targetGrade) {
        Map<String, Integer> required = new LinkedHashMap<>();
        for (BlueprintGrade grade : database.gradesFor(selected.moduleType(), selected.blueprintName())) {
            if (grade.isExperimental()) {
                continue;
            }
            int g = grade.getGrade();
            if (g < 1 || g > targetGrade) {
                continue;
            }
            int rolls = EngineeringGradeProgress.ROLLS_PER_GRADE;
            for (MaterialRequirement mat : grade.getMaterials()) {
                required.merge(EngineeringMaterialKeys.canonicalKey(mat.getKey()), mat.getCount() * rolls, Integer::sum);
            }
        }
        if (required.isEmpty()) {
            return "<html><body style='color:" + EdoUi.htmlHex(EdoUi.Internal.EMPTY_STATE_INK)
                    + "'>No materials listed for this grade range.</body></html>";
        }
        StringBuilder sb = new StringBuilder("<html><body style='color:"
                + EdoUi.htmlHex(EdoUi.Internal.EMPTY_STATE_INK) + "; width:420px'>Materials (G1–G")
                .append(targetGrade).append("): ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getValue()).append("× ").append(htmlEscape(database.materialDisplayName(e.getKey())));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void confirmSave() {
        BlueprintOption selected = mode == Mode.EDIT
                ? new BlueprintOption(editSource.getModuleType(), editSource.getBlueprintName(), editSource.getTargetGrade())
                : selectedOption();
        if (selected == null) {
            if (mode == Mode.ADD) {
                effectsLabel.setText("<html><body style='color:" + EdoUi.htmlHex(EdoUi.Internal.EMPTY_STATE_WARN)
                        + "'>Pick a blueprint from the suggestions.</body></html>");
            }
            return;
        }
        Object gradeSel = gradeCombo.getSelectedItem();
        if (gradeSel == null) {
            return;
        }
        int targetGrade = Integer.parseInt(gradeSel.toString().substring(1));
        int quantity = ((Number) quantitySpinner.getValue()).intValue();
        if (quantity <= 0) {
            if (mode == Mode.EDIT) {
                promptDeleteGoal();
            } else {
                effectsLabel.setText("<html><body style='color:" + EdoUi.htmlHex(EdoUi.Internal.EMPTY_STATE_WARN)
                        + "'>Quantity must be at least 1.</body></html>");
            }
            return;
        }
        String experimentalId = resolveExperimentalId(selected.moduleType(), selected.blueprintName());
        EngineeringShipRef ship = (EngineeringShipRef) shipCombo.getSelectedItem();
        long shipId = ship != null && ship.isKnown() ? ship.getShipId() : EngineeringShipRef.UNKNOWN_SHIP_ID;
        String shipLabel = "";
        if (ship != null) {
            shipLabel = shipCatalog != null
                    ? shipCatalog.displayLabel(ship)
                    : EngineeringShipRef.displayLabelAmong(ship, shipChoices);
        }
        if (mode == Mode.EDIT) {
            result = editSource.withUserSettings(targetGrade, experimentalId, quantity, shipId, shipLabel);
            dispose();
            return;
        }
        BlueprintGrade bp = database.gradesFor(selected.moduleType(), selected.blueprintName()).stream()
                .filter(g -> g.getGrade() == targetGrade)
                .findFirst()
                .orElse(null);
        if (bp == null) {
            return;
        }
        result = new EngineeringGoal(
                bp.getId(),
                bp.getModuleType(),
                bp.getName(),
                0,
                0,
                targetGrade,
                experimentalId,
                true,
                false,
                quantity,
                0,
                shipId,
                shipLabel);
        dispose();
    }

    /** Quantity 0 on Edit → confirm, then signal deletion to the caller. */
    private void promptDeleteGoal() {
        String label = editSource != null ? editSource.displayLabel() : "this goal";
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Quantity is 0. Delete engineering goal?\n\n" + label,
                "Delete goal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            deleted = true;
            result = null;
            dispose();
        }
    }

    private String resolveExperimentalId(String moduleType, String blueprintName) {
        Object expSel = experimentalCombo.getSelectedItem();
        if (expSel == null || "(none)".equals(expSel.toString())) {
            return "";
        }
        for (BlueprintGrade e : database.experimentalsFor(moduleType, blueprintName)) {
            if (e.getName().equals(expSel.toString())) {
                return e.getId();
            }
        }
        return "";
    }

    /**
     * Select an experimental by catalog name / journal label / id token.
     * Handles localization quirks like fitted {@code Super Capacitors} vs catalog {@code Super Capacitor}.
     */
    private void selectExperimentalFuzzy(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank() || "(none)".equalsIgnoreCase(nameOrId.trim())) {
            experimentalCombo.setSelectedItem("(none)");
            return;
        }
        String raw = nameOrId.trim();
        for (int i = 0; i < experimentalCombo.getItemCount(); i++) {
            String item = experimentalCombo.getItemAt(i);
            if (item != null && item.equalsIgnoreCase(raw)) {
                experimentalCombo.setSelectedIndex(i);
                return;
            }
        }
        String want = EngineeringJournalBlueprintResolver.normalizeToken(raw);
        if (want.isEmpty()) {
            experimentalCombo.setSelectedItem("(none)");
            return;
        }
        String best = null;
        int bestScore = 0;
        for (int i = 0; i < experimentalCombo.getItemCount(); i++) {
            String item = experimentalCombo.getItemAt(i);
            if (item == null || "(none)".equals(item)) {
                continue;
            }
            String n = EngineeringJournalBlueprintResolver.normalizeToken(item);
            int score = 0;
            if (n.equals(want)) {
                score = 100;
            } else if (n.startsWith(want) || want.startsWith(n)) {
                score = 90;
            } else if (want.length() >= 4 && (n.contains(want) || want.contains(n))) {
                score = 80;
            }
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        if (best != null && bestScore >= 80) {
            experimentalCombo.setSelectedItem(best);
        } else {
            experimentalCombo.setSelectedItem("(none)");
        }
    }

    private void confirmAdd() {
        confirmSave();
    }

    private static JLabel fieldLabel(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.PLAIN, fontSize));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        return label;
    }

    private static JLabel sectionHeader(String text, Font base, int fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(base.deriveFont(Font.BOLD, fontSize + 1));
        label.setForeground(EdoUi.User.MAIN_TEXT);
        label.setBorder(new EmptyBorder(4, 0, 2, 0));
        return label;
    }

    private static void styleTextField(JTextField field, Font base) {
        field.setFont(base);
        field.setForeground(EdoUi.User.MAIN_TEXT);
        field.setBackground(EdoUi.User.PANEL_BG);
        field.setCaretColor(EdoUi.User.MAIN_TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140),
                new EmptyBorder(3, 6, 3, 6)));
    }

    private static void styleCombo(JComboBox<?> combo, Font base) {
        combo.setFont(base);
        combo.setForeground(EdoUi.User.MAIN_TEXT);
        combo.setBackground(EdoUi.User.PANEL_BG);
        combo.setMaximumRowCount(24);
        OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
    }

    private void styleQuantitySpinner(Font base) {
        quantitySpinner.setFont(base);
        quantitySpinner.setForeground(EdoUi.User.MAIN_TEXT);
        quantitySpinner.setBackground(EdoUi.User.PANEL_BG);
        quantitySpinner.setOpaque(true);
        JComponent editor = quantitySpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            field.setFont(base);
            field.setForeground(EdoUi.User.MAIN_TEXT);
            field.setBackground(EdoUi.User.PANEL_BG);
            field.setCaretColor(EdoUi.User.MAIN_TEXT);
            field.setHorizontalAlignment(SwingConstants.LEFT);
            field.setOpaque(true);
            field.setBorder(new EmptyBorder(2, 6, 2, 4));
        }
        Dimension pref = quantitySpinner.getPreferredSize();
        quantitySpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    /**
     * Keep target-grade and quantity the same width. Experimental stays free to fill the field column
     * so long names are not ellipsized.
     */
    private void equalizeGradeQtyWidths() {
        gradeCombo.setPreferredSize(null);
        quantitySpinner.setPreferredSize(null);
        Dimension g = gradeCombo.getPreferredSize();
        Dimension q = quantitySpinner.getPreferredSize();
        int width = Math.max(g.width, q.width);
        int height = Math.max(g.height, q.height);
        Dimension size = new Dimension(width, height);
        gradeCombo.setPreferredSize(size);
        quantitySpinner.setPreferredSize(size);
        gradeCombo.setMinimumSize(size);
        quantitySpinner.setMinimumSize(size);
        gradeCombo.revalidate();
        quantitySpinner.revalidate();
        experimentalCombo.setPreferredSize(null);
        experimentalCombo.revalidate();
    }

    private static void constrainComboHeight(JComboBox<?> combo) {
        Dimension pref = combo.getPreferredSize();
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    }

    private static String htmlEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private record BlueprintOption(String moduleType, String blueprintName, int maxGrade) {
        BlueprintOption withMaxGrade(int grade) {
            return new BlueprintOption(moduleType, blueprintName, grade);
        }

        String displayLabel() {
            return moduleType + " · " + blueprintName + "  G1–G" + maxGrade;
        }

        String fieldText() {
            return moduleType + ": " + blueprintName;
        }
    }
}
