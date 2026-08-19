package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringCraftStore;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringGoalProgress.ModuleUnitProgress;
import org.dce.ed.engineering.EngineeringGoalSlotMatcher;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringJournalBlueprintResolver;
import org.dce.ed.engineering.EngineeringShipCatalog;
import org.dce.ed.engineering.EngineeringShipRef;
import org.dce.ed.engineering.ShipEngineeringSummary;
import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;
import org.dce.ed.logreader.event.LoadoutEvent;
import org.dce.ed.ui.AlwaysOnTopPopupFactory;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.SystemTableHoverCopyManager;

/**
 * Ship Loadout hub: glanceable Gap / Partial / Done engineering status with add-goal actions
 * and a concise clipboard copy.
 */
final class EngineeringBuildProgressDialog extends JDialog {

	private static final int HOVER_CLICK_DELAY_MS = 500;
	private static final String CORIOLIS_IMPORT_URL = "https://coriolis.io/import";
	private static final String ENGINEERING_RECOMMENDATIONS_URL =
			"https://github.com/deccles/Elite-Dangerous-RockHound/blob/main/docs/engineering-recommendations.md";

	private final EngineeringDatabase database;
	private final BooleanSupplier passThroughEnabledSupplier;
	private final EngineeringShipCatalog shipCatalog;
	private final Long initialShipFilterId;
	private final JComboBox<ShipFilterItem> shipCombo;
	private final JLabel countsLabel;
	private final JPanel contentPanel;
	private final JScrollPane contentScroll;
	private final Font baseFont;
	private final int fontSize;
	private boolean shipComboReady;

	private List<ModuleUnitProgress> allUnits = List.of();
	private Map<Long, LoadoutEvent> loadoutsByShip = Map.of();
	private ShipEngineeringSummary currentSummary = ShipEngineeringSummary.fromLoadout(null, null);
	private Long lastSelectedShipFilterId;
	private SwingWorker<?, ?> loadWorker;
	private final Function<AddGoalRequest, EngineeringGoal> addGoalHandler;
	private final Supplier<List<EngineeringGoal>> goalsSupplier;
	private String clientKey = "";
	private int pendingRestoreScrollY = -1;

	private EngineeringBuildProgressDialog(Window owner,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			BooleanSupplier passThroughEnabledSupplier,
			Function<AddGoalRequest, EngineeringGoal> addGoalHandler,
			Supplier<List<EngineeringGoal>> goalsSupplier) {
		super(owner, "Loadout", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.database = database;
		this.passThroughEnabledSupplier = passThroughEnabledSupplier;
		this.shipCatalog = shipCatalog != null ? shipCatalog : new EngineeringShipCatalog();
		this.initialShipFilterId = initialShipFilterId;
		this.addGoalHandler = addGoalHandler;
		this.goalsSupplier = goalsSupplier != null ? goalsSupplier : List::of;
		this.baseFont = OverlayPreferences.getUiFont() != null ? OverlayPreferences.getUiFont() : getFont();
		this.fontSize = OverlayPreferences.getUiFontSize();

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(12, 14, 12, 14));
		root.setBackground(EdoUi.User.BACKGROUND);
		root.setOpaque(true);

		JPanel north = new JPanel(new BorderLayout(8, 6));
		north.setOpaque(false);
		JLabel title = new JLabel("Loadout");
		title.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 2));
		title.setForeground(EdoUi.User.MAIN_TEXT);
		north.add(title, BorderLayout.NORTH);

		JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		filterRow.setOpaque(false);
		JLabel shipLbl = new JLabel("Ship:");
		shipLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		shipLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		filterRow.add(shipLbl);
		shipCombo = new JComboBox<>();
		styleShipCombo(shipCombo);
		shipComboReady = false;
		shipCombo.addActionListener(e -> {
			if (!shipComboReady) {
				return;
			}
			ShipFilterItem item = (ShipFilterItem) shipCombo.getSelectedItem();
			if (item == null || item.isSeparator() || item.shipId() == null) {
				Long prefer = lastSelectedShipFilterId != null
						? lastSelectedShipFilterId
						: initialShipFilterId;
				ShipFilterItem fallback = findShipFilterItem(prefer);
				if (fallback != null) {
					shipCombo.setSelectedItem(fallback);
				}
				return;
			}
			lastSelectedShipFilterId = item.shipId();
			rebuildContent();
		});
		filterRow.add(shipCombo);
		north.add(filterRow, BorderLayout.CENTER);

		countsLabel = new JLabel(" ");
		countsLabel.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		countsLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		north.add(countsLabel, BorderLayout.SOUTH);

		JPanel northWithRule = new JPanel(new BorderLayout());
		northWithRule.setOpaque(false);
		northWithRule.add(north, BorderLayout.NORTH);
		northWithRule.add(sectionSeparator(), BorderLayout.SOUTH);
		root.add(northWithRule, BorderLayout.NORTH);

		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setOpaque(false);
		contentPanel.setBorder(new EmptyBorder(4, 0, 8, 0));
		showLoading();

		contentScroll = new JScrollPane(contentPanel);
		contentScroll.setBorder(BorderFactory.createEmptyBorder());
		contentScroll.getViewport().setOpaque(false);
		contentScroll.setOpaque(false);
		contentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		OverlayScrollPaneSupport.installSubtleScrollBars(contentScroll);
		root.add(contentScroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);

		JButton viewSummaryBtn = new JButton("View Summary");
		OverlayOutlineButtonStyle.applyChip(viewSummaryBtn, baseFont, false);
		viewSummaryBtn.setToolTipText("View Gap / Partial / Done summary (with copy)");
		Runnable viewSummaryAction = this::showSummaryDialog;
		viewSummaryBtn.addActionListener(e -> viewSummaryAction.run());
		HoverClickPoller.register(viewSummaryBtn, HOVER_CLICK_DELAY_MS, viewSummaryAction, passThroughEnabledSupplier);
		south.add(viewSummaryBtn);

		JButton coriolisBtn = new JButton("Coriolis");
		OverlayOutlineButtonStyle.applyChip(coriolisBtn, baseFont, false);
		Runnable coriolisAction = this::copyLoadoutJsonAndOpenCoriolis;
		coriolisBtn.addActionListener(e -> coriolisAction.run());
		HoverClickPoller.register(coriolisBtn, HOVER_CLICK_DELAY_MS, coriolisAction, passThroughEnabledSupplier);
		south.add(coriolisBtn);

		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, baseFont, false);
		closeBtn.addActionListener(e -> dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);

		JPanel southWithRule = new JPanel(new BorderLayout());
		southWithRule.setOpaque(false);
		southWithRule.add(sectionSeparator(), BorderLayout.NORTH);
		southWithRule.add(south, BorderLayout.SOUTH);
		root.add(southWithRule, BorderLayout.SOUTH);

		setContentPane(root);
		int dialogW = preferredDialogWidth();
		int dialogH = preferredDialogHeight();
		setMinimumSize(new Dimension(minimumDialogWidth(), 520));
		setPreferredSize(new Dimension(dialogW, dialogH));
		pack();
		setSize(dialogW, dialogH);
		setLocationRelativeTo(owner);
		setAlwaysOnTop(true);
		// Heavyweight tooltips otherwise appear behind this always-on-top dialog.
		AlwaysOnTopPopupFactory.installWhileShowing(this);
	}

	/** Fixed Component / Size / Blueprint / Experimental / Level / Action columns + gutters. */
	private int fixedColumnsWidth() {
		return FIXED_COMPONENT_COL_W
				+ FIXED_SLOT_SIZE_COL_W
				+ FIXED_BLUEPRINT_COMBO_W
				+ FIXED_EXPERIMENTAL_COMBO_W
				+ levelColumnWidth()
				+ actionColumnWidth()
				+ (8 * 5);
	}

	private int minimumDialogWidth() {
		return fixedColumnsWidth() + 56;
	}

	private int preferredDialogWidth() {
		return Math.max(1020, fixedColumnsWidth() + 72);
	}

	private int preferredDialogHeight() {
		return 960;
	}

	static void show(Window owner,
			List<EngineeringGoal> goals,
			EngineeringShipCatalog shipCatalog,
			Long initialShipFilterId,
			EngineeringDatabase database,
			String clientKey,
			BooleanSupplier passThroughEnabledSupplier,
			Function<AddGoalRequest, EngineeringGoal> addGoalHandler,
			Supplier<List<EngineeringGoal>> goalsSupplier) {
		EngineeringBuildProgressDialog dialog = new EngineeringBuildProgressDialog(
				owner, shipCatalog, initialShipFilterId, database, passThroughEnabledSupplier,
				addGoalHandler, goalsSupplier);
		dialog.setVisible(true);
		dialog.startLoad(goals, clientKey);
	}

	private void startLoad(List<EngineeringGoal> goals, String clientKey) {
		startLoad(goals, clientKey, false);
	}

	private void startLoad(List<EngineeringGoal> goals, String clientKey, boolean preserveScroll) {
		List<EngineeringGoal> goalSnapshot = goals != null ? List.copyOf(goals) : List.of();
		String key = clientKey != null ? clientKey : "";
		this.clientKey = key;
		EngineeringDatabase db = database;
		EngineeringShipCatalog catalog = shipCatalog;
		final boolean keepScroll = preserveScroll;
		if (keepScroll) {
			pendingRestoreScrollY = contentScroll.getVerticalScrollBar().getValue();
		} else {
			pendingRestoreScrollY = -1;
		}

		if (loadWorker != null) {
			loadWorker.cancel(true);
		}
		loadWorker = new SwingWorker<DialogLoadResult, Void>() {
			@Override
			protected DialogLoadResult doInBackground() {
				if (!EngineeringCraftStore.hasCrafts(key)
						&& EngineeringCraftStore.loadLatestLoadouts(key).isEmpty()) {
					EngineeringCraftStore.reparseFromJournal(key);
				}
				List<ModuleUnitProgress> units =
						EngineeringGoalProgress.collectModuleUnitProgress(goalSnapshot, key, db);
				Map<Long, LoadoutEvent> loadouts = EngineeringCraftStore.loadLatestLoadouts(key);
				if (catalog != null) {
					for (ModuleUnitProgress u : units) {
						if (u.shipId() >= 0 && catalog.get(u.shipId()) == null) {
							catalog.remember(new EngineeringShipRef(u.shipId(), "", u.shipLabel(), ""));
						}
					}
					for (Map.Entry<Long, LoadoutEvent> e : loadouts.entrySet()) {
						LoadoutEvent loadout = e.getValue();
						if (loadout != null && catalog.get(e.getKey()) == null) {
							catalog.remember(new EngineeringShipRef(
									e.getKey(),
									loadout.getShip() != null ? loadout.getShip() : "",
									loadout.getShipName() != null ? loadout.getShipName() : "",
									loadout.getShipIdent() != null ? loadout.getShipIdent() : ""));
						}
					}
				}
				return new DialogLoadResult(units, loadouts);
			}

			@Override
			protected void done() {
				if (isCancelled() || !isDisplayable()) {
					return;
				}
				try {
					DialogLoadResult result = get();
					allUnits = result.units();
					loadoutsByShip = result.loadoutsByShip();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					allUnits = List.of();
					loadoutsByShip = Map.of();
				} catch (ExecutionException ex) {
					allUnits = List.of();
					loadoutsByShip = Map.of();
				}
				populateShipCombo(keepScroll ? null : initialShipFilterId);
				rebuildContent();
				if (keepScroll && pendingRestoreScrollY >= 0) {
					final int y = pendingRestoreScrollY;
					pendingRestoreScrollY = -1;
					SwingUtilities.invokeLater(() -> {
						JScrollBar bar = contentScroll.getVerticalScrollBar();
						bar.setValue(Math.min(y, bar.getMaximum()));
					});
				}
			}
		};
		loadWorker.execute();
	}

	private void showLoading() {
		contentPanel.removeAll();
		countsLabel.setText(" ");
		JLabel loading = mutedLabel("Loading loadout…");
		loading.setAlignmentX(Component.LEFT_ALIGNMENT);
		contentPanel.add(loading);
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private void populateShipCombo(Long preferShipId) {
		shipComboReady = false;
		shipCombo.removeAllItems();
		Map<Long, String> labels = new LinkedHashMap<>();
		for (Long id : loadoutsByShip.keySet()) {
			labels.put(id, resolveShipTitle(id.longValue()));
		}
		for (ModuleUnitProgress u : allUnits) {
			if (u.shipId() >= 0) {
				labels.putIfAbsent(Long.valueOf(u.shipId()), resolveShipTitle(u.shipId()));
			}
		}
		List<Long> ordered = new ArrayList<>(labels.keySet());
		ordered.sort(Comparator.comparing(labels::get, String.CASE_INSENSITIVE_ORDER));
		for (Long id : ordered) {
			shipCombo.addItem(ShipFilterItem.ship(id.longValue(), labels.get(id)));
		}
		Long prefer = preferShipId != null ? preferShipId : lastSelectedShipFilterId;
		ShipFilterItem select = findShipFilterItem(prefer);
		if (select == null && shipCombo.getItemCount() > 0) {
			select = shipCombo.getItemAt(0);
		}
		if (select != null) {
			shipCombo.setSelectedItem(select);
			lastSelectedShipFilterId = select.shipId();
		}
		shipComboReady = true;
	}

	private ShipFilterItem findShipFilterItem(Long shipId) {
		if (shipId == null) {
			return null;
		}
		for (int i = 0; i < shipCombo.getItemCount(); i++) {
			ShipFilterItem item = shipCombo.getItemAt(i);
			if (item != null && !item.isSeparator() && shipId.equals(item.shipId())) {
				return item;
			}
		}
		return null;
	}

	private void rebuildContent() {
		contentPanel.removeAll();
		ShipFilterItem filter = (ShipFilterItem) shipCombo.getSelectedItem();
		Long shipFilterId = filter != null && filter.shipId() != null ? filter.shipId() : null;
		if (shipFilterId == null) {
			countsLabel.setText(" ");
			JLabel empty = mutedLabel("Select a ship to view its loadout.");
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		LoadoutEvent loadout = loadoutsByShip.get(shipFilterId);
		if (loadout == null) {
			countsLabel.setText(" ");
			JLabel empty = mutedLabel(
					"No stored loadout for this ship yet. Board it in-game (or change a module) to record one.");
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, database);
		currentSummary = summary;
		List<Row> visible = summary.rows();
		countsLabel.setText(summary.countsLine());

		if (visible.isEmpty()) {
			JLabel empty = mutedLabel("No engineerable modules on this loadout.");
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			contentPanel.add(empty);
			contentPanel.revalidate();
			contentPanel.repaint();
			return;
		}

		boolean firstBand = true;
		for (Band band : List.of(Band.GAP, Band.PARTIAL, Band.DONE)) {
			List<Row> section = new ArrayList<>();
			for (Row row : visible) {
				if (row.band() == band) {
					section.add(row);
				}
			}
			if (section.isEmpty()) {
				continue;
			}
			if (!firstBand) {
				contentPanel.add(sectionSeparator());
				contentPanel.add(Box.createVerticalStrut(6));
			}
			firstBand = false;
			String headline = band == Band.GAP
					? "No Engineering (" + section.size() + ")"
					: section.get(0).bandLabel() + " (" + section.size() + ")";
			contentPanel.add(sectionHeadline(headline, band));
			contentPanel.add(Box.createVerticalStrut(4));
			contentPanel.add(createBandPanel(band, section));
		}
		contentPanel.add(Box.createVerticalGlue());
		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private static final int FIXED_BLUEPRINT_COMBO_W = 230;
	private static final int FIXED_EXPERIMENTAL_COMBO_W = 190;
	private static final int FIXED_SLOT_SIZE_COL_W = 88;
	/** Cap so short names don't leave a huge empty Component column. */
	private static final int FIXED_COMPONENT_COL_W = 200;

	/** Prefer natural chip width for longest action label so text isn't ellipsized. */
	private int actionColumnWidth() {
		int w = 120;
		for (String label : List.of("Add goal", "Edit goal", "Modify")) {
			JButton probe = new JButton(label);
			OverlayOutlineButtonStyle.applyChip(probe, baseFont, false);
			w = Math.max(w, probe.getPreferredSize().width + 4);
		}
		return w;
	}

	private int levelColumnWidth() {
		JLabel probe = headerLabel("Level");
		JLabel sample = new JLabel("G3→G5");
		sample.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		return Math.max(probe.getPreferredSize().width + 4,
				Math.max(56, sample.getPreferredSize().width + 4));
	}

	private Dimension actionButtonSize(JButton btn) {
		Dimension pref = btn.getPreferredSize();
		int w = Math.max(actionColumnWidth(), pref.width);
		int h = Math.max(fontSize + 10, pref.height);
		return new Dimension(w, h);
	}

	private JComponent createBandPanel(Band band, List<Row> rows) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 2, 8);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridy = 0;
		addBandHeader(panel, gbc, band);

		gbc.insets = new Insets(0, 0, 4, 8);
		int y = 1;
		if (band == Band.PARTIAL || band == Band.GAP) {
			List<Row> withoutGoal = new ArrayList<>();
			List<Row> withGoal = new ArrayList<>();
			for (Row row : rows) {
				if (hasExistingGoalFor(row)) {
					withGoal.add(row);
				} else {
					withoutGoal.add(row);
				}
			}
			for (Row row : withoutGoal) {
				gbc.gridy = y++;
				gbc.gridwidth = 1;
				if (band == Band.GAP) {
					addNoEngineeringRow(panel, gbc, row);
				} else {
					addEngineeredRow(panel, gbc, row);
				}
			}
			if (!withoutGoal.isEmpty() && !withGoal.isEmpty()) {
				addIntraBandSeparator(panel, gbc, y++);
			}
			for (Row row : withGoal) {
				gbc.gridy = y++;
				gbc.gridwidth = 1;
				gbc.insets = new Insets(0, 0, 4, 8);
				if (band == Band.GAP) {
					addGapRowWithGoal(panel, gbc, row);
				} else {
					addEngineeredRow(panel, gbc, row);
				}
			}
		} else {
			for (Row row : rows) {
				gbc.gridy = y++;
				addEngineeredRow(panel, gbc, row);
			}
		}
		gbc.gridx = 0;
		gbc.gridy = y;
		gbc.gridwidth = 6;
		gbc.weightx = 1;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(0, 0, 0, 0);
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		panel.add(filler, gbc);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	private void addIntraBandSeparator(JPanel panel, GridBagConstraints gbc, int y) {
		gbc.gridy = y;
		gbc.gridx = 0;
		gbc.gridwidth = 6;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 0, 6, 0);
		panel.add(sectionSeparator(), gbc);
	}

	private void addBandHeader(JPanel panel, GridBagConstraints gbc, Band band) {
		JLabel moduleHdr = headerLabel("Component");
		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(moduleHdr, gbc);
		moduleHdr.setPreferredSize(new Dimension(FIXED_COMPONENT_COL_W, fontSize + 8));

		JLabel sizeHdr = headerLabel("Size");
		gbc.gridx = 1;
		panel.add(sizeHdr, gbc);
		sizeHdr.setPreferredSize(new Dimension(FIXED_SLOT_SIZE_COL_W, fontSize + 8));

		JLabel bpHdr = headerLabel("Blueprint");
		gbc.gridx = 2;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(bpHdr, gbc);
		bpHdr.setPreferredSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, fontSize + 8));
		bpHdr.setMinimumSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, fontSize + 8));

		JLabel expHdr = headerLabel("Experimental");
		gbc.gridx = 3;
		gbc.weightx = 0.75;
		panel.add(expHdr, gbc);
		expHdr.setPreferredSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, fontSize + 8));
		expHdr.setMinimumSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, fontSize + 8));

		int levelW = levelColumnWidth();
		int actionW = actionColumnWidth();

		JLabel levelHdr = headerLabel(band == Band.GAP ? " " : "Level");
		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(levelHdr, gbc);
		levelHdr.setPreferredSize(new Dimension(levelW, fontSize + 8));

		JLabel actionHdr = headerLabel(" ");
		gbc.gridx = 5;
		panel.add(actionHdr, gbc);
		actionHdr.setPreferredSize(new Dimension(actionW, fontSize + 8));
	}

	private JLabel headerLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(baseFont.deriveFont(Font.BOLD, fontSize));
		label.setForeground(EdoUi.User.MAIN_TEXT);
		return label;
	}

	private JLabel componentLabel(Row row) {
		JLabel moduleLbl = new JLabel(row.componentDisplay());
		moduleLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		moduleLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		String tip = row.componentDisplay();
		if (row.moduleLabel() != null && !row.moduleLabel().isBlank()
				&& !row.moduleLabel().equalsIgnoreCase(row.componentDisplay())) {
			tip = tip + " · " + row.moduleLabel();
		}
		moduleLbl.setToolTipText(tip);
		int h = Math.max(fontSize + 10, moduleLbl.getPreferredSize().height);
		int naturalW = moduleLbl.getPreferredSize().width;
		int w = Math.min(Math.max(naturalW, 80), FIXED_COMPONENT_COL_W);
		moduleLbl.setPreferredSize(new Dimension(w, h));
		moduleLbl.setMinimumSize(new Dimension(80, h));
		moduleLbl.setMaximumSize(new Dimension(FIXED_COMPONENT_COL_W, h));
		return moduleLbl;
	}

	private JLabel slotSizeLabel(Row row) {
		JLabel sizeLbl = new JLabel(row.slotSizeDisplay());
		sizeLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		sizeLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		sizeLbl.setPreferredSize(new Dimension(FIXED_SLOT_SIZE_COL_W, fontSize + 10));
		if (row.slotLabel() != null && !row.slotLabel().isBlank()) {
			sizeLbl.setToolTipText(row.slotLabel());
		}
		return sizeLbl;
	}

	private void addNoEngineeringRow(JPanel panel, GridBagConstraints gbc, Row row) {
		JLabel moduleLbl = componentLabel(row);
		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(moduleLbl, gbc);

		gbc.gridx = 1;
		panel.add(slotSizeLabel(row), gbc);

		JComboBox<String> blueprintCombo = new JComboBox<>();
		styleActionCombo(blueprintCombo);
		List<String> blueprintNames = blueprintNamesForModuleType(row.moduleType());
		boolean hasBlueprints = !blueprintNames.isEmpty();
		blueprintCombo.addItem("(none)");
		for (String name : blueprintNames) {
			blueprintCombo.addItem(name);
		}
		blueprintCombo.setEnabled(hasBlueprints);
		blueprintCombo.setSelectedItem("(none)");
		sizeFlexibleCombo(blueprintCombo, FIXED_BLUEPRINT_COMBO_W);
		installBlueprintComboEffectTooltips(blueprintCombo, row.moduleType());
		gbc.gridx = 2;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(blueprintCombo, gbc);

		JComboBox<String> experimentalCombo = new JComboBox<>();
		styleActionCombo(experimentalCombo);
		experimentalCombo.setEnabled(false);
		experimentalCombo.setSelectedIndex(-1);
		sizeFlexibleCombo(experimentalCombo, FIXED_EXPERIMENTAL_COMBO_W);
		installExperimentalComboEffectTooltips(experimentalCombo, row.moduleType(),
				() -> {
					String bp = (String) blueprintCombo.getSelectedItem();
					return isNoneComboChoice(bp) ? null : bp;
				});
		gbc.gridx = 3;
		gbc.weightx = 0.75;
		panel.add(experimentalCombo, gbc);

		JLabel levelSpacer = new JLabel(" ");
		levelSpacer.setPreferredSize(new Dimension(levelColumnWidth(), fontSize + 10));
		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(levelSpacer, gbc);

		JButton addBtn = new JButton("Add goal");
		OverlayOutlineButtonStyle.applyChip(addBtn, baseFont, false);
		Dimension actionSize = actionButtonSize(addBtn);
		addBtn.setPreferredSize(actionSize);
		addBtn.setMinimumSize(actionSize);
		addBtn.setMaximumSize(actionSize);
		addBtn.setEnabled(addGoalHandler != null);
		blueprintCombo.addActionListener(e -> {
			String blueprint = (String) blueprintCombo.getSelectedItem();
			experimentalCombo.removeAllItems();
			if (!isNoneComboChoice(blueprint)) {
				experimentalCombo.addItem("(none)");
				for (String name : experimentalNamesFor(row.moduleType(), blueprint)) {
					experimentalCombo.addItem(name);
				}
				experimentalCombo.setEnabled(true);
				experimentalCombo.setSelectedItem("(none)");
			} else {
				experimentalCombo.setEnabled(false);
				experimentalCombo.setSelectedIndex(-1);
			}
			OverlayComboBoxStyle.refreshInk(experimentalCombo);
		});
		Runnable addAction = () -> addGoalFromGap(row, blueprintCombo, experimentalCombo, addBtn);
		addBtn.addActionListener(e -> addAction.run());
		HoverClickPoller.register(addBtn, HOVER_CLICK_DELAY_MS, addAction, passThroughEnabledSupplier);
		gbc.gridx = 5;
		panel.add(addBtn, gbc);
	}

	/** Gap row that already has a matching goal: show goal blueprint / experimental + Edit goal. */
	private void addGapRowWithGoal(JPanel panel, GridBagConstraints gbc, Row row) {
		EngineeringGoal goal = findMatchingGoal(row);
		JLabel moduleLbl = componentLabel(row);
		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(moduleLbl, gbc);

		gbc.gridx = 1;
		panel.add(slotSizeLabel(row), gbc);

		String blueprint = goal != null ? goal.getBlueprintName() : "";
		JLabel bpLbl = new JLabel(blueprint != null && !blueprint.isBlank() ? blueprint : "—");
		bpLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		bpLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		int bpH = Math.max(fontSize + 10, bpLbl.getPreferredSize().height);
		bpLbl.setPreferredSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, bpH));
		bpLbl.setMinimumSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, bpH));
		int tipGrade = goal != null ? Math.max(1, goal.getTargetGrade()) : 1;
		bpLbl.setToolTipText(database != null && blueprint != null && !blueprint.isBlank()
				? database.blueprintEffectTooltip(row.moduleType(), blueprint, tipGrade)
				: null);
		gbc.gridx = 2;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(bpLbl, gbc);

		String goalExp = experimentalNameForGoal(goal);
		JLabel expLbl = new JLabel(goalExp != null && !goalExp.isBlank() ? goalExp : "—");
		expLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		expLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		int expH = Math.max(fontSize + 10, expLbl.getPreferredSize().height);
		expLbl.setPreferredSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, expH));
		expLbl.setMinimumSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, expH));
		expLbl.setToolTipText(database != null && goalExp != null && !goalExp.isBlank()
				? database.experimentalEffectTooltip(row.moduleType(), blueprint, goalExp)
				: null);
		gbc.gridx = 3;
		gbc.weightx = 0.75;
		panel.add(expLbl, gbc);

		JLabel levelSpacer = new JLabel(" ");
		levelSpacer.setPreferredSize(new Dimension(levelColumnWidth(), fontSize + 10));
		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(levelSpacer, gbc);

		JButton goalBtn = new JButton("Edit goal");
		OverlayOutlineButtonStyle.applyChip(goalBtn, baseFont, false);
		goalBtn.setEnabled(addGoalHandler != null);
		goalBtn.setToolTipText("Edit the matching engineering goal (grade / experimental / quantity)");
		Dimension actionSize = actionButtonSize(goalBtn);
		goalBtn.setPreferredSize(actionSize);
		goalBtn.setMinimumSize(actionSize);
		goalBtn.setMaximumSize(actionSize);
		Runnable goalAction = () -> openGoalForEngineeredRow(row);
		goalBtn.addActionListener(e -> goalAction.run());
		HoverClickPoller.register(goalBtn, HOVER_CLICK_DELAY_MS, goalAction, passThroughEnabledSupplier);
		gbc.gridx = 5;
		panel.add(goalBtn, gbc);
	}

	private void addEngineeredRow(JPanel panel, GridBagConstraints gbc, Row row) {
		JLabel moduleLbl = componentLabel(row);
		gbc.gridx = 0;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(moduleLbl, gbc);

		gbc.gridx = 1;
		panel.add(slotSizeLabel(row), gbc);

		JLabel bpLbl = new JLabel(row.blueprintDisplay());
		bpLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		bpLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		int bpH = Math.max(fontSize + 10, bpLbl.getPreferredSize().height);
		bpLbl.setPreferredSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, bpH));
		bpLbl.setMinimumSize(new Dimension(FIXED_BLUEPRINT_COMBO_W, bpH));
		bpLbl.setToolTipText(database != null
				? database.blueprintEffectTooltip(row.moduleType(), row.blueprintLabel(), row.level())
				: null);
		gbc.gridx = 2;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(bpLbl, gbc);

		JLabel expLbl = new JLabel(experimentalDisplayFor(row));
		expLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		expLbl.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		int expH = Math.max(fontSize + 10, expLbl.getPreferredSize().height);
		expLbl.setPreferredSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, expH));
		expLbl.setMinimumSize(new Dimension(FIXED_EXPERIMENTAL_COMBO_W, expH));
		String tipExperimental = experimentalTooltipNameFor(row);
		expLbl.setToolTipText(database != null
				? database.experimentalEffectTooltip(
						row.moduleType(), row.blueprintLabel(), tipExperimental)
				: null);
		gbc.gridx = 3;
		gbc.weightx = 0.75;
		panel.add(expLbl, gbc);

		JLabel levelLbl = new JLabel(levelDisplayFor(row));
		levelLbl.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		levelLbl.setForeground(EdoUi.User.MAIN_TEXT);
		levelLbl.setPreferredSize(new Dimension(levelColumnWidth(), fontSize + 10));
		gbc.gridx = 4;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(levelLbl, gbc);

		boolean hasGoal = hasExistingGoalFor(row);
		String actionLabel = row.band() == Band.DONE
				? (hasGoal ? "Edit goal" : "Modify")
				: (hasGoal ? "Edit goal" : "Add goal");
		JButton goalBtn = new JButton(actionLabel);
		OverlayOutlineButtonStyle.applyChip(goalBtn, baseFont, false);
		goalBtn.setEnabled(addGoalHandler != null);
		goalBtn.setToolTipText(hasGoal
				? "Edit the matching engineering goal (grade / experimental / quantity)"
				: row.band() == Band.DONE
						? "Modify engineering on this finished module (e.g. experimental)"
						: "Add an engineering goal for this module");
		Dimension actionSize = actionButtonSize(goalBtn);
		goalBtn.setPreferredSize(actionSize);
		goalBtn.setMinimumSize(actionSize);
		goalBtn.setMaximumSize(actionSize);
		Runnable goalAction = () -> openGoalForEngineeredRow(row);
		goalBtn.addActionListener(e -> goalAction.run());
		HoverClickPoller.register(goalBtn, HOVER_CLICK_DELAY_MS, goalAction, passThroughEnabledSupplier);
		gbc.gridx = 5;
		panel.add(goalBtn, gbc);
	}

	private void sizeFixedCombo(JComboBox<String> combo, int width) {
		int h = Math.max(fontSize + 10, combo.getPreferredSize().height);
		Dimension size = new Dimension(width, h);
		combo.setPreferredSize(size);
		combo.setMinimumSize(size);
		combo.setMaximumSize(size);
	}

	/** Preferred/min width fixed; max unbounded so GridBag can grow the column. */
	private void sizeFlexibleCombo(JComboBox<String> combo, int width) {
		int h = Math.max(fontSize + 10, combo.getPreferredSize().height);
		combo.setPreferredSize(new Dimension(width, h));
		combo.setMinimumSize(new Dimension(width, h));
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
	}

	private static java.awt.Color statusColor(Band band) {
		return switch (band) {
			case GAP -> EdoUi.User.ERROR;
			case PARTIAL -> EdoUi.User.WARNING;
			case DONE -> EdoUi.User.SUCCESS;
		};
	}

	private void addGoalFromGap(Row row, JComboBox<String> blueprintCombo, JComboBox<String> experimentalCombo,
			JComponent toastAnchor) {
		if (addGoalHandler == null || row == null) {
			return;
		}
		String blueprint = blueprintCombo != null ? (String) blueprintCombo.getSelectedItem() : null;
		if (isNoneComboChoice(blueprint)) {
			SystemTableHoverCopyManager.showToast(
					toastAnchor != null ? toastAnchor : this.getRootPane(),
					"Pick a blueprint");
			return;
		}
		String experimental = experimentalCombo != null ? (String) experimentalCombo.getSelectedItem() : null;
		if (isNoneComboChoice(experimental)) {
			experimental = null;
		}
		EngineeringShipRef ship = shipRefFor(row);
		String slotKey = row.slotKey() != null ? row.slotKey() : "";
		EngineeringGoal created = addGoalHandler.apply(new AddGoalRequest(
				ship,
				row.moduleType(),
				row.moduleLabel(),
				blueprint,
				experimental,
				slotKey.isBlank() ? quantityForRow(row) : 1,
				maxGradeForBlueprint(row.moduleType(), blueprint),
				null,
				slotKey));
		if (created != null) {
			startLoad(goalsSupplier.get(), clientKey, true);
		}
	}

	/** Add or edit a goal for an engineered module (including experimental swaps on Done rows). */
	private void openGoalForEngineeredRow(Row row) {
		if (addGoalHandler == null || row == null) {
			return;
		}
		String experimental = row.experimentalLabel();
		if (experimental != null && experimental.isBlank()) {
			experimental = null;
		}
		EngineeringGoal existing = findMatchingGoal(row);
		String slotKey = row.slotKey() != null ? row.slotKey() : "";
		int quantity = existing != null
				? existing.getQuantity()
				: (slotKey.isBlank() ? quantityForRow(row) : 1);
		String blueprintName;
		String experimentalName;
		int preferredGrade;
		if (row.band() == Band.GAP && existing != null) {
			blueprintName = existing.getBlueprintName();
			experimentalName = experimentalNameForGoal(existing);
			preferredGrade = existing.getTargetGrade();
		} else {
			blueprintName = row.blueprintLabel();
			experimentalName = experimental;
			preferredGrade = row.maxGrade() > 0
					? row.maxGrade()
					: maxGradeForBlueprint(row.moduleType(), row.blueprintLabel());
		}
		EngineeringGoal result = addGoalHandler.apply(new AddGoalRequest(
				shipRefFor(row),
				row.moduleType(),
				row.moduleLabel(),
				blueprintName,
				experimentalName,
				quantity,
				preferredGrade,
				existing,
				slotKey));
		if (result != null) {
			startLoad(goalsSupplier.get(), clientKey, true);
		}
	}

	/**
	 * How many identical fitted modules this line represents for goal quantity (same type /
	 * blueprint / level / experimental on the selected ship).
	 */
	private int quantityForRow(Row row) {
		if (row == null) {
			return 1;
		}
		int n = 0;
		for (Row other : currentSummary.rows()) {
			if (other.shipId() != row.shipId()) {
				continue;
			}
			if (!EngineeringJournalBlueprintResolver.sameModuleType(other.moduleType(), row.moduleType())) {
				continue;
			}
			if (row.band() == Band.GAP) {
				if (other.band() != Band.GAP) {
					continue;
				}
			} else {
				String a = EngineeringJournalBlueprintResolver.normalizeToken(row.blueprintLabel());
				String b = EngineeringJournalBlueprintResolver.normalizeToken(other.blueprintLabel());
				if (!a.equals(b) || other.level() != row.level()) {
					continue;
				}
				String expA = EngineeringJournalBlueprintResolver.normalizeToken(row.experimentalLabel());
				String expB = EngineeringJournalBlueprintResolver.normalizeToken(other.experimentalLabel());
				if (!expA.equals(expB)) {
					continue;
				}
			}
			n += Math.max(1, other.count());
		}
		return Math.max(1, n);
	}

	private int maxGradeForBlueprint(String moduleType, String blueprintName) {
		if (database == null || moduleType == null || moduleType.isBlank()
				|| blueprintName == null || blueprintName.isBlank()) {
			return 0;
		}
		return database.gradesFor(moduleType, blueprintName).stream()
				.filter(g -> !g.isExperimental())
				.mapToInt(BlueprintGrade::getGrade)
				.max()
				.orElse(0);
	}

	private EngineeringShipRef shipRefFor(Row row) {
		EngineeringShipRef ship = shipCatalog.get(row.shipId());
		if (ship != null) {
			return ship;
		}
		return new EngineeringShipRef(row.shipId(), "", resolveShipTitle(row.shipId()), "");
	}

	private EngineeringGoal findMatchingGoal(Row row) {
		if (row == null) {
			return null;
		}
		List<Row> rows = currentSummary != null ? currentSummary.rows() : List.of();
		List<EngineeringGoal> live = goalsSupplier != null ? goalsSupplier.get() : List.of();
		EngineeringGoal matched = EngineeringGoalSlotMatcher.forRow(row, rows, live);
		if (matched != null) {
			return matched;
		}
		if (allUnits == null || allUnits.isEmpty()) {
			return null;
		}
		List<EngineeringGoal> fromUnits = new ArrayList<>();
		IdentityHashMap<EngineeringGoal, Boolean> seen = new IdentityHashMap<>();
		for (ModuleUnitProgress unit : allUnits) {
			if (unit == null || unit.unit() == null) {
				continue;
			}
			if (seen.put(unit.unit(), Boolean.TRUE) == null) {
				fromUnits.add(unit.unit());
			}
		}
		return EngineeringGoalSlotMatcher.forRow(row, rows, fromUnits);
	}

	private boolean hasExistingGoalFor(Row row) {
		return findMatchingGoal(row) != null;
	}

	private String buildSummaryText() {
		ShipFilterItem filter = (ShipFilterItem) shipCombo.getSelectedItem();
		Long shipId = filter != null ? filter.shipId() : null;
		if (shipId == null) {
			return null;
		}
		LoadoutEvent loadout = loadoutsByShip.get(shipId);
		ShipEngineeringSummary summary = ShipEngineeringSummary.fromLoadout(loadout, database);
		String title = resolveShipTitle(shipId.longValue());
		StringBuilder text = new StringBuilder(summary.toClipboardText(
				title, this::goalTargetGradeFor, this::experimentalDisplayFor));
		appendGoalsSection(text, shipId.longValue());
		text.append(engineeringRecommendationFooter());
		return text.toString();
	}

	static String engineeringRecommendationFooter() {
		return "\nEngineering recommendations: follow the SLEF return format at\n"
				+ ENGINEERING_RECOMMENDATIONS_URL + "\n";
	}

	private void showSummaryDialog() {
		String text = buildSummaryText();
		if (text == null) {
			return;
		}
		JDialog dlg = new JDialog(this, "Loadout summary", ModalityType.APPLICATION_MODAL);
		dlg.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(12, 14, 12, 14));
		root.setBackground(EdoUi.User.BACKGROUND);
		root.setOpaque(true);

		JLabel heading = new JLabel("Summary");
		heading.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 2));
		heading.setForeground(EdoUi.User.MAIN_TEXT);
		root.add(heading, BorderLayout.NORTH);

		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setLineWrap(false);
		area.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		area.setForeground(EdoUi.User.MAIN_TEXT);
		area.setBackground(EdoUi.User.PANEL_BG);
		area.setCaretColor(EdoUi.User.MAIN_TEXT);
		area.setBorder(new EmptyBorder(8, 10, 8, 10));
		area.setSelectedTextColor(EdoUi.User.BACKGROUND);
		area.setSelectionColor(EdoUi.User.MAIN_TEXT);
		area.setCaretPosition(0);

		JScrollPane scroll = new JScrollPane(area);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		root.add(scroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);

		JButton copyBtn = new JButton("Copy");
		OverlayOutlineButtonStyle.applyChip(copyBtn, baseFont, false);
		copyBtn.setToolTipText("Copy this summary to the clipboard");
		Runnable copyAction = () -> {
			copyToClipboard(text);
			SystemTableHoverCopyManager.showCopiedToast(copyBtn, "Summary");
		};
		copyBtn.addActionListener(e -> copyAction.run());
		HoverClickPoller.register(copyBtn, HOVER_CLICK_DELAY_MS, copyAction, passThroughEnabledSupplier);
		south.add(copyBtn);

		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, baseFont, false);
		closeBtn.addActionListener(e -> dlg.dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, dlg::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);

		JPanel southWithRule = new JPanel(new BorderLayout());
		southWithRule.setOpaque(false);
		southWithRule.add(sectionSeparator(), BorderLayout.NORTH);
		southWithRule.add(south, BorderLayout.SOUTH);
		root.add(southWithRule, BorderLayout.SOUTH);

		dlg.setContentPane(root);
		dlg.setMinimumSize(new Dimension(420, 360));
		dlg.setPreferredSize(new Dimension(560, 520));
		dlg.pack();
		dlg.setSize(560, 520);
		dlg.setLocationRelativeTo(this);
		dlg.setAlwaysOnTop(true);
		AlwaysOnTopPopupFactory.installWhileShowing(dlg);
		dlg.setVisible(true);
	}

	private String levelDisplayFor(Row row) {
		return row.levelDisplay(goalTargetGradeFor(row));
	}

	private int goalTargetGradeFor(Row row) {
		EngineeringGoal goal = findMatchingGoal(row);
		return goal != null ? goal.getTargetGrade() : 0;
	}

	/**
	 * Partial + goal with a pending experimental: {@code +Mass Manager}.
	 * Once applied (or already fitted), drop the {@code +}.
	 */
	private String experimentalDisplayFor(Row row) {
		if (row == null) {
			return "—";
		}
		if (row.band() != Band.PARTIAL) {
			return row.experimentalDisplay();
		}
		EngineeringGoal goal = findMatchingGoal(row);
		String goalExp = experimentalNameForGoal(goal);
		if (goalExp == null || goalExp.isBlank()) {
			return row.experimentalDisplay();
		}
		boolean applied = goal.isExperimentalApplied()
				|| experimentalMatchesFitted(row.experimentalLabel(), goal, goalExp);
		return applied ? goalExp : "+" + goalExp;
	}

	private String experimentalTooltipNameFor(Row row) {
		String shown = experimentalDisplayFor(row);
		if (shown != null && shown.startsWith("+")) {
			return shown.substring(1);
		}
		if (shown != null && !shown.isBlank() && !"—".equals(shown)) {
			return shown;
		}
		return row != null ? row.experimentalLabel() : null;
	}

	private String experimentalNameForGoal(EngineeringGoal goal) {
		if (goal == null || goal.getExperimentalId() == null || goal.getExperimentalId().isBlank()) {
			return "";
		}
		if (database != null) {
			return database.findById(goal.getExperimentalId()).map(BlueprintGrade::getName).orElse("");
		}
		return "";
	}

	private static boolean experimentalMatchesFitted(String fittedLabel, EngineeringGoal goal, String goalExpName) {
		if (fittedLabel == null || fittedLabel.isBlank() || "—".equals(fittedLabel.trim())) {
			return false;
		}
		String fitted = EngineeringJournalBlueprintResolver.normalizeToken(fittedLabel);
		if (fitted.isEmpty()) {
			return false;
		}
		if (!goalExpName.isBlank()
				&& fitted.equals(EngineeringJournalBlueprintResolver.normalizeToken(goalExpName))) {
			return true;
		}
		if (goal != null && goal.getExperimentalId() != null) {
			String id = EngineeringJournalBlueprintResolver.normalizeToken(goal.getExperimentalId());
			if (!id.isEmpty() && (fitted.contains(id) || id.contains(fitted))) {
				return true;
			}
		}
		return false;
	}

	private void appendGoalsSection(StringBuilder sb, long shipId) {
		List<EngineeringGoal> goals = new ArrayList<>();
		for (EngineeringGoal goal : goalsSupplier.get()) {
			if (goal != null && goal.hasShip() && goal.getShipId() == shipId) {
				goals.add(goal);
			}
		}
		goals.sort(Comparator
				.comparingInt((EngineeringGoal g) -> g.getPriority().sortRank())
				.thenComparing(EngineeringGoal::getModuleType, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(EngineeringGoal::getBlueprintName, String.CASE_INSENSITIVE_ORDER));
		sb.append('\n').append("Goals").append('\n');
		if (goals.isEmpty()) {
			sb.append("  (none)\n");
			return;
		}
		for (EngineeringGoal goal : goals) {
			sb.append("  ").append(formatGoalClipboardLine(goal)).append('\n');
		}
	}

	private String formatGoalClipboardLine(EngineeringGoal goal) {
		StringBuilder line = new StringBuilder();
		line.append(goalModuleDisplay(goal))
				.append(": ")
				.append(goal.getBlueprintName())
				.append(" → ")
				.append(EngineeringGradeProgress.progressLabel(goal));
		if (goal.getExperimentalId() != null && !goal.getExperimentalId().isBlank()) {
			String expName = database != null
					? database.findById(goal.getExperimentalId()).map(BlueprintGrade::getName).orElse("")
					: "";
			if (expName.isBlank()) {
				expName = "experimental";
			}
			line.append(" · ").append(expName);
			if (goal.isExperimentalApplied()) {
				line.append(" (done)");
			}
		}
		if (goal.getQuantity() > 1) {
			line.append(" (").append(goal.getCompletedUnits()).append('/').append(goal.getQuantity()).append(')');
		}
		if (!goal.isEnabled()) {
			line.append(" [off]");
		}
		return line.toString();
	}

	static String goalModuleDisplay(EngineeringGoal goal) {
		String moduleType = goal != null ? goal.getModuleType() : "";
		String size = goal != null
				? ShipEngineeringSummary.shortSlotSize(goal.getTargetSlot())
				: "";
		return size.isBlank() ? moduleType : moduleType + " · " + size;
	}

	private void copyLoadoutJsonAndOpenCoriolis() {
		ShipFilterItem filter = (ShipFilterItem) shipCombo.getSelectedItem();
		Long shipId = filter != null ? filter.shipId() : null;
		if (shipId == null) {
			return;
		}
		LoadoutEvent loadout = loadoutsByShip.get(shipId);
		if (loadout == null || loadout.getRawJson() == null) {
			return;
		}
		copyToClipboard(loadout.getRawJson().toString());
		try {
			if (java.awt.Desktop.isDesktopSupported()
					&& java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(CORIOLIS_IMPORT_URL));
			}
		} catch (Exception ignored) {
			// Clipboard already has JSON.
		}
	}

	private static void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text != null ? text : ""), null);
	}

	private static boolean isNoneComboChoice(String value) {
		return value == null || value.isBlank() || "(none)".equalsIgnoreCase(value);
	}

	private List<String> blueprintNamesForModuleType(String moduleType) {
		if (database == null || moduleType == null || moduleType.isBlank()) {
			return List.of();
		}
		Map<String, Boolean> names = new LinkedHashMap<>();
		for (BlueprintGrade bp : database.getAllBlueprints()) {
			if (bp == null || bp.isExperimental()) {
				continue;
			}
			if (EngineeringJournalBlueprintResolver.sameModuleType(moduleType, bp.getModuleType())) {
				names.putIfAbsent(bp.getName(), Boolean.TRUE);
			}
		}
		return List.copyOf(names.keySet());
	}

	private List<String> experimentalNamesFor(String moduleType, String blueprintName) {
		if (database == null || moduleType == null || moduleType.isBlank()
				|| blueprintName == null || blueprintName.isBlank()) {
			return List.of();
		}
		Map<String, Boolean> names = new LinkedHashMap<>();
		for (BlueprintGrade bp : database.experimentalsFor(moduleType, blueprintName)) {
			if (bp != null && bp.getName() != null && !bp.getName().isBlank()) {
				names.putIfAbsent(bp.getName(), Boolean.TRUE);
			}
		}
		return List.copyOf(names.keySet());
	}

	private void styleActionCombo(JComboBox<String> combo) {
		OverlayComboBoxStyle.apply(combo, baseFont.deriveFont(Font.PLAIN, fontSize));
		combo.setMaximumRowCount(24);
		OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setEnabled(true);
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				return c;
			}
		});
	}

	private void installBlueprintComboEffectTooltips(JComboBox<String> combo, String moduleType) {
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setEnabled(true);
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				String name = value != null ? value.toString() : "";
				setToolTipText(database != null && !isNoneComboChoice(name)
						? database.blueprintEffectTooltip(moduleType, name, 0)
						: null);
				return c;
			}
		});
		Runnable syncTip = () -> {
			String name = (String) combo.getSelectedItem();
			combo.setToolTipText(database != null && !isNoneComboChoice(name)
					? database.blueprintEffectTooltip(moduleType, name, 0)
					: null);
		};
		combo.addActionListener(e -> syncTip.run());
		syncTip.run();
	}

	private void installExperimentalComboEffectTooltips(JComboBox<String> combo,
			String moduleType,
			Supplier<String> blueprintSupplier) {
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setEnabled(true);
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				String name = value != null ? value.toString() : "";
				String blueprint = blueprintSupplier != null ? blueprintSupplier.get() : null;
				setToolTipText(database != null
						? database.experimentalEffectTooltip(moduleType, blueprint, name)
						: null);
				return c;
			}
		});
		Runnable syncTip = () -> {
			String name = (String) combo.getSelectedItem();
			String blueprint = blueprintSupplier != null ? blueprintSupplier.get() : null;
			combo.setToolTipText(database != null
					? database.experimentalEffectTooltip(moduleType, blueprint, name)
					: null);
		};
		combo.addActionListener(e -> syncTip.run());
		syncTip.run();
	}

	private void styleShipCombo(JComboBox<ShipFilterItem> combo) {
		OverlayComboBoxStyle.apply(combo, baseFont.deriveFont(Font.PLAIN, fontSize));
		combo.setMaximumRowCount(24);
		OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
		combo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof ShipFilterItem item) {
					setText(item.label());
				}
				setForeground(EdoUi.User.MAIN_TEXT);
				setBackground(isSelected ? EdoUi.ED_ORANGE_LESS_TRANS : EdoUi.User.PANEL_BG);
				setOpaque(true);
				return c;
			}
		});
	}

	private String resolveShipTitle(long shipId) {
		if (shipId >= 0) {
			EngineeringShipRef ref = shipCatalog.get(shipId);
			if (ref != null) {
				return shipCatalog.displayLabel(ref);
			}
		}
		LoadoutEvent loadout = loadoutsByShip.get(Long.valueOf(shipId));
		if (loadout != null) {
			return EngineeringShipCatalog.fromLoadout(loadout).baseDisplayLabel();
		}
		for (ModuleUnitProgress u : allUnits) {
			if (u.shipId() == shipId && !u.shipLabel().isBlank()) {
				return u.shipLabel();
			}
		}
		return shipId >= 0 ? "Ship #" + shipId : "Ship";
	}

	private JLabel sectionHeadline(String text, Band band) {
		JLabel label = new JLabel(text);
		label.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 1));
		label.setForeground(statusColor(band));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JComponent sectionSeparator() {
		JPanel rule = new JPanel();
		rule.setOpaque(true);
		rule.setBackground(EdoUi.Internal.MAIN_TEXT_ALPHA_40);
		rule.setPreferredSize(new Dimension(1, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		rule.setAlignmentX(Component.LEFT_ALIGNMENT);
		return rule;
	}

	private JLabel mutedLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
		label.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_220);
		return label;
	}

	private record DialogLoadResult(
			List<ModuleUnitProgress> units,
			Map<Long, LoadoutEvent> loadoutsByShip) {
	}

	static record AddGoalRequest(
			EngineeringShipRef ship,
			String moduleType,
			String moduleLabel,
			String blueprintName,
			String experimentalName,
			int quantity,
			int preferredTargetGrade,
			EngineeringGoal existingGoal,
			/** Journal slot to pin; blank leaves the goal unscoped. */
			String slotKey) {
	}

	private record ShipFilterItem(Long shipId, String label, boolean isSeparator) {
		static ShipFilterItem ship(long shipId, String label) {
			return new ShipFilterItem(Long.valueOf(shipId), label != null ? label : ("Ship #" + shipId), false);
		}
	}
}
