package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.dce.ed.exec.ExecShortcutKeys;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.mining.GoogleSheetsAuth;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.mining.ProspectorWriteResult;
import org.dce.ed.mission.MissionSpeechTracker;
import org.dce.ed.ui.EdoDialogTitleBar;
import org.dce.ed.ui.EdoLookAndFeel;
import org.dce.ed.ui.EdoOptionDialog;
import org.dce.ed.ui.EdoSurface;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HelpCircleIcon;
import org.dce.ed.ui.OverlayCheckBoxStyle;
import org.dce.ed.ui.OverlayComboBoxStyle;
import org.dce.ed.ui.OverlayFieldStyle;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.OverlaySliderUI;
import org.dce.ed.ui.WindowEdgeResizeSupport;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.tts.VoiceCacheWarmer;
import org.dce.ed.tts.VoicePackManager;

/**
 * Preferences dialog for the overlay.
 */
public class PreferencesDialog extends JDialog {

	/** Index of the Mining tab in {@link #PreferencesDialog(Window, String)}'s tabbed pane. */
	public static final int MINING_TAB_INDEX = 5;
	public static final int FONTS_TAB_INDEX = 3;
	public static final int EXEC_TAB_INDEX = 8;

	/**
	 * Shows preferences, or brings the existing modeless window to the front if one is already open.
	 * Avoids multiple dialogs whose OK would overwrite newer mining (Sheets) settings with stale UI.
	 */
	public static void show(Window owner, String clientKey) {
		show(owner, clientKey, -1);
	}

	/**
	 * @param initialTabIndex tab to select when opening a new dialog, or -1 for the first tab
	 */
	public static void show(Window owner, String clientKey, int initialTabIndex) {
		for (Window w : Window.getWindows()) {
			if (w instanceof PreferencesDialog pd && w.isDisplayable()) {
				pd.wireExecFromOwner();
				w.toFront();
				if (initialTabIndex >= 0) {
					pd.selectTabIfPossible(initialTabIndex);
				}
				return;
			}
		}
		PreferencesDialog d = new PreferencesDialog(owner, clientKey);
		if (initialTabIndex >= 0) {
			d.selectTabIfPossible(initialTabIndex);
		}
		d.setLocationRelativeTo(owner);
		d.setVisible(true);
	}

	void selectTabIfPossible(int index) {
		if (preferenceTabs != null && index >= 0 && index < preferenceTabs.getTabCount()) {
			preferenceTabs.setSelectedIndex(index);
		}
	}

	void wireExecFromOwner() {
		if (execTabPanel == null) {
			return;
		}
		ExecTriggerService service = resolveExecTriggerService(getOwner());
		if (service != null) {
			execTabPanel.setExecTriggerService(service);
			execTabPanel.refreshFuelLevelLabel();
		}
	}

	/**
	 * {@link ExecTriggerService} always lives on {@link OverlayFrame}. Preferences may be owned by
	 * {@link DecoratedOverlayDialog} when the user runs in normal (non-pass-through) window mode.
	 */
	static ExecTriggerService resolveExecTriggerService(Window window) {
		for (Window w = window; w != null; w = w.getOwner()) {
			if (w instanceof OverlayFrame frame) {
				return frame.getExecTriggerService();
			}
			if (w instanceof DecoratedOverlayDialog decorated) {
				ExecTriggerService service = decorated.getExecTriggerService();
				if (service != null) {
					return service;
				}
			}
		}
		return null;
	}

	public final String clientKey;

	/** True after any live overlay/theme/font preview that must be reverted on Cancel. */
	private boolean livePreviewDirty;
	/** Prevents Cancel + windowClosed from each rebuilding the overlay. */
	private boolean dismissHandled;

	/** Cached once; enumerating system fonts on Windows is often multi-second. */
	private static volatile String[] cachedFontFamilies;
	private boolean fontFamiliesLoaded;

	// Overlay-tab fields so OK can read them
	private JSlider normalTransparencySlider;
	private JLabel normalTransparencyValueLabel;

	private JSlider passThroughTransparencySlider;
	private JLabel passThroughTransparencyValueLabel;

	private JComboBox<String> passThroughHotkeyCombo;
	private JComboBox<String> nextShownTabHotkeyCombo;
	private JCheckBox nonOverlayAlwaysOnTopCheckBox;

	// Overlay-tab fields: auto-switch preferences
	private JCheckBox autoSwitchGalaxyMapToRouteCheckBox;
	private JCheckBox autoSwitchSystemMapToSystemCheckBox;
	private JCheckBox autoSwitchTabOnFsdTargetCheckBox;
	private JCheckBox autoSwitchSystemTabOnJumpOrScanCheckBox;
	private JCheckBox autoSwitchMiningOnPlanetaryRingCheckBox;
	private JCheckBox autoSwitchMiningOnStartupPlanetaryRingCheckBox;
	private JCheckBox autoSwitchBiologyOnNearBodyCheckBox;
	private JCheckBox autoSwitchFleetCarrierOnJsonDropCheckBox;
	private JCheckBox routeFuelPredictionCheckBox;
	private JCheckBox routeFuelPredictionConsiderScoopCheckBox;

	/** Overlay tab: System tab ship / plan-map reference body mode */
	private JComboBox<SystemTabShipRefMode> systemTabShipRefModeComboBox;
	private JCheckBox systemPlanMapAutoZoomHudTargetCheckBox;

	// Logging-tab fields so OK can read them
	private JCheckBox autoDetectCheckBox;
	private JTextField customPathField;

	// Speech-tab fields so OK can read them
	private JCheckBox speechEnabledCheckBox;
	private JCheckBox firstDiscoveredSystemAnnouncementCheckBox;
	private JCheckBox bountyScanFirstAnnouncementCheckBox;
	private JCheckBox bountyScanAdditionalAnnouncementCheckBox;
	private JCheckBox missionProgressAnnouncementCheckBox;
	private JCheckBox miningLowLimpetReminderEnabledCheckBox;
	private JCheckBox fighterPilotReminderEnabledCheckBox;
	private JComboBox<String> speechEngineCombo;
	private JComboBox<String> speechVoiceCombo;
	private JTextField speechCacheDirField;
	private JTextField speechSampleRateField;

	// Fonts-tab fields
	private JComboBox<String> uiFontNameCombo;
	private JSpinner uiFontSizeSpinner;


	// Colors-tab fields
	private JButton uiMainTextColorButton;
	private JButton uiBackgroundColorButton;
	private JButton uiSneakerColorButton;
	private JButton uiPrimaryHighlightColorButton;
	private JButton uiSecondaryHighlightColorButton;

	// Mining-tab fields
	private JTextField prospectorMaterialsField;
	private JSpinner prospectorMinPropSpinner;
	private JSpinner prospectorMinAvgValueSpinner;
	private JTextField miningLogCommanderNameField;

	// Mining tab: log / spreadsheet backend (local vs Google Sheets vs both)
	private JRadioButton miningLogBackendLocalRadio;
	private JRadioButton miningLogBackendGoogleRadio;
	private JRadioButton miningLogBackendBothRadio;
	private JTextField miningGoogleSheetsUrlField;
	private JTextField miningGoogleClientIdField;
	private JTextField miningGoogleClientSecretField;
	private JButton miningGoogleConnectButton;
	private JButton miningGoogleSetupHelpButton;
	private JButton miningGoogleMigrateLegacyButton;
	private JButton miningGoogleRepairLayoutButton;

	// Mining tab: limpet reminder thresholds (announcement toggle is on Speech tab)
	private JRadioButton miningLowLimpetReminderCountRadio;
	private JSpinner miningLowLimpetReminderThresholdSpinner;
	private JRadioButton miningLowLimpetReminderPercentRadio;
	private JSpinner miningLowLimpetReminderPercentSpinner;
	private JSpinner miningAnimGunSizeSpinner;
	private JSpinner miningAnimAsteroidSizeSpinner;
	private JCheckBox miningAnimShowLaserCheckBox;
	private JCheckBox miningAnimShowAsteroidCheckBox;
	private JCheckBox miningScatterAsteroidIconsAllPointsCheckBox;

	private JCheckBox overlayTabRouteVisibleCheckBox;
	private JCheckBox overlayTabSystemVisibleCheckBox;
	private JCheckBox overlayTabBiologyVisibleCheckBox;
	private JCheckBox overlayTabMiningVisibleCheckBox;
	private JCheckBox overlayTabMissionsVisibleCheckBox;
	private JCheckBox overlayTabCombatVisibleCheckBox;
	private JCheckBox overlayTabFleetCarrierVisibleCheckBox;
	private JCheckBox overlayTabEngineeringVisibleCheckBox;
	private JCheckBox overlayTabControlPanelVisibleCheckBox;
	private final java.util.Map<String, JCheckBox> combatFighterCommandCheckBoxes = new java.util.LinkedHashMap<>();
	private final java.util.Map<String, JCheckBox> combatTargetingCommandCheckBoxes = new java.util.LinkedHashMap<>();
	private JSpinner bountyScanValuableThresholdSpinner;
	private JSpinner combatHighValueBountySpinner;

	private ExecTabPanel execTabPanel;
	private JButton okButton;
	private JButton cancelButton;

	private int lastPrefsTabIndex;

	private JSpinner bioValuableThresholdMillionSpinner;
	private JComboBox<OverlayPreferences.BiologyMapDisplayMode> biologyMapDisplayModeComboBox;
	private JCheckBox autoExpandBioOnTargetedBodyCheckBox;

	/** Root tabbed pane (Colors, Exobiology, …); used to jump to a specific tab from helpers. */
	private JTabbedPane preferenceTabs;
	private EdoDialogTitleBar titleBar;

	/** Lazy shared TTS for Speech-tab “sample prospector” preview (avoids constructing Polly clients per click). */
	private static volatile TtsSprintf speechPreferencesPreviewTts;

	/**
	 * Verbatim {@code speakf} templates and args from production code (same strings VoiceCacheWarmer scrapes), so
	 * each click exercises phrases that offline packs should already contain.
	 */
	private static final class PreferenceSpeechTestClip {
		final String template;
		final Object[] args;

		PreferenceSpeechTestClip(String template, Object... args) {
			this.template = template;
			this.args = (args == null || args.length == 0) ? new Object[0] : args.clone();
		}
	}

	private static final PreferenceSpeechTestClip[] PREFERENCE_SPEECH_TEST_CLIPS;
	static {
		// Match VoiceCacheWarmer: list must be an adjacent pair from sorted INARA names + 10/90 or that chunk is missing offline.
		String prospectorListTwo = VoiceCacheWarmer.sampleProspectorListTwoForVoicePack();
		PREFERENCE_SPEECH_TEST_CLIPS = new PreferenceSpeechTestClip[] {
				new PreferenceSpeechTestClip("Welcome commander"),
				new PreferenceSpeechTestClip("Did you forget your limpets again commander?"),
				new PreferenceSpeechTestClip(NpcCrewTracker.FIGHTER_PILOT_REMINDER_SPEECH),
				new PreferenceSpeechTestClip(BountyScanTracker.FIRST_BOUNTY_SPEECH, Long.valueOf(250_000L)),
				new PreferenceSpeechTestClip(BountyScanTracker.ADDITIONAL_BOUNTY_SPEECH,
						Long.valueOf(60_000L), Long.valueOf(310_000L)),
				new PreferenceSpeechTestClip(MissionSpeechTracker.COMBAT_COMPLETE_SPEECH),
				new PreferenceSpeechTestClip(MissionSpeechTracker.DELIVERED_SPEECH,
						Integer.valueOf(12), Integer.valueOf(16)),
				new PreferenceSpeechTestClip("First Discovered System"),
				new PreferenceSpeechTestClip("Jump complete"),
				new PreferenceSpeechTestClip("Cooldown complete"),
				// Prospector {n}: digits, teens, compound tens+ones, max-ish
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(50)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(1)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(11)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(19)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(21)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(99)),
				new PreferenceSpeechTestClip("Prospector found {material} at {n} percent.", "Grandidierite", Integer.valueOf(100)),
				// Prospector list {min}/{max}
				new PreferenceSpeechTestClip("Prospector found {list} from {min} to {max} percent.",
						prospectorListTwo, Integer.valueOf(10), Integer.valueOf(90)),
				new PreferenceSpeechTestClip("Prospector found {list} from {min} to {max} percent.",
						prospectorListTwo, Integer.valueOf(5), Integer.valueOf(95)),
				new PreferenceSpeechTestClip("Prospector found {list} from {min} to {max} percent.",
						prospectorListTwo, Integer.valueOf(11), Integer.valueOf(19)),
				new PreferenceSpeechTestClip("Prospector found {list} from {min} to {max} percent.",
						prospectorListTwo, Integer.valueOf(1), Integer.valueOf(100)),
				// Biology {meters}
				new PreferenceSpeechTestClip("Entering clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(10)),
				new PreferenceSpeechTestClip("Entering clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(1)),
				new PreferenceSpeechTestClip("Entering clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(500)),
				new PreferenceSpeechTestClip("Entering clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(2_500)),
				new PreferenceSpeechTestClip("Entering clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(50_000)),
				new PreferenceSpeechTestClip("Leaving clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(10)),
				new PreferenceSpeechTestClip("Leaving clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(120)),
				new PreferenceSpeechTestClip("Leaving clonal colony range of {species}. Minimum {meters} meters.",
						"Bacterium Acies", Integer.valueOf(9_999)),
				// Exobiology value: single {credits}
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(3), "A 1", Long.valueOf(1_500_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(1), "A 61 b", Long.valueOf(0L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(12), "A 1", Long.valueOf(12_300_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(42), "A 1", Long.valueOf(900_000_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(7), "A 1", Long.valueOf(2_000_000_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with guaranteed exobiology value of {credits} credits",
						Integer.valueOf(128), "A 1", Long.valueOf(3_100_000_000L)),
				// Whole millions range {mm}
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated exobiology value from {mm} to {mm} million credits",
						Integer.valueOf(3), "A 1", Long.valueOf(2L), Long.valueOf(12L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated exobiology value from {mm} to {mm} million credits",
						Integer.valueOf(2), "A 1", Long.valueOf(1L), Long.valueOf(2L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated exobiology value from {mm} to {mm} million credits",
						Integer.valueOf(100), "A 1", Long.valueOf(5L), Long.valueOf(999L)),
				// Arbitrary credit range
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated value between {credits} and {credits} credits",
						Integer.valueOf(3), "A 1", Long.valueOf(1_000L), Long.valueOf(50_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated value between {credits} and {credits} credits",
						Integer.valueOf(5), "A 1", Long.valueOf(1_500_000L), Long.valueOf(12_300_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated value between {credits} and {credits} credits",
						Integer.valueOf(1), "A 1", Long.valueOf(99_000L), Long.valueOf(150_000L)),
				new PreferenceSpeechTestClip(
						"{n} signals on planetary body {body} with estimated value between {credits} and {credits} credits",
						Integer.valueOf(8), "A 1", Long.valueOf(2_000_000_000L), Long.valueOf(3_000_000_000L)),
		};
	}

	private int speechPreferenceTestClipIndex;

	private boolean okPressed;
	private final Font originalUiFont;
	private final int originalNormalTransparencyPct;
	private final int originalPassThroughTransparencyPct;
	private final int originalPassThroughToggleKeyCode;
	private final int originalNextShownTabKeyCode;

	private final int originalUiMainTextRgb;
	private final int originalUiBackgroundRgb;
	private final int originalUiSneakerRgb;
	private final int originalUiPrimaryHighlightRgb;
	private final int originalUiSecondaryHighlightRgb;


	public static final String[] STANDARD_US_ENGLISH_VOICES = new String[] {
			"Joanna",
			"Matthew",
			"Ivy",
			"Justin",
			"Kendra",
			"Kimberly",
			"Joey",
			"Salli"
	};

	public PreferencesDialog(java.awt.Window owner, String clientKey) {
		super(owner, "Overlay Preferences", java.awt.Dialog.ModalityType.MODELESS);

		this.clientKey = clientKey;
		this.originalUiFont = OverlayPreferences.getUiFont();

		this.originalNormalTransparencyPct = OverlayPreferences.getNormalTransparencyPercent();
		this.originalPassThroughTransparencyPct = OverlayPreferences.getPassThroughTransparencyPercent();
		this.originalPassThroughToggleKeyCode = OverlayPreferences.getPassThroughToggleKeyCode();
		this.originalNextShownTabKeyCode = OverlayPreferences.getNextShownTabKeyCode();

		this.originalUiMainTextRgb = OverlayPreferences.getUiMainTextRgb();
		this.originalUiBackgroundRgb = OverlayPreferences.getUiBackgroundRgb();
		this.originalUiSneakerRgb = OverlayPreferences.getUiSneakerRgb();
		this.originalUiPrimaryHighlightRgb = OverlayPreferences.getUiPrimaryHighlightRgb();
		this.originalUiSecondaryHighlightRgb = OverlayPreferences.getUiSecondaryHighlightRgb();


		this.okPressed = false;
		setUndecorated(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		if (getRootPane() != null) {
			EdoSurface.markDialog(getRootPane());
		}
		// Match typical Exec-tab layout from the prefs screenshot (~860×720 content).
		setMinimumSize(new Dimension(780, 560));
		setPreferredSize(new Dimension(860, 720));
		getRootPane().setBorder(BorderFactory.createLineBorder(EdoUi.Internal.TITLEBAR_BG_HOVER, 1));

		titleBar = new EdoDialogTitleBar(this, "Overlay Preferences");
		EdoSurface.markDialog(titleBar);
		add(titleBar, BorderLayout.NORTH);

		this.preferenceTabs = new JTabbedPane();
		EdoSurface.markDialog(preferenceTabs);
		preferenceTabs.addTab("Colors", createColorsPanel());
		preferenceTabs.addTab("Combat", wrapTabInEdoScroll(createCombatPanel()));
		preferenceTabs.addTab("Exobiology", wrapTabInEdoScroll(createExobiologyPanel()));
		preferenceTabs.addTab("Fonts", createFontsPanel());
		preferenceTabs.addTab("Logging", createLoggingPanel());
		preferenceTabs.addTab("Mining", wrapTabInEdoScroll(createMiningPanel()));
		preferenceTabs.addTab("Overlay", wrapTabInEdoScroll(createOverlayPanel()));
		preferenceTabs.addTab("Speech", wrapTabInEdoScroll(createSpeechPanel()));
		preferenceTabs.addTab("Exec", createExecPanel());

		add(preferenceTabs, BorderLayout.CENTER);
		add(createButtonPanel(), BorderLayout.SOUTH);

		preferenceTabs.addChangeListener(e -> {
			int selected = preferenceTabs.getSelectedIndex();
			if (execTabPanel != null && lastPrefsTabIndex == EXEC_TAB_INDEX && selected != EXEC_TAB_INDEX) {
				execTabPanel.commitPendingEdits();
			}
			lastPrefsTabIndex = selected;
			if (selected == EXEC_TAB_INDEX) {
				wireExecFromOwner();
				if (execTabPanel != null) {
					execTabPanel.refreshColumnLayout();
				}
			}
			if (isFontsTabSelected(selected)) {
				ensureFontFamiliesLoaded();
			}
			stylePreferenceTabChrome();
		});

		wireExecFromOwner();

		pack();
		setSize(860, 720);
		setLocationRelativeTo(owner);
		WindowEdgeResizeSupport.install(this);
		applyDialogChrome(true);
		if (execTabPanel != null) {
			SwingUtilities.invokeLater(execTabPanel::refreshColumnLayout);
		}

		// If the user closes the dialog or hits Cancel, revert any live preview.
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				finishDismissWithoutSave();
			}

			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				finishDismissWithoutSave();
			}
		});
	}

	private JPanel createExecPanel() {
		execTabPanel = new ExecTabPanel();
		// No outer scroll: table scrolls inside ExecTabPanel so Add/Manage/help stay visible above OK/Cancel.
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(true);
		wrapper.setBackground(EdoUi.User.BACKGROUND);
		wrapper.add(execTabPanel, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel createOverlayPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		// --- Controls / Hotkeys ---
		JPanel hotkeyPanel = new JPanel(new GridBagLayout());
		hotkeyPanel.setOpaque(false);
		hotkeyPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Controls"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		JLabel hotkeyLabel = new JLabel("Overlay window mode toggle key:");
		hotkeyPanel.add(hotkeyLabel, gbc);

		gbc.gridx = 1;
		passThroughHotkeyCombo = new JComboBox<>(ExecShortcutKeys.displayChoices());
		passThroughHotkeyCombo.setSelectedItem(ExecShortcutKeys.toDisplayString(originalPassThroughToggleKeyCode));
		hotkeyPanel.add(passThroughHotkeyCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel nextTabLabel = new JLabel("Next shown tab key:");
		hotkeyPanel.add(nextTabLabel, gbc);

		gbc.gridx = 1;
		nextShownTabHotkeyCombo = new JComboBox<>(ExecShortcutKeys.displayChoices());
		nextShownTabHotkeyCombo.setSelectedItem(ExecShortcutKeys.toDisplayString(originalNextShownTabKeyCode));
		hotkeyPanel.add(nextShownTabHotkeyCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;

		nonOverlayAlwaysOnTopCheckBox = new JCheckBox(
				"Always on top when Elite has focus (non-overlay mode)");
		nonOverlayAlwaysOnTopCheckBox.setOpaque(false);
		nonOverlayAlwaysOnTopCheckBox.setSelected(OverlayPreferences.isNonOverlayAlwaysOnTop());
		hotkeyPanel.add(nonOverlayAlwaysOnTopCheckBox, gbc);

		gbc.gridwidth = 1;

		addLeftStackedSection(panel, hotkeyPanel);
		JPanel tabsPanel = new JPanel(new GridBagLayout());
		tabsPanel.setOpaque(false);
		tabsPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Visible tabs"));

		GridBagConstraints tgc = new GridBagConstraints();
		tgc.gridx = 0;
		tgc.gridy = 0;
		tgc.anchor = GridBagConstraints.WEST;
		tgc.insets = new Insets(2, 8, 2, 8);

		overlayTabRouteVisibleCheckBox = new JCheckBox("Route");
		overlayTabRouteVisibleCheckBox.setOpaque(false);
		overlayTabRouteVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabRouteVisible());
		tabsPanel.add(overlayTabRouteVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabSystemVisibleCheckBox = new JCheckBox("System");
		overlayTabSystemVisibleCheckBox.setOpaque(false);
		overlayTabSystemVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabSystemVisible());
		tabsPanel.add(overlayTabSystemVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabBiologyVisibleCheckBox = new JCheckBox("ExoBio");
		overlayTabBiologyVisibleCheckBox.setOpaque(false);
		overlayTabBiologyVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabBiologyVisible());
		tabsPanel.add(overlayTabBiologyVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabMiningVisibleCheckBox = new JCheckBox("Mining");
		overlayTabMiningVisibleCheckBox.setOpaque(false);
		overlayTabMiningVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabMiningVisible());
		tabsPanel.add(overlayTabMiningVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabMissionsVisibleCheckBox = new JCheckBox("Missions");
		overlayTabMissionsVisibleCheckBox.setOpaque(false);
		overlayTabMissionsVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabMissionsVisible());
		tabsPanel.add(overlayTabMissionsVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabCombatVisibleCheckBox = new JCheckBox("Combat");
		overlayTabCombatVisibleCheckBox.setOpaque(false);
		overlayTabCombatVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabCombatVisible());
		tabsPanel.add(overlayTabCombatVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabFleetCarrierVisibleCheckBox = new JCheckBox("Fleet Carrier");
		overlayTabFleetCarrierVisibleCheckBox.setOpaque(false);
		overlayTabFleetCarrierVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabFleetCarrierVisible());
		tabsPanel.add(overlayTabFleetCarrierVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabEngineeringVisibleCheckBox = new JCheckBox("Engineering");
		overlayTabEngineeringVisibleCheckBox.setOpaque(false);
		overlayTabEngineeringVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabEngineeringVisible());
		tabsPanel.add(overlayTabEngineeringVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabControlPanelVisibleCheckBox = new JCheckBox("Control Panel");
		overlayTabControlPanelVisibleCheckBox.setOpaque(false);
		overlayTabControlPanelVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabControlPanelVisible());
		tabsPanel.add(overlayTabControlPanelVisibleCheckBox, tgc);

		addLeftStackedSection(panel, tabsPanel);
		JPanel systemTabPrefsPanel = new JPanel(new GridBagLayout());
		systemTabPrefsPanel.setOpaque(false);
		systemTabPrefsPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"System tab"));

		GridBagConstraints stc = new GridBagConstraints();
		stc.gridx = 0;
		stc.gridy = 0;
		stc.anchor = GridBagConstraints.WEST;
		stc.insets = new Insets(2, 8, 2, 8);

		JLabel shipRefLabel = new JLabel("Ship / plan map reference body:");
		systemTabPrefsPanel.add(shipRefLabel, stc);

		stc.gridx = 1;
		systemTabShipRefModeComboBox = new JComboBox<>(SystemTabShipRefMode.values());
		systemTabShipRefModeComboBox.setMaximumRowCount(2);
		systemTabShipRefModeComboBox.setOpaque(false);
		systemTabShipRefModeComboBox.setSelectedItem(OverlayPreferences.getSystemTabShipRefMode());
		systemTabShipRefModeComboBox.setToolTipText(
				"<html>Body used for the plan map “You” marker, ship-centric distances, and distance-column sort "
						+ "(rocket icon on the System tab).<br>"
						+ "<b>Approach body</b>: journal ApproachBody and Status proximity — stays on the last "
						+ "approached body until a new approach.<br>"
						+ "<b>HUD target (sticky)</b>: the HUD navigation body; clearing the target keeps that body "
						+ "until you select another (plan-map ▲ follows the target). Active ApproachBody always overrides.<br>"
						+ "Optional <b>Auto-zoom to destination subsystem</b> frames the target cluster on the plan map.<br>"
						+ "Docked on a fleet carrier → the carrier’s parked orbit body.</html>");
		systemTabShipRefModeComboBox.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof SystemTabShipRefMode) {
					setText(((SystemTabShipRefMode) value).displayName());
				}
				return c;
			}
		});
		systemTabPrefsPanel.add(systemTabShipRefModeComboBox, stc);

		stc.gridx = 0;
		stc.gridy = 1;
		stc.gridwidth = 2;
		systemPlanMapAutoZoomHudTargetCheckBox = new JCheckBox("Auto-zoom to destination subsystem",
				OverlayPreferences.isSystemPlanMapAutoZoomHudTargetSubsystem());
		systemPlanMapAutoZoomHudTargetCheckBox.setOpaque(false);
		systemPlanMapAutoZoomHudTargetCheckBox.setToolTipText(
				"<html>When <b>HUD target (sticky)</b> is selected and you target a body on the HUD, "
						+ "the plan map smoothly pans and zooms to frame a useful cluster "
						+ "(≤7 s; skips zoom-out when already near scale).<br>"
						+ "Wide binaries: both stars (A+B) unless you target a branch star that has planets.<br>"
						+ "Does not apply in Approach body mode.</html>");
		systemTabPrefsPanel.add(systemPlanMapAutoZoomHudTargetCheckBox, stc);
		stc.gridwidth = 1;

		addLeftStackedSection(panel, systemTabPrefsPanel);
		JPanel autoSwitchPanel = new JPanel(new GridBagLayout());
		autoSwitchPanel.setOpaque(false);
		autoSwitchPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Auto-switch tabs"));

		GridBagConstraints agc = new GridBagConstraints();
		agc.gridx = 0;
		agc.gridy = 0;
		agc.anchor = GridBagConstraints.WEST;
		agc.insets = new Insets(2, 8, 2, 8);

		autoSwitchGalaxyMapToRouteCheckBox = new JCheckBox(
				"Open Galaxy Map → Route tab (Fleet Carrier only after carrier management, then map from right panel / station services)");
		autoSwitchGalaxyMapToRouteCheckBox.setOpaque(false);
		autoSwitchGalaxyMapToRouteCheckBox.setSelected(OverlayPreferences.isAutoSwitchRouteOnGalaxyMap());
		autoSwitchPanel.add(autoSwitchGalaxyMapToRouteCheckBox, agc);

		agc.gridy++;
		autoSwitchSystemMapToSystemCheckBox = new JCheckBox("Open System Map → System tab");
		autoSwitchSystemMapToSystemCheckBox.setOpaque(false);
		autoSwitchSystemMapToSystemCheckBox.setSelected(OverlayPreferences.isAutoSwitchSystemOnSystemMap());
		autoSwitchPanel.add(autoSwitchSystemMapToSystemCheckBox, agc);

		agc.gridy++;
		autoSwitchTabOnFsdTargetCheckBox = new JCheckBox(
				"Start hyperspace jump → Route / System tab (Fleet Carrier tab if docked on a carrier)");
		autoSwitchTabOnFsdTargetCheckBox.setOpaque(false);
		autoSwitchTabOnFsdTargetCheckBox.setSelected(OverlayPreferences.isAutoSwitchTabOnFsdTarget());
		autoSwitchPanel.add(autoSwitchTabOnFsdTargetCheckBox, agc);

		agc.gridy++;
		autoSwitchSystemTabOnJumpOrScanCheckBox = new JCheckBox("Jump / Discovery scan → System tab");
		autoSwitchSystemTabOnJumpOrScanCheckBox.setOpaque(false);
		autoSwitchSystemTabOnJumpOrScanCheckBox.setSelected(OverlayPreferences.isAutoSwitchSystemTabOnJumpOrScan());
		autoSwitchPanel.add(autoSwitchSystemTabOnJumpOrScanCheckBox, agc);

		agc.gridy++;
		autoSwitchMiningOnPlanetaryRingCheckBox = new JCheckBox("Planetary ring → Mining tab");
		autoSwitchMiningOnPlanetaryRingCheckBox.setOpaque(false);
		autoSwitchMiningOnPlanetaryRingCheckBox.setSelected(OverlayPreferences.isAutoSwitchMiningOnPlanetaryRing());
		autoSwitchPanel.add(autoSwitchMiningOnPlanetaryRingCheckBox, agc);

		agc.gridy++;
		autoSwitchMiningOnStartupPlanetaryRingCheckBox = new JCheckBox("Startup in planetary ring → Mining tab");
		autoSwitchMiningOnStartupPlanetaryRingCheckBox.setOpaque(false);
		autoSwitchMiningOnStartupPlanetaryRingCheckBox.setSelected(OverlayPreferences.isAutoSwitchMiningOnStartupPlanetaryRing());
		autoSwitchPanel.add(autoSwitchMiningOnStartupPlanetaryRingCheckBox, agc);

		agc.gridy++;
		autoSwitchBiologyOnNearBodyCheckBox = new JCheckBox(
				"On planetary surface / near landable body with atmosphere → Biology tab");
		autoSwitchBiologyOnNearBodyCheckBox.setOpaque(false);
		autoSwitchBiologyOnNearBodyCheckBox.setSelected(OverlayPreferences.isAutoSwitchBiologyOnNearLandableAtmosphere());
		autoSwitchPanel.add(autoSwitchBiologyOnNearBodyCheckBox, agc);

		agc.gridy++;
		autoSwitchFleetCarrierOnJsonDropCheckBox = new JCheckBox("Dropping a carrier route file (JSON or CSV) → Fleet Carrier tab");
		autoSwitchFleetCarrierOnJsonDropCheckBox.setOpaque(false);
		autoSwitchFleetCarrierOnJsonDropCheckBox.setSelected(OverlayPreferences.isAutoSwitchFleetCarrierOnJsonDrop());
		autoSwitchPanel.add(autoSwitchFleetCarrierOnJsonDropCheckBox, agc);

		addLeftStackedSection(panel, autoSwitchPanel);

		JPanel routePanel = new JPanel(new GridBagLayout());
		routePanel.setOpaque(false);
		routePanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Route tab"));
		GridBagConstraints rgc = new GridBagConstraints();
		rgc.gridx = 0;
		rgc.gridy = 0;
		rgc.anchor = GridBagConstraints.WEST;
		rgc.insets = new Insets(2, 8, 2, 8);
		routeFuelPredictionCheckBox = new JCheckBox(
				"Predict fuel along the route (green=scoopable; yellow/red=fuel warning; slash=not scoopable)");
		routeFuelPredictionCheckBox.setOpaque(false);
		routeFuelPredictionCheckBox.setSelected(OverlayPreferences.isRouteFuelPredictionEnabled());
		routeFuelPredictionCheckBox.addActionListener(e -> syncRouteFuelScoopPrefEnabled());
		routePanel.add(routeFuelPredictionCheckBox, rgc);
		rgc.gridy++;
		routeFuelPredictionConsiderScoopCheckBox = new JCheckBox(
				"Assume fuel scoop refills the tank at scoopable stars");
		routeFuelPredictionConsiderScoopCheckBox.setOpaque(false);
		routeFuelPredictionConsiderScoopCheckBox.setSelected(
				OverlayPreferences.isRouteFuelPredictionConsiderScoop());
		routeFuelPredictionConsiderScoopCheckBox.setToolTipText(
				"When off, fuel estimates drain continuously even if a fuel scoop is fitted.");
		syncRouteFuelScoopPrefEnabled();
		routePanel.add(routeFuelPredictionConsiderScoopCheckBox, rgc);
		addLeftStackedSection(panel, routePanel);

		finishLeftSectionStack(panel);
		return panel;
	}

	private void syncRouteFuelScoopPrefEnabled() {
		if (routeFuelPredictionConsiderScoopCheckBox == null) {
			return;
		}
		boolean predictionOn = routeFuelPredictionCheckBox == null
				|| routeFuelPredictionCheckBox.isSelected();
		routeFuelPredictionConsiderScoopCheckBox.setEnabled(predictionOn);
	}

	/**
	 * Logging tab: choose between auto-detected live folder and a custom test folder.
	 */
	private JPanel createLoggingPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		JPanel box = new JPanel(new GridBagLayout());
		box.setOpaque(false);
		box.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Journal folder"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		JLabel journalLabel = new JLabel("Use auto-detected ED log folder:");
		autoDetectCheckBox = new JCheckBox();
		autoDetectCheckBox.setOpaque(false);
		boolean auto = OverlayPreferences.isAutoLogDir(clientKey);
		autoDetectCheckBox.setSelected(auto);

		box.add(journalLabel, gbc);
		gbc.gridx = 1;
		box.add(autoDetectCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel pathLabel = new JLabel("Custom journal folder:");
		box.add(pathLabel, gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		JPanel pathPanel = new JPanel(new BorderLayout(4, 0));
		pathPanel.setOpaque(false);

		customPathField = new JTextField(28);
		customPathField.setText(OverlayPreferences.getCustomLogDir(clientKey));

		JButton browseButton = new JButton("Browse.");
		browseButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select Elite Dangerous journal folder");
			String existing = customPathField.getText().trim();
			if (!existing.isEmpty()) {
				File f = new File(existing);
				if (f.isDirectory()) {
					chooser.setCurrentDirectory(f);
				}
			}
			int result = chooser.showOpenDialog(PreferencesDialog.this);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				customPathField.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});

		pathPanel.add(customPathField, BorderLayout.CENTER);
		pathPanel.add(browseButton, BorderLayout.EAST);
		box.add(pathPanel, gbc);

		Runnable updateEnabled = () -> {
			boolean useAuto = autoDetectCheckBox.isSelected();
			customPathField.setEnabled(!useAuto);
			browseButton.setEnabled(!useAuto);
		};
		autoDetectCheckBox.addActionListener(e -> updateEnabled.run());
		updateEnabled.run();

		addLeftStackedSection(panel, box);
		finishLeftSectionStack(panel);
		return panel;
	}


	private JPanel createFontsPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		JPanel box = new JPanel(new GridBagLayout());
		box.setOpaque(false);
		box.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"UI font"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		box.add(new JLabel("Font:"), gbc);
		gbc.gridx = 1;
		// Defer full family enumeration — it is slow on Windows — until Fonts tab is shown.
		String currentFamily = OverlayPreferences.getUiFontName();
		uiFontNameCombo = new JComboBox<>(new String[] { currentFamily });
		uiFontNameCombo.setSelectedItem(currentFamily);
		uiFontNameCombo.setPrototypeDisplayValue("Segoe UI Semibold");
		box.add(uiFontNameCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		box.add(new JLabel("Size:"), gbc);
		gbc.gridx = 1;
		int sz = OverlayPreferences.getUiFontSize();
		uiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(sz, 8, 72, 1));
		((JSpinner.DefaultEditor) uiFontSizeSpinner.getEditor()).getTextField().setColumns(4);
		box.add(uiFontSizeSpinner, gbc);

		uiFontNameCombo.addActionListener(e -> updatePreviewLabelFont());
		uiFontSizeSpinner.addChangeListener(e -> updatePreviewLabelFont());

		addLeftStackedSection(panel, box);
		finishLeftSectionStack(panel);
		return panel;
	}

	private static boolean isFontsTabSelected(int selectedIndex) {
		return selectedIndex == FONTS_TAB_INDEX;
	}

	private void ensureFontFamiliesLoaded() {
		if (fontFamiliesLoaded || uiFontNameCombo == null) {
			return;
		}
		fontFamiliesLoaded = true;
		String selected = (String) uiFontNameCombo.getSelectedItem();
		String[] families = cachedFontFamilies;
		if (families == null) {
			families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
			cachedFontFamilies = families;
		}
		uiFontNameCombo.setModel(new javax.swing.DefaultComboBoxModel<>(families));
		if (selected != null) {
			uiFontNameCombo.setSelectedItem(selected);
		}
	}


	private JPanel createColorsPanel() {
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		initLeftSectionStack(panel);

		int initialMainRgb = OverlayPreferences.getUiMainTextRgb();
		int initialBgRgb = OverlayPreferences.getUiBackgroundRgb();
		int initialSneakerRgb = OverlayPreferences.getUiSneakerRgb();
		int initialPrimaryHighlightRgb = OverlayPreferences.getUiPrimaryHighlightRgb();
		int initialSecondaryHighlightRgb = OverlayPreferences.getUiSecondaryHighlightRgb();

		JPanel themeBox = new JPanel(new GridBagLayout());
		themeBox.setOpaque(false);
		themeBox.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Theme colors"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		themeBox.add(new JLabel("Main text:"), gbc);
		gbc.gridx = 1;
		uiMainTextColorButton = new JButton("Choose...");
		uiMainTextColorButton.setBackground(rgbToColor(initialMainRgb));
		uiMainTextColorButton.setOpaque(true);
		uiMainTextColorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "Choose main text color", uiMainTextColorButton.getBackground());
			if (chosen != null) {
				uiMainTextColorButton.setBackground(chosen);
				applyLiveColorPreviewFromButtons();
			}
		});
		themeBox.add(uiMainTextColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		themeBox.add(new JLabel("Background:"), gbc);
		gbc.gridx = 1;
		uiBackgroundColorButton = new JButton("Choose...");
		uiBackgroundColorButton.setBackground(rgbToColor(initialBgRgb));
		uiBackgroundColorButton.setOpaque(true);
		uiBackgroundColorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "Choose background color", uiBackgroundColorButton.getBackground());
			if (chosen != null) {
				uiBackgroundColorButton.setBackground(chosen);
				applyLiveColorPreviewFromButtons();
			}
		});
		themeBox.add(uiBackgroundColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		themeBox.add(new JLabel("Sneaker (landable icon):"), gbc);
		gbc.gridx = 1;
		uiSneakerColorButton = new JButton("Choose...");
		uiSneakerColorButton.setBackground(rgbToColor(initialSneakerRgb));
		uiSneakerColorButton.setOpaque(true);
		uiSneakerColorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "Choose sneaker color", uiSneakerColorButton.getBackground());
			if (chosen != null) {
				uiSneakerColorButton.setBackground(chosen);
				applyLiveColorPreviewFromButtons();
			}
		});
		themeBox.add(uiSneakerColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		themeBox.add(new JLabel("Primary highlight (complete exob, prospector match):"), gbc);
		gbc.gridx = 1;
		uiPrimaryHighlightColorButton = new JButton("Choose...");
		uiPrimaryHighlightColorButton.setBackground(rgbToColor(initialPrimaryHighlightRgb));
		uiPrimaryHighlightColorButton.setOpaque(true);
		uiPrimaryHighlightColorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "Choose primary highlight color",
					uiPrimaryHighlightColorButton.getBackground());
			if (chosen != null) {
				uiPrimaryHighlightColorButton.setBackground(chosen);
				applyLiveColorPreviewFromButtons();
			}
		});
		themeBox.add(uiPrimaryHighlightColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		themeBox.add(new JLabel("Secondary highlight (exob in progress):"), gbc);
		gbc.gridx = 1;
		uiSecondaryHighlightColorButton = new JButton("Choose...");
		uiSecondaryHighlightColorButton.setBackground(rgbToColor(initialSecondaryHighlightRgb));
		uiSecondaryHighlightColorButton.setOpaque(true);
		uiSecondaryHighlightColorButton.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(this, "Choose secondary highlight color",
					uiSecondaryHighlightColorButton.getBackground());
			if (chosen != null) {
				uiSecondaryHighlightColorButton.setBackground(chosen);
				applyLiveColorPreviewFromButtons();
			}
		});
		themeBox.add(uiSecondaryHighlightColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.WEST;
		JButton resetColorsButton = new JButton("Reset to defaults");
		resetColorsButton.addActionListener(e -> {
			uiMainTextColorButton.setBackground(EdoUi.Defaults.MAIN_TEXT);
			uiBackgroundColorButton.setBackground(EdoUi.Defaults.BACKGROUND);
			uiSneakerColorButton.setBackground(EdoUi.Defaults.SNEAKER);
			uiPrimaryHighlightColorButton.setBackground(EdoUi.Defaults.PRIMARY_HIGHLIGHT);
			uiSecondaryHighlightColorButton.setBackground(EdoUi.Defaults.SECONDARY_HIGHLIGHT);
			applyLiveColorPreviewFromButtons();
		});
		themeBox.add(resetColorsButton, gbc);

		addLeftStackedSection(panel, themeBox);

		JPanel normalPanel = createOverlayAppearanceSection(
				"Overlay background (Normal — mouse mode: clicks on overlay)",
				originalNormalTransparencyPct,
				(slider, valueLabel) -> {
					normalTransparencySlider = slider;
					normalTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(false)
				);
		addLeftStackedSection(panel, normalPanel, 8);

		JPanel ptPanel = createOverlayAppearanceSection(
				"Overlay background (Selective / Full pass-through)",
				originalPassThroughTransparencyPct,
				(slider, valueLabel) -> {
					passThroughTransparencySlider = slider;
					passThroughTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(true)
				);
		addLeftStackedSection(panel, ptPanel, 6);

		JLabel transparencyHint = new JLabel(
				"<html>See-through transparency requires the undecorated overlay window "
						+ "(not the standard titled window). Cycle mouse mode on the title bar "
						+ "(Normal → Selective → Full pass-through): Selective and Full use the "
						+ "pass-through slider; Normal uses the other.</html>");
		addLeftStackedSection(panel, transparencyHint, 0);
		finishLeftSectionStack(panel);
		return panel;
	}

	/**
	 * Stack preference sections top-to-bottom, full width, flush left.
	 * Do not use {@link BoxLayout#Y_AXIS} for this — mixed {@code alignmentX} (glue/struts default
	 * to center) pulls titled boxes toward the middle.
	 */
	private static void initLeftSectionStack(JPanel stack) {
		if (stack == null) {
			return;
		}
		stack.setLayout(new GridBagLayout());
		stack.putClientProperty("edo.prefs.stackRow", Integer.valueOf(0));
	}

	private static void addLeftStackedSection(JPanel stack, JComponent section) {
		addLeftStackedSection(stack, section, 10);
	}

	private static void addLeftStackedSection(JPanel stack, JComponent section, int bottomGapPx) {
		if (stack == null || section == null) {
			return;
		}
		Object raw = stack.getClientProperty("edo.prefs.stackRow");
		int row = raw instanceof Integer i ? i.intValue() : 0;
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = row;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(0, 0, Math.max(0, bottomGapPx), 0);
		stack.add(section, c);
		stack.putClientProperty("edo.prefs.stackRow", Integer.valueOf(row + 1));
	}

	private static void finishLeftSectionStack(JPanel stack) {
		if (stack == null) {
			return;
		}
		Object raw = stack.getClientProperty("edo.prefs.stackRow");
		int row = raw instanceof Integer i ? i.intValue() : 0;
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = row;
		c.weightx = 1.0;
		c.weighty = 1.0;
		c.fill = GridBagConstraints.BOTH;
		stack.add(filler, c);
		stack.putClientProperty("edo.prefs.stackRow", Integer.valueOf(row + 1));
	}
	private void applyLiveColorPreviewFromButtons() {
		if (uiMainTextColorButton == null || uiBackgroundColorButton == null || uiSneakerColorButton == null
				|| uiPrimaryHighlightColorButton == null || uiSecondaryHighlightColorButton == null) {
			return;
		}
		int mainRgb = colorToRgb(uiMainTextColorButton.getBackground());
		int bgRgb = colorToRgb(uiBackgroundColorButton.getBackground());
		int sneakerRgb = colorToRgb(uiSneakerColorButton.getBackground());
		int primaryHiRgb = colorToRgb(uiPrimaryHighlightColorButton.getBackground());
		int secondaryHiRgb = colorToRgb(uiSecondaryHighlightColorButton.getBackground());
		applyLiveColorPreview(mainRgb, bgRgb, sneakerRgb, primaryHiRgb, secondaryHiRgb);
	}

	private void applyLiveColorPreview(int mainRgb, int bgRgb, int sneakerRgb, int primaryHighlightRgb,
			int secondaryHighlightRgb) {
		// Live preview: write to preferences so the existing theme plumbing picks it up.
		// If the user cancels, revertLivePreviewIfNeeded() restores the original values.
		OverlayPreferences.setUiMainTextRgb(mainRgb);
		OverlayPreferences.setUiBackgroundRgb(bgRgb);
		OverlayPreferences.setNormalBackgroundRgb(bgRgb);
		OverlayPreferences.setPassThroughBackgroundRgb(bgRgb);
		OverlayPreferences.setUiSneakerRgb(sneakerRgb);
		OverlayPreferences.setUiPrimaryHighlightRgb(primaryHighlightRgb);
		OverlayPreferences.setUiSecondaryHighlightRgb(secondaryHighlightRgb);
		OverlayPreferences.applyThemeToEdoUi();
		applyDialogChrome(false);
		livePreviewDirty = true;

		if (getOwner() instanceof OverlayUiPreviewHost) {
			OverlayUiPreviewHost f = (OverlayUiPreviewHost) getOwner();

			boolean pt = f.isPassThroughEnabled();
			int pct;
			if (pt) {
				pct = passThroughTransparencySlider != null
						? passThroughTransparencySlider.getValue()
								: originalPassThroughTransparencyPct;
			} else {
				pct = normalTransparencySlider != null
						? normalTransparencySlider.getValue()
								: originalNormalTransparencyPct;
			}

			// Background RGB is driven by the unified theme in the Colors tab.
			// Re-apply fill after theme so mouse-PT transparency survives rebuildTabbedPane().
			f.applyThemeFromPreferences();
			f.applyOverlayBackgroundPreview(pt, bgRgb, pct);
		}
	}

	private JPanel createMiningPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel outer = new JPanel();
		outer.setOpaque(false);
		initLeftSectionStack(outer);

		// -----------------------------------------------------------------
		// Prospector box
		// -----------------------------------------------------------------
		JPanel prospectorBox = new JPanel(new GridBagLayout());
		prospectorBox.setOpaque(false);
		prospectorBox.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
						"Prospector"
						)
				);


		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		JLabel materialsLabel = new JLabel("Materials (comma separated):");
		prospectorBox.add(materialsLabel, gbc);

		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;

		prospectorMaterialsField = new JTextField(32);
		prospectorMaterialsField.setText(OverlayPreferences.getProspectorMaterialsCsv());
		prospectorBox.add(prospectorMaterialsField, gbc);

		gbc.gridx = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0.0;

		JLabel minPropLabel = new JLabel("Min%:");
		prospectorBox.add(minPropLabel, gbc);

		gbc.gridx = 3;
		double currentProp = OverlayPreferences.getProspectorMinProportionPercent();
		prospectorMinPropSpinner = new JSpinner(new SpinnerNumberModel(currentProp, 0.0, 100.0, 1.0));
		((JSpinner.DefaultEditor) prospectorMinPropSpinner.getEditor()).getTextField().setColumns(6);
		prospectorBox.add(prospectorMinPropSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel minAvgValueLabel = new JLabel("Minimum galactic avg value (Cr/t):");
		prospectorBox.add(minAvgValueLabel, gbc);

		gbc.gridx = 1;
		int currentAvg = OverlayPreferences.getProspectorMinAvgValueCrPerTon();
		prospectorMinAvgValueSpinner = new JSpinner(new SpinnerNumberModel(currentAvg, 0, 10_000_000, 1000));
		((JSpinner.DefaultEditor) prospectorMinAvgValueSpinner.getEditor()).getTextField().setColumns(8);
		prospectorBox.add(prospectorMinAvgValueSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 4;
		JLabel hint = new JLabel("Tip: leave materials blank to announce ANY material above the thresholds.");
		prospectorBox.add(hint, gbc);

		addLeftStackedSection(outer, prospectorBox);

		// -----------------------------------------------------------------
		// Log / Spreadsheet (local CSV vs Google Sheets)
		// -----------------------------------------------------------------
		JPanel logBackendBox = new JPanel(new GridBagLayout());
		logBackendBox.setOpaque(false);
		logBackendBox.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
						"Prospector log / Spreadsheet"
						)
				);
		GridBagConstraints gbcLog = new GridBagConstraints();
		gbcLog.gridx = 0;
		gbcLog.gridy = 0;
		gbcLog.anchor = GridBagConstraints.WEST;
		gbcLog.insets = new Insets(6, 8, 6, 8);

		ButtonGroup logBackendGroup = new ButtonGroup();
		miningLogBackendLocalRadio = new JRadioButton("Local CSV (per-commander files in ~/.edo/)");
		miningLogBackendLocalRadio.setOpaque(false);
		miningLogBackendGoogleRadio = new JRadioButton("Google Sheets");
		miningLogBackendGoogleRadio.setOpaque(false);
		miningLogBackendBothRadio = new JRadioButton("Both (CSV + Google Sheets)");
		miningLogBackendBothRadio.setOpaque(false);
		miningLogBackendBothRadio.setToolTipText(
			"Mirror writes to both. Pick which side the Mining table reads from on the Mining tab.");
		logBackendGroup.add(miningLogBackendLocalRadio);
		logBackendGroup.add(miningLogBackendGoogleRadio);
		logBackendGroup.add(miningLogBackendBothRadio);
		String currentBackend = OverlayPreferences.getMiningLogBackend();
		boolean useGoogle = "google".equals(currentBackend);
		boolean useBoth = "both".equals(currentBackend);
		miningLogBackendLocalRadio.setSelected(!useGoogle && !useBoth);
		miningLogBackendGoogleRadio.setSelected(useGoogle);
		miningLogBackendBothRadio.setSelected(useBoth);

		JLabel commanderNameLabel = new JLabel("Commander name:");
		logBackendBox.add(commanderNameLabel, gbcLog);
		gbcLog.gridx = 1;
		gbcLog.fill = GridBagConstraints.HORIZONTAL;
		gbcLog.weightx = 1.0;
		miningLogCommanderNameField = new JTextField(32);
		miningLogCommanderNameField.setText(OverlayPreferences.getMiningLogCommanderName());
		logBackendBox.add(miningLogCommanderNameField, gbcLog);
		gbcLog.gridx = 0;
		gbcLog.gridy++;
		gbcLog.fill = GridBagConstraints.NONE;
		gbcLog.weightx = 0;
		logBackendBox.add(miningLogBackendLocalRadio, gbcLog);
		gbcLog.gridy++;
		logBackendBox.add(miningLogBackendGoogleRadio, gbcLog);
		gbcLog.gridy++;
		logBackendBox.add(miningLogBackendBothRadio, gbcLog);
		gbcLog.gridy++;
		gbcLog.gridx = 0;
		JLabel urlLabel = new JLabel("Google Sheets URL (edit link from browser):");
		logBackendBox.add(urlLabel, gbcLog);
		gbcLog.gridx = 1;
		gbcLog.fill = GridBagConstraints.HORIZONTAL;
		gbcLog.weightx = 1.0;
		miningGoogleSheetsUrlField = new JTextField(40);
		miningGoogleSheetsUrlField.setText(OverlayPreferences.getMiningGoogleSheetsUrl());
		logBackendBox.add(miningGoogleSheetsUrlField, gbcLog);

		gbcLog.gridx = 0;
		gbcLog.gridy++;
		gbcLog.fill = GridBagConstraints.NONE;
		gbcLog.weightx = 0;
		JLabel clientIdLabel = new JLabel("Client ID (from Google Cloud Console):");
		logBackendBox.add(clientIdLabel, gbcLog);
		gbcLog.gridx = 1;
		gbcLog.fill = GridBagConstraints.HORIZONTAL;
		gbcLog.weightx = 1.0;
		miningGoogleClientIdField = new JTextField(36);
		miningGoogleClientIdField.setText(OverlayPreferences.getMiningGoogleSheetsClientId());
		logBackendBox.add(miningGoogleClientIdField, gbcLog);
		gbcLog.gridx = 0;
		gbcLog.gridy++;
		gbcLog.fill = GridBagConstraints.NONE;
		gbcLog.weightx = 0;
		JLabel clientSecretLabel = new JLabel("Client Secret:");
		logBackendBox.add(clientSecretLabel, gbcLog);
		gbcLog.gridx = 1;
		gbcLog.fill = GridBagConstraints.HORIZONTAL;
		gbcLog.weightx = 1.0;
		miningGoogleClientSecretField = new JTextField(24);
		miningGoogleClientSecretField.setText(OverlayPreferences.getMiningGoogleSheetsClientSecret());
		logBackendBox.add(miningGoogleClientSecretField, gbcLog);
		gbcLog.gridx = 0;
		gbcLog.gridy++;
		gbcLog.gridwidth = 2;
		JPanel googleButtonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		googleButtonsRow.setOpaque(false);
		miningGoogleSetupHelpButton = new JButton("How to set up Google Sheets");
		miningGoogleSetupHelpButton.addActionListener(e -> showGoogleSheetsSetupInstructions());
		googleButtonsRow.add(miningGoogleSetupHelpButton);
		miningGoogleConnectButton = new JButton("Connect to Google");
		miningGoogleConnectButton.setEnabled(useGoogle || useBoth);
		miningGoogleConnectButton.addActionListener(e -> connectToGoogleAndStoreToken());
		googleButtonsRow.add(miningGoogleConnectButton);
		logBackendBox.add(googleButtonsRow, gbcLog);
		gbcLog.gridy++;
		JPanel migrateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		migrateRow.setOpaque(false);
		miningGoogleMigrateLegacyButton = new JButton("Migrate first sheet to per-commander tabs…");
		miningGoogleMigrateLegacyButton.setToolTipText(
				"Splits a legacy mixed-commander first worksheet into CMDR … tabs (one per commander). Use if automatic migration failed or you restored an old sheet.");
		miningGoogleMigrateLegacyButton.addActionListener(e -> runMiningSheetLegacyMigration());
		migrateRow.add(miningGoogleMigrateLegacyButton);
		miningGoogleRepairLayoutButton = new JButton("Repair CMDR sheet columns…");
		miningGoogleRepairLayoutButton.setToolTipText(
				"For Google Sheets: rewrites each CMDR … tab (A:Q). Fixes row 1 when it skipped the Ship column (Commander / "
						+ "Start / End only), pads short rows to 16 columns, moves a ship name stuck under Start/End back into "
						+ "Ship when that cell is empty, and clears duplicate ship text in time columns. Make a Drive copy first.");
		miningGoogleRepairLayoutButton.addActionListener(e -> runMiningSheetLayoutRepair());
		migrateRow.add(miningGoogleRepairLayoutButton);
		logBackendBox.add(migrateRow, gbcLog);
		updateMiningGoogleMigrateLegacyButtonEnabled();
		miningGoogleSheetsUrlField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateMiningGoogleMigrateLegacyButtonEnabled();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateMiningGoogleMigrateLegacyButtonEnabled();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateMiningGoogleMigrateLegacyButtonEnabled();
			}
		});
		applyMiningGoogleSpreadsheetFieldEditability(useGoogle || useBoth);
		ActionListener miningBackendRadioChange = ev -> applyMiningGoogleSpreadsheetFieldEditability(
			(miningLogBackendGoogleRadio != null && miningLogBackendGoogleRadio.isSelected())
				|| (miningLogBackendBothRadio != null && miningLogBackendBothRadio.isSelected()));
		miningLogBackendGoogleRadio.addActionListener(miningBackendRadioChange);
		miningLogBackendLocalRadio.addActionListener(miningBackendRadioChange);
		miningLogBackendBothRadio.addActionListener(miningBackendRadioChange);

		addLeftStackedSection(outer, logBackendBox);

		// -----------------------------------------------------------------
		// Limpet reminder thresholds (announcement toggle is on Speech tab)
		// -----------------------------------------------------------------
		JPanel limpetPanel = new JPanel();
		limpetPanel.setOpaque(false);
		initLeftSectionStack(limpetPanel);

		JPanel limpetIntroRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		limpetIntroRow.setOpaque(false);
		limpetIntroRow.add(new JLabel("Low limpet reminder thresholds (announce on Speech tab):"));
		addLeftStackedSection(limpetPanel, limpetIntroRow, 4);

		// Row 1: COUNT (indented)
		JPanel limpetCountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		limpetCountRow.setOpaque(false);
		limpetCountRow.add(Box.createHorizontalStrut(28)); // ~4 spaces indent

		miningLowLimpetReminderCountRadio = new JRadioButton("Remind if limpets <");
		miningLowLimpetReminderCountRadio.setOpaque(false);

		ButtonGroup limpetModeGroup = new ButtonGroup();
		limpetModeGroup.add(miningLowLimpetReminderCountRadio);
		limpetCountRow.add(miningLowLimpetReminderCountRadio);

		int currentCountThreshold = OverlayPreferences.getMiningLowLimpetReminderThreshold();
		miningLowLimpetReminderThresholdSpinner =
				new JSpinner(new SpinnerNumberModel(currentCountThreshold, 0, 10_000, 1));
		JSpinner.DefaultEditor countEd = (JSpinner.DefaultEditor) miningLowLimpetReminderThresholdSpinner.getEditor();
		countEd.getTextField().setColumns(5);
		limpetCountRow.add(miningLowLimpetReminderThresholdSpinner);

		JLabel limpetCountUnitsLabel = new JLabel("limpets");
		limpetCountRow.add(limpetCountUnitsLabel);

		addLeftStackedSection(limpetPanel, limpetCountRow, 4);

		// Row 2: PERCENT (indented)
		JPanel limpetPercentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		limpetPercentRow.setOpaque(false);
		limpetPercentRow.add(Box.createHorizontalStrut(28)); // ~4 spaces indent

		miningLowLimpetReminderPercentRadio = new JRadioButton("Remind if limpets <");
		miningLowLimpetReminderPercentRadio.setOpaque(false);
		limpetModeGroup.add(miningLowLimpetReminderPercentRadio);
		limpetPercentRow.add(miningLowLimpetReminderPercentRadio);

		int currentPercentThreshold = OverlayPreferences.getMiningLowLimpetReminderThresholdPercent();
		miningLowLimpetReminderPercentSpinner =
				new JSpinner(new SpinnerNumberModel(currentPercentThreshold, 0, 100, 1));
		JSpinner.DefaultEditor percentEd = (JSpinner.DefaultEditor) miningLowLimpetReminderPercentSpinner.getEditor();
		percentEd.getTextField().setColumns(5); // same as count (width consistency)
		limpetPercentRow.add(miningLowLimpetReminderPercentSpinner);

		JLabel limpetPercentUnitsLabel = new JLabel("% of cargo capacity");
		limpetPercentRow.add(limpetPercentUnitsLabel);

		addLeftStackedSection(limpetPanel, limpetPercentRow, 0);

		// Force both spinners to the same preferred size (whichever is wider)
		Dimension s1 = miningLowLimpetReminderThresholdSpinner.getPreferredSize();
		Dimension s2 = miningLowLimpetReminderPercentSpinner.getPreferredSize();
		int w = Math.max(s1.width, s2.width);
		int h = Math.max(s1.height, s2.height);
		Dimension same = new Dimension(w, h);
		miningLowLimpetReminderThresholdSpinner.setPreferredSize(same);
		miningLowLimpetReminderPercentSpinner.setPreferredSize(same);

		// Initialize mode selection
		OverlayPreferences.MiningLimpetReminderMode mode = OverlayPreferences.getMiningLowLimpetReminderMode();
		if (mode == OverlayPreferences.MiningLimpetReminderMode.PERCENT) {
			miningLowLimpetReminderPercentRadio.setSelected(true);
		} else {
			miningLowLimpetReminderCountRadio.setSelected(true);
		}

		Runnable updateLimpetEnabled = () -> {
			boolean countSelected = miningLowLimpetReminderCountRadio.isSelected();

			miningLowLimpetReminderCountRadio.setEnabled(true);
			miningLowLimpetReminderPercentRadio.setEnabled(true);

			miningLowLimpetReminderThresholdSpinner.setEnabled(countSelected);
			limpetCountUnitsLabel.setEnabled(countSelected);

			miningLowLimpetReminderPercentSpinner.setEnabled(!countSelected);
			limpetPercentUnitsLabel.setEnabled(!countSelected);
		};

		miningLowLimpetReminderCountRadio.addActionListener(e -> updateLimpetEnabled.run());
		miningLowLimpetReminderPercentRadio.addActionListener(e -> updateLimpetEnabled.run());
		updateLimpetEnabled.run();


		addLeftStackedSection(outer, limpetPanel);

		// -----------------------------------------------------------------
		// Mining scatter gather animation sizes (gun + asteroid line-art)
		// -----------------------------------------------------------------
		JPanel animBox = new JPanel(new GridBagLayout());
		animBox.setOpaque(false);
		animBox.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
						"Mining scatter animation (size %)"
						)
				);
		GridBagConstraints abc = new GridBagConstraints();
		abc.gridx = 0;
		abc.gridy = 0;
		abc.anchor = GridBagConstraints.WEST;
		abc.insets = new Insets(6, 8, 6, 8);
		animBox.add(new JLabel("Gun platform:"), abc);
		abc.gridx = 1;
		miningAnimGunSizeSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getMiningAnimationGunSizePercent(), 25, 400, 5));
		((JSpinner.DefaultEditor) miningAnimGunSizeSpinner.getEditor()).getTextField().setColumns(5);
		animBox.add(miningAnimGunSizeSpinner, abc);
		abc.gridx = 2;
		animBox.add(new JLabel("% (100 = default)"), abc);
		abc.gridx = 0;
		abc.gridy++;
		animBox.add(new JLabel("Asteroid:"), abc);
		abc.gridx = 1;
		miningAnimAsteroidSizeSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getMiningAnimationAsteroidSizePercent(), 25, 400, 5));
		((JSpinner.DefaultEditor) miningAnimAsteroidSizeSpinner.getEditor()).getTextField().setColumns(5);
		animBox.add(miningAnimAsteroidSizeSpinner, abc);
		abc.gridx = 2;
		animBox.add(new JLabel("% (100 = default)"), abc);
		abc.gridx = 0;
		abc.gridy++;
		abc.gridwidth = 3;
		miningAnimShowLaserCheckBox = new JCheckBox("Show laser");
		miningAnimShowLaserCheckBox.setOpaque(false);
		miningAnimShowLaserCheckBox.setSelected(OverlayPreferences.isMiningAnimationShowLaser());
		miningAnimShowLaserCheckBox.setToolTipText(
				"When off, the mining scatter plot hides the gun platform, laser beam, and ore shrapnel during the gather animation.");
		animBox.add(miningAnimShowLaserCheckBox, abc);
		abc.gridy++;
		miningAnimShowAsteroidCheckBox = new JCheckBox("Show asteroid");
		miningAnimShowAsteroidCheckBox.setOpaque(false);
		miningAnimShowAsteroidCheckBox.setSelected(OverlayPreferences.isMiningAnimationShowAsteroid());
		miningAnimShowAsteroidCheckBox.setToolTipText(
				"When off, scatter markers use data points only (no rotating asteroid line-art), including during gather.");
		animBox.add(miningAnimShowAsteroidCheckBox, abc);
		abc.gridy++;
		miningScatterAsteroidIconsAllPointsCheckBox = new JCheckBox("Asteroid icons on all scatter points");
		miningScatterAsteroidIconsAllPointsCheckBox.setOpaque(false);
		miningScatterAsteroidIconsAllPointsCheckBox.setSelected(OverlayPreferences.isMiningScatterAsteroidIconsAllPoints());
		miningScatterAsteroidIconsAllPointsCheckBox.setToolTipText(
				"Draw every prospector log point as a line-art asteroid. Only the current asteroid position(s) "
						+ "for the active run spin; older points stay static. Off by default (plain dots).");
		animBox.add(miningScatterAsteroidIconsAllPointsCheckBox, abc);
		addLeftStackedSection(outer, animBox, 0);
		finishLeftSectionStack(outer);

		panel.add(outer, BorderLayout.CENTER);
		return panel;
	}

	/** Exobiology tab: valuable-bio threshold. */
	private JPanel createExobiologyPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		JPanel box = new JPanel(new GridBagLayout());
		box.setOpaque(false);
		box.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
						"High-value exobiology"
						)
				);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		JLabel valuableBioLabel = new JLabel("Minimum valuable exobiology (M Cr):");
		valuableBioLabel.setToolTipText(
				"<html>Species at or above this estimated payout (million credits) get the money bag on the System tab; "
						+ "also used for first bio-prediction TTS and other exobiology value filters.</html>");
		box.add(valuableBioLabel, gbc);
		gbc.gridx = 1;
		bioValuableThresholdMillionSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getBioValuableThresholdMillionCredits(), 0.0, 1000.0, 0.5));
		((JSpinner.DefaultEditor) bioValuableThresholdMillionSpinner.getEditor()).getTextField().setColumns(6);
		bioValuableThresholdMillionSpinner.setToolTipText(valuableBioLabel.getToolTipText());
		box.add(bioValuableThresholdMillionSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		autoExpandBioOnTargetedBodyCheckBox = new JCheckBox(
				"Auto-expand exobiology when a body is targeted (dashed outline)",
				OverlayPreferences.isAutoExpandBioOnTargetedBody());
		autoExpandBioOnTargetedBodyCheckBox.setOpaque(false);
		autoExpandBioOnTargetedBodyCheckBox.setToolTipText(
				"When enabled, the System tab expands exobiology detail lines for your navigation target and collapses them when the target clears.");
		box.add(autoExpandBioOnTargetedBodyCheckBox, gbc);

		gbc.gridy = 2;
		gbc.gridwidth = 1;
		JLabel biologyMapDisplayLabel = new JLabel("ExoBio map display:");
		biologyMapDisplayLabel.setToolTipText(
				"<html>Rays draw lines from your position to each sample pin with distance and heading.<br>"
						+ "Points place a dot at each sample (clipped to the map edge when off-screen) with the label inside the map.</html>");
		box.add(biologyMapDisplayLabel, gbc);
		gbc.gridx = 1;
		biologyMapDisplayModeComboBox = new JComboBox<>(OverlayPreferences.BiologyMapDisplayMode.values());
		biologyMapDisplayModeComboBox.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(
					JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof OverlayPreferences.BiologyMapDisplayMode mode) {
					setText(mode.displayName());
				}
				return c;
			}
		});
		biologyMapDisplayModeComboBox.setSelectedItem(OverlayPreferences.getBiologyMapDisplayMode());
		biologyMapDisplayModeComboBox.setToolTipText(biologyMapDisplayLabel.getToolTipText());
		box.add(biologyMapDisplayModeComboBox, gbc);

		addLeftStackedSection(panel, box);
		finishLeftSectionStack(panel);
		return panel;
	}

	private void updatePreviewLabelFont() {
		Font f = buildSelectedUiFont();
		if (originalUiFont != null
				&& f.getName().equals(originalUiFont.getName())
				&& f.getSize() == originalUiFont.getSize()) {
			return;
		}
		livePreviewDirty = true;
		applyLivePreview(f);
	}

	private Font buildSelectedUiFont() {
		String name = (String) uiFontNameCombo.getSelectedItem();
		int size = 17;
		try {
			size = ((Number) uiFontSizeSpinner.getValue()).intValue();
		} catch (Exception e) {
			// ignore
		}
		if (name == null || name.isBlank()) {
			name = originalUiFont.getName();
		}
		return new Font(name, Font.PLAIN, size);
	}

	private void applyLivePreview(Font font) {
		if (getOwner() instanceof OverlayUiPreviewHost) {
			((OverlayUiPreviewHost) getOwner()).applyUiFontPreview(font);
		}
	}

	private void applyLivePreviewToOverlay() {
		if (!(getOwner() instanceof OverlayUiPreviewHost)) {
			return;
		}

		String name = (String) uiFontNameCombo.getSelectedItem();
		int size = 17;
		try {
			size = ((Number) uiFontSizeSpinner.getValue()).intValue();
		} catch (Exception e) {
			// ignore
		}

		Font font = new Font(name, Font.PLAIN, size);
		((OverlayUiPreviewHost) getOwner()).applyUiFontPreview(font);
	}

	private static TtsSprintf speechPreferencesPreviewTts() {
		TtsSprintf t = speechPreferencesPreviewTts;
		if (t == null) {
			synchronized (PreferencesDialog.class) {
				t = speechPreferencesPreviewTts;
				if (t == null) {
					t = new TtsSprintf(new PollyTtsCached());
					speechPreferencesPreviewTts = t;
				}
			}
		}
		return t;
	}

	/**
	 * Scroll tall preference bodies inside the tab so dialog OK/Cancel stay pinned.
	 * Uses EDO subtle scroll thumbs (prefs main-text color).
	 */
	private JPanel wrapTabInEdoScroll(JComponent content) {
		Color bg = EdoUi.User.BACKGROUND;
		if (content != null) {
			content.setOpaque(true);
			content.setBackground(bg);
		}
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.setOpaque(true);
		scroll.setBackground(bg);
		scroll.getViewport().setOpaque(true);
		scroll.getViewport().setBackground(bg);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(true);
		wrapper.setBackground(bg);
		wrapper.add(scroll, BorderLayout.CENTER);
		return wrapper;
	}

	private void applyDialogChrome() {
		applyDialogChrome(true);
	}

	/**
	 * @param restyleControls full checkbox/chip/scrollbar restyle (open). Live color previews only need colors.
	 */
	private void applyDialogChrome(boolean restyleControls) {
		if (preferenceTabs == null) {
			return;
		}
		Color bg = EdoUi.User.BACKGROUND;
		Color fg = EdoUi.User.MAIN_TEXT;
		if (getContentPane() instanceof JComponent content) {
			content.setOpaque(true);
			content.setBackground(bg);
		} else {
			getContentPane().setBackground(bg);
		}
		if (getRootPane() != null) {
			getRootPane().setBackground(bg);
			getRootPane().setBorder(BorderFactory.createLineBorder(EdoUi.Internal.TITLEBAR_BG_HOVER, 1));
		}
		setBackground(bg);
		if (titleBar != null) {
			titleBar.refreshTheme();
		}
		preferenceTabs.setOpaque(true);
		preferenceTabs.setBackground(bg);
		preferenceTabs.setForeground(fg);
		EdoLookAndFeel.refreshFromPreferences();
		stylePreferenceTabChrome();
		Font chipFont = OverlayPreferences.getUiFont();
		if (chipFont == null) {
			chipFont = getFont();
		}
		applyPreferenceChromeRecursive(getContentPane(), bg, fg, chipFont, restyleControls);
		if (execTabPanel != null) {
			execTabPanel.applyThemeColors();
		}
		if (restyleControls) {
			styleDialogActionButtons();
		}
		preferenceTabs.revalidate();
		preferenceTabs.repaint();
	}

	/**
	 * One tree walk for surface paint, label colors, chips/checkboxes, and scrollbars.
	 */
	private void applyPreferenceChromeRecursive(
			Component root, Color bg, Color fg, Font chipFont, boolean restyleControls) {
		if (root == null || bg == null) {
			return;
		}
		if (root instanceof JButton button && isColorSwatchButton(button)) {
			if (restyleControls && root instanceof java.awt.Container container) {
				for (Component child : container.getComponents()) {
					applyPreferenceChromeRecursive(child, bg, fg, chipFont, restyleControls);
				}
			}
			return;
		}
		if (!(root instanceof JButton)
				&& !(root instanceof JCheckBox)
				&& !(root instanceof JRadioButton)
				&& !(root instanceof JLabel)
				&& !(root instanceof JTextField)
				&& !(root instanceof JTextArea)
				&& !(root instanceof JComboBox)
				&& !(root instanceof JSpinner)
				&& !(root instanceof JSlider)
				&& !(root instanceof javax.swing.JTable)) {
			if (root instanceof JPanel
					|| root instanceof JScrollPane
					|| root instanceof javax.swing.JViewport
					|| root instanceof JTabbedPane
					|| root instanceof JComponent) {
				root.setBackground(bg);
				if (root instanceof JComponent jc) {
					jc.setOpaque(true);
				}
			}
		}
		if (root instanceof JLabel || root instanceof JCheckBox || root instanceof JRadioButton) {
			root.setForeground(fg);
		}
		if (root instanceof JComponent jc && jc.getBorder() instanceof javax.swing.border.TitledBorder titled) {
			titled.setTitleColor(fg);
		}
		// Fields always get themed (including live color preview); chips/checkboxes only on full restyle.
		if (root instanceof JSpinner spinner) {
			OverlayFieldStyle.applySpinner(spinner, chipFont);
		} else if (root instanceof JTextField textField) {
			// Spinner editors are also JTextFields; style them via the spinner branch above.
			if (!(textField.getParent() instanceof JSpinner.DefaultEditor)
					&& !(SwingUtilities.getAncestorOfClass(JSpinner.class, textField) instanceof JSpinner)) {
				OverlayFieldStyle.applyTextField(textField, chipFont);
			}
		} else if (root instanceof JTextArea textArea) {
			OverlayFieldStyle.applyTextArea(textArea, chipFont);
		} else if (root instanceof JComboBox<?> combo) {
			if (restyleControls) {
				OverlayComboBoxStyle.apply(combo, chipFont);
				OverlayScrollPaneSupport.installSubtleScrollBarsOnComboPopup(combo);
			} else {
				OverlayComboBoxStyle.refreshInk(combo);
			}
		} else if (root instanceof JSlider slider) {
			OverlaySliderUI.apply(slider);
		}
		if (restyleControls) {
			if (root instanceof JCheckBox checkBox) {
				OverlayCheckBoxStyle.apply(checkBox);
			} else if (root instanceof JButton button && shouldStylePreferenceActionButton(button)) {
				OverlayOutlineButtonStyle.applyChip(button, chipFont, false);
			}
			if (root instanceof JScrollPane scrollPane) {
				OverlayScrollPaneSupport.installSubtleScrollBars(scrollPane);
			}
		}
		if (root instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				applyPreferenceChromeRecursive(child, bg, fg, chipFont, restyleControls);
			}
		}
	}

	/**
	 * Apply Engineering-style checkboxes and outline chip buttons across all prefs tabs.
	 * Color swatch “Choose…” buttons keep opaque fills. Help chips get {@link HelpCircleIcon}.
	 */
	private void stylePreferenceTabControls(Component root) {
		Font chipFont = OverlayPreferences.getUiFont();
		if (chipFont == null) {
			chipFont = getFont();
		}
		applyPreferenceChromeRecursive(root, EdoUi.User.BACKGROUND, EdoUi.User.MAIN_TEXT, chipFont, true);
		if (miningGoogleSetupHelpButton != null) {
			HelpCircleIcon.applyTo(miningGoogleSetupHelpButton);
		}
	}

	private boolean shouldStylePreferenceActionButton(JButton button) {
		if (button == null || isColorSwatchButton(button)) {
			return false;
		}
		// Spinner / combo arrows are JButtons with no label — leave native.
		if (button instanceof javax.swing.plaf.basic.BasicArrowButton) {
			return false;
		}
		String text = button.getText();
		return text != null && !text.isBlank();
	}

	private boolean isColorSwatchButton(JButton button) {
		return button == uiMainTextColorButton
				|| button == uiBackgroundColorButton
				|| button == uiSneakerColorButton
				|| button == uiPrimaryHighlightColorButton
				|| button == uiSecondaryHighlightColorButton;
	}

	/** Prefs / nested dialogs: thin main-text thumbs instead of Windows LAF bars. */
	private static void installEdoScrollBars(Component root) {
		if (root instanceof JScrollPane scrollPane) {
			OverlayScrollPaneSupport.installSubtleScrollBars(scrollPane);
		}
		if (root instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				installEdoScrollBars(child);
			}
		}
	}

	private static JScrollPane edoScroll(JScrollPane scrollPane) {
		OverlayScrollPaneSupport.installSubtleScrollBars(scrollPane);
		return scrollPane;
	}

	private void styleDialogActionButtons() {
		Font chipFont = OverlayPreferences.getUiFont();
		if (chipFont == null) {
			chipFont = getFont();
		}
		if (okButton != null) {
			OverlayOutlineButtonStyle.applyChip(okButton, chipFont, false);
		}
		if (cancelButton != null) {
			OverlayOutlineButtonStyle.applyChip(cancelButton, chipFont, false);
		}
	}

	private void stylePreferenceTabChrome() {
		if (preferenceTabs == null) {
			return;
		}
		Color bg = EdoUi.User.BACKGROUND;
		Color fg = EdoUi.User.MAIN_TEXT;
		for (int i = 0; i < preferenceTabs.getTabCount(); i++) {
			preferenceTabs.setBackgroundAt(i, bg);
			preferenceTabs.setForegroundAt(i, fg);
		}
		if (miningGoogleSetupHelpButton != null) {
			HelpCircleIcon.applyTo(miningGoogleSetupHelpButton);
		}
	}

	/** Cancel / window close: run once, and only rebuild the overlay if a live preview ran. */
	private void finishDismissWithoutSave() {
		if (dismissHandled || okPressed) {
			return;
		}
		dismissHandled = true;
		revertLivePreviewIfNeeded();
	}

	private void revertLivePreviewIfNeeded() {
		if (okPressed || !livePreviewDirty) {
			return;
		}
		if (!(getOwner() instanceof OverlayUiPreviewHost)) {
			return;
		}

		OverlayUiPreviewHost f = (OverlayUiPreviewHost) getOwner();

		// Revert theme colors
		OverlayPreferences.setUiMainTextRgb(originalUiMainTextRgb);
		OverlayPreferences.setUiBackgroundRgb(originalUiBackgroundRgb);
		OverlayPreferences.setUiSneakerRgb(originalUiSneakerRgb);
		OverlayPreferences.setUiPrimaryHighlightRgb(originalUiPrimaryHighlightRgb);
		OverlayPreferences.setUiSecondaryHighlightRgb(originalUiSecondaryHighlightRgb);
		OverlayPreferences.applyThemeToEdoUi();
		// Do not restyle this dialog — it is closing.

		// Revert font (clear preview overrides so icon sizing matches saved prefs)
		f.revertUiFontLivePreview(originalUiFont);

		// Revert overlay fill, then rebuild so tabbed pane inherits the restored background.
		boolean pt = f.isPassThroughEnabled();
		int pct = pt ? originalPassThroughTransparencyPct : originalNormalTransparencyPct;
		f.applyOverlayBackgroundPreview(pt, originalUiBackgroundRgb, pct);
		f.applyThemeFromPreferences();
		livePreviewDirty = false;
	}

	private JPanel createCombatPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		addLeftStackedSection(panel, createCombatBountyValueBox(), 8);

		JLabel intro = new JLabel(
				"<html>Choose which Combat-tab command buttons to show. Unchecked commands stay hidden; "
						+ "all scanned ships and kills still appear.</html>");
		intro.setOpaque(false);
		addLeftStackedSection(panel, intro, 6);

		addLeftStackedSection(panel, createCombatCommandCheckboxBox(
				"Targeting commands",
				CombatTabCommands.TARGETING,
				combatTargetingCommandCheckBoxes));
		addLeftStackedSection(panel, createCombatCommandCheckboxBox(
				"Fighter commands",
				CombatTabCommands.FIGHTER,
				combatFighterCommandCheckBoxes), 0);
		return panel;
	}

	private JPanel createCombatBountyValueBox() {
		JPanel box = new JPanel(new GridBagLayout());
		box.setOpaque(false);
		box.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Bounty values"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 8, 4, 8);
		gbc.fill = GridBagConstraints.NONE;

		JLabel announceMinLabel = new JLabel("Announce bounties min value (credits):");
		announceMinLabel.setToolTipText(
				"First-scan speech only at/above this total; KWS additional speech only when the delta is at/above this. "
						+ "Combat lists still show every scanned bounty.");
		box.add(announceMinLabel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		bountyScanValuableThresholdSpinner = new JSpinner(new SpinnerNumberModel(
				Long.valueOf(OverlayPreferences.getBountyScanValuableThresholdCredits()),
				Long.valueOf(0L),
				Long.valueOf(100_000_000L),
				Long.valueOf(1_000L)));
		((JSpinner.DefaultEditor) bountyScanValuableThresholdSpinner.getEditor()).getTextField().setColumns(10);
		bountyScanValuableThresholdSpinner.setToolTipText(announceMinLabel.getToolTipText());
		box.add(bountyScanValuableThresholdSpinner, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.weightx = 0;
		gbc.fill = GridBagConstraints.NONE;
		JLabel highValueLabel = new JLabel("High value bounties (credits):");
		highValueLabel.setToolTipText(
				"Combat TARGET / SCANNED / KILLS rows at or above this total use the secondary highlight color from Theme prefs. "
						+ "Lower bounty rows use the primary highlight.");
		box.add(highValueLabel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		combatHighValueBountySpinner = new JSpinner(new SpinnerNumberModel(
				Long.valueOf(OverlayPreferences.getCombatHighValueBountyCredits()),
				Long.valueOf(0L),
				Long.valueOf(100_000_000L),
				Long.valueOf(1_000L)));
		((JSpinner.DefaultEditor) combatHighValueBountySpinner.getEditor()).getTextField().setColumns(10);
		combatHighValueBountySpinner.setToolTipText(highValueLabel.getToolTipText());
		box.add(combatHighValueBountySpinner, gbc);

		return box;
	}

	private static JPanel createCombatCommandCheckboxBox(
			String title,
			java.util.List<CombatTabCommands.Command> commands,
			java.util.Map<String, JCheckBox> out) {
		out.clear();
		JPanel box = new JPanel(new GridBagLayout());
		box.setOpaque(false);
		box.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				title));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 8, 4, 8);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		for (CombatTabCommands.Command command : commands) {
			JCheckBox cb = new JCheckBox(command.label() + "  (" + command.bindName() + ")");
			cb.setOpaque(false);
			cb.setSelected(OverlayPreferences.isCombatTabCommandVisible(command.bindName()));
			cb.setToolTipText("Show the \"" + command.label() + "\" button on the Combat tab");
			out.put(command.bindName(), cb);
			box.add(cb, gbc);
			gbc.gridy++;
		}
		return box;
	}

	private JPanel createSpeechPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);
		initLeftSectionStack(panel);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);
		content.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
				"Speech"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 8, 6, 8);

		// Enabled
		JLabel enabledLabel = new JLabel("Enable speech (Amazon Polly):");
		speechEnabledCheckBox = new JCheckBox();
		speechEnabledCheckBox.setOpaque(false);
		speechEnabledCheckBox.setSelected(OverlayPreferences.isSpeechEnabled());

		content.add(enabledLabel, gbc);
		gbc.gridx = 1;
		content.add(speechEnabledCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel firstDiscoveredSystemAnnouncementLabel = new JLabel("First discovered system announcement:");
		content.add(firstDiscoveredSystemAnnouncementLabel, gbc);
		gbc.gridx = 1;
		firstDiscoveredSystemAnnouncementCheckBox = new JCheckBox();
		firstDiscoveredSystemAnnouncementCheckBox.setOpaque(false);
		firstDiscoveredSystemAnnouncementCheckBox.setSelected(
				OverlayPreferences.isFirstDiscoveredSystemAnnouncementEnabled());
		content.add(firstDiscoveredSystemAnnouncementCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel bountyScanFirstLabel = new JLabel("Bounty scan announcement:");
		content.add(bountyScanFirstLabel, gbc);
		gbc.gridx = 1;
		bountyScanFirstAnnouncementCheckBox = new JCheckBox();
		bountyScanFirstAnnouncementCheckBox.setOpaque(false);
		bountyScanFirstAnnouncementCheckBox.setSelected(OverlayPreferences.isBountyScanFirstAnnouncementEnabled());
		content.add(bountyScanFirstAnnouncementCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel bountyScanAdditionalLabel = new JLabel("Additional bounty (KWS) announcement:");
		content.add(bountyScanAdditionalLabel, gbc);
		gbc.gridx = 1;
		bountyScanAdditionalAnnouncementCheckBox = new JCheckBox();
		bountyScanAdditionalAnnouncementCheckBox.setOpaque(false);
		bountyScanAdditionalAnnouncementCheckBox.setSelected(
				OverlayPreferences.isBountyScanAdditionalAnnouncementEnabled());
		content.add(bountyScanAdditionalAnnouncementCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel missionProgressAnnouncementLabel = new JLabel("Mission progress announcements:");
		content.add(missionProgressAnnouncementLabel, gbc);
		gbc.gridx = 1;
		missionProgressAnnouncementCheckBox = new JCheckBox();
		missionProgressAnnouncementCheckBox.setOpaque(false);
		missionProgressAnnouncementCheckBox.setSelected(
				OverlayPreferences.isMissionProgressAnnouncementEnabled());
		missionProgressAnnouncementCheckBox.setToolTipText(
				"Combat mission complete and cargo delivery progress.");
		content.add(missionProgressAnnouncementCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel lowLimpetAnnouncementLabel = new JLabel("Low limpet announcement:");
		content.add(lowLimpetAnnouncementLabel, gbc);
		gbc.gridx = 1;
		miningLowLimpetReminderEnabledCheckBox = new JCheckBox();
		miningLowLimpetReminderEnabledCheckBox.setOpaque(false);
		miningLowLimpetReminderEnabledCheckBox.setSelected(OverlayPreferences.isMiningLowLimpetReminderEnabled());
		content.add(miningLowLimpetReminderEnabledCheckBox, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel fighterPilotAnnouncementLabel = new JLabel("Fighter pilot announcement:");
		content.add(fighterPilotAnnouncementLabel, gbc);
		gbc.gridx = 1;
		fighterPilotReminderEnabledCheckBox = new JCheckBox();
		fighterPilotReminderEnabledCheckBox.setOpaque(false);
		fighterPilotReminderEnabledCheckBox.setSelected(OverlayPreferences.isFighterPilotReminderEnabled());
		content.add(fighterPilotReminderEnabledCheckBox, gbc);

		// Voice (keep list small and “safe”) — used for Polly and for offline voice packs
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel voiceLabel = new JLabel("Voice (Standard):");
		content.add(voiceLabel, gbc);

		gbc.gridx = 1;
		speechVoiceCombo = new JComboBox<>(STANDARD_US_ENGLISH_VOICES);
		speechVoiceCombo.setSelectedItem(OverlayPreferences.getSpeechVoiceName());
		content.add(speechVoiceCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel engineLabel = new JLabel("Engine:");
		content.add(engineLabel, gbc);

		gbc.gridx = 1;
		speechEngineCombo = new JComboBox<>(new String[] { "standard", "neural" });
		speechEngineCombo.setSelectedItem(OverlayPreferences.getSpeechEngine());
		content.add(speechEngineCombo, gbc);

		// Cache dir
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel cacheDirLabel = new JLabel("Cache directory:");
		content.add(cacheDirLabel, gbc);

		gbc.gridx = 1;
		JPanel cachePanel = new JPanel(new BorderLayout(4, 0));
		cachePanel.setOpaque(false);

		speechCacheDirField = new JTextField(28);
		speechCacheDirField.setText(OverlayPreferences.getSpeechCacheDir().toString());

		JButton browseCacheButton = new JButton("Browse.");
		browseCacheButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select speech cache folder");
			String existing = speechCacheDirField.getText().trim();
			if (!existing.isEmpty()) {
				File f = new File(existing);
				if (f.isDirectory()) {
					chooser.setCurrentDirectory(f);
				}
			}
			int result = chooser.showOpenDialog(PreferencesDialog.this);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				speechCacheDirField.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});

		JButton clearSpeechCacheButton = new JButton("Clear cache");
		clearSpeechCacheButton.addActionListener(e -> {
			String pathStr = speechCacheDirField.getText().trim();
			if (pathStr.isEmpty()) {
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"Set a cache directory path first.",
						"Speech cache",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			Path root = Path.of(pathStr);
			if (!Files.exists(root)) {
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"The folder does not exist:\n" + root.toAbsolutePath(),
						"Speech cache",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!Files.isDirectory(root)) {
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"Not a directory:\n" + root.toAbsolutePath(),
						"Speech cache",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			int choice = JOptionPane.showConfirmDialog(PreferencesDialog.this,
					"Delete everything inside this folder?\n\n" + root.toAbsolutePath()
							+ "\n\nAll cached speech (every voice) will be removed. The folder itself will remain.",
					"Clear speech cache",
					JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) {
				return;
			}
			try {
				OverlayPreferences.setSpeechCacheDir(pathStr);
				deleteSpeechCacheDirectoryContents(root);
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"Speech cache cleared.",
						"Speech cache",
						JOptionPane.INFORMATION_MESSAGE);
				if (speechEnabledCheckBox != null && speechEnabledCheckBox.isSelected()
						&& speechVoiceCombo != null && speechVoiceCombo.getSelectedItem() != null) {
					String voice = speechVoiceCombo.getSelectedItem().toString();
					VoicePackManager.downloadAndInstallVoicePack(this, voice, null);
				}
			} catch (Exception ex) {
				String msg = ex.getMessage();
				if (msg == null || msg.isBlank()) {
					msg = ex.getClass().getSimpleName();
				}
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"Could not clear cache:\n" + msg,
						"Speech cache",
						JOptionPane.ERROR_MESSAGE);
			}
		});

		JPanel cacheButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		cacheButtons.setOpaque(false);
		cacheButtons.add(browseCacheButton);
		cacheButtons.add(clearSpeechCacheButton);

		cachePanel.add(speechCacheDirField, BorderLayout.CENTER);
		cachePanel.add(cacheButtons, BorderLayout.EAST);
		content.add(cachePanel, gbc);

		// Sample rate (PCM)
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel rateLabel = new JLabel("PCM sample rate (Hz):");
		content.add(rateLabel, gbc);

		gbc.gridx = 1;
		speechSampleRateField = new JTextField(8);
		speechSampleRateField.setText(Integer.toString(OverlayPreferences.getSpeechSampleRateHz()));
		content.add(speechSampleRateField, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel prospectorSampleLabel = new JLabel("Speech test (cycles in-app phrases):");
		content.add(prospectorSampleLabel, gbc);
		gbc.gridx = 1;
		JButton previewProspectorSpeechButton = new JButton("Test Speech");
		previewProspectorSpeechButton.setToolTipText(
				"Each click plays the next sample. Strings match speakf() calls in the source (same set VoiceCacheWarmer warms) "
						+ "so offline packs should already have clips. Uses voice, cache folder, engine, and sample rate from this dialog.");
		previewProspectorSpeechButton.addActionListener(e -> {
			if (!speechEnabledCheckBox.isSelected()) {
				return;
			}
			PreferenceSpeechTestClip clip = PREFERENCE_SPEECH_TEST_CLIPS[
					Math.floorMod(speechPreferenceTestClipIndex++, PREFERENCE_SPEECH_TEST_CLIPS.length)];
			Object selVoice = speechVoiceCombo.getSelectedItem();
			String voiceName = selVoice != null ? selVoice.toString() : null;
			var voicePreview = new PollyTtsCached.SpeechSynthesisVoicePreview(voiceName);
			speechPreferencesPreviewTts().speakfWithSpeechGateArray(
					speechEnabledCheckBox.isSelected(),
					voicePreview,
					clip.template,
					clip.args);
		});
		content.add(previewProspectorSpeechButton, gbc);

		// Enable/disable speech-tab controls from master switch
		Runnable updateSpeechPanelEnabled = () -> {
			boolean speechOn = speechEnabledCheckBox.isSelected();

			voiceLabel.setEnabled(speechOn);
			speechVoiceCombo.setEnabled(speechOn);
			engineLabel.setEnabled(speechOn);
			speechEngineCombo.setEnabled(speechOn);
			firstDiscoveredSystemAnnouncementLabel.setEnabled(speechOn);
			firstDiscoveredSystemAnnouncementCheckBox.setEnabled(speechOn);
			cacheDirLabel.setEnabled(speechOn);
			speechCacheDirField.setEnabled(speechOn);
			browseCacheButton.setEnabled(speechOn);
			clearSpeechCacheButton.setEnabled(speechOn);
			rateLabel.setEnabled(speechOn);
			speechSampleRateField.setEnabled(speechOn);
			prospectorSampleLabel.setEnabled(speechOn);
			previewProspectorSpeechButton.setEnabled(speechOn);
		};
		speechEnabledCheckBox.addActionListener(e -> updateSpeechPanelEnabled.run());
		updateSpeechPanelEnabled.run();

		addLeftStackedSection(panel, content);
		finishLeftSectionStack(panel);
		return panel;
	}

	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		panel.setBorder(new EmptyBorder(8, 8, 8, 8));
		panel.setOpaque(true);
		panel.setBackground(EdoUi.User.BACKGROUND);
		// Pin OK/Cancel so a tall Exec table cannot steal their vertical space.
		panel.setMinimumSize(new Dimension(120, 48));

		okButton = new JButton("OK");
		cancelButton = new JButton("Cancel");
		styleDialogActionButtons();

		okButton.addActionListener(e -> {
			if (!validateMiningGoogleSettingsBeforeSave()) {
				return;
			}
			if (execTabPanel != null) {
				execTabPanel.commitPendingEdits();
			}
			okPressed = true;
			dismissHandled = true;
			boolean colorsChanged = colorsDifferFromOriginal();
			boolean fontChanged = fontDifferFromOriginal();
			boolean transparencyChanged = transparencyDifferFromOriginal();
			applyAndSavePreferences();

			if (getOwner() instanceof OverlayUiPreviewHost) {
				OverlayUiPreviewHost f = (OverlayUiPreviewHost) getOwner();
				// Full overlay tab rebuild is expensive — only when colors need rebaking into components.
				if (colorsChanged) {
					f.applyThemeFromPreferences();
					f.applyOverlayBackgroundFromPreferences(f.isPassThroughEnabled());
					if (fontChanged) {
						f.applyUiFontPreferences();
					}
				} else {
					if (fontChanged) {
						f.applyUiFontPreferences();
					}
					if (transparencyChanged) {
						f.applyOverlayBackgroundFromPreferences(f.isPassThroughEnabled());
					}
				}
				f.refreshSystemTabFromSavedPreferences();
				f.refreshOverlayTabBarFromSavedPreferences();

				if (!f.isPassThroughEnabled()) {
					if (getOwner() instanceof Window) {
						Window w = (Window) getOwner();
						w.setAlwaysOnTop(OverlayPreferences.isNonOverlayAlwaysOnTop()
								&& org.dce.ed.util.EliteWindowFocus.isEliteForeground());
					}
				}
			}

			dispose();
		});

		cancelButton.addActionListener(e -> {
			finishDismissWithoutSave();
			dispose();
		});

		panel.add(cancelButton);
		panel.add(okButton);
		return panel;
	}

	private boolean colorsDifferFromOriginal() {
		if (uiMainTextColorButton == null) {
			return false;
		}
		return colorToRgb(uiMainTextColorButton.getBackground()) != originalUiMainTextRgb
				|| colorToRgb(uiBackgroundColorButton.getBackground()) != originalUiBackgroundRgb
				|| colorToRgb(uiSneakerColorButton.getBackground()) != originalUiSneakerRgb
				|| colorToRgb(uiPrimaryHighlightColorButton.getBackground()) != originalUiPrimaryHighlightRgb
				|| colorToRgb(uiSecondaryHighlightColorButton.getBackground()) != originalUiSecondaryHighlightRgb;
	}

	private boolean fontDifferFromOriginal() {
		if (uiFontNameCombo == null || uiFontSizeSpinner == null || originalUiFont == null) {
			return false;
		}
		Object name = uiFontNameCombo.getSelectedItem();
		int size = ((Number) uiFontSizeSpinner.getValue()).intValue();
		return name == null
				|| !name.toString().equals(originalUiFont.getName())
				|| size != originalUiFont.getSize();
	}

	private boolean transparencyDifferFromOriginal() {
		int normal = normalTransparencySlider != null
				? normalTransparencySlider.getValue()
				: originalNormalTransparencyPct;
		int pt = passThroughTransparencySlider != null
				? passThroughTransparencySlider.getValue()
				: originalPassThroughTransparencyPct;
		return normal != originalNormalTransparencyPct || pt != originalPassThroughTransparencyPct;
	}

	private boolean validateMiningGoogleSettingsBeforeSave() {
		boolean googleSelected = miningLogBackendGoogleRadio != null && miningLogBackendGoogleRadio.isSelected();
		boolean bothSelected = miningLogBackendBothRadio != null && miningLogBackendBothRadio.isSelected();
		if (!googleSelected && !bothSelected) {
			return true;
		}
		String url = miningGoogleSheetsUrlField != null ? miningGoogleSheetsUrlField.getText() : "";
		if (url != null && !url.trim().isEmpty()) {
			return true;
		}
		String which = bothSelected ? "Both" : "Google Sheets";
		EdoOptionDialog.showMessage(
				this,
				which + " is selected but the Google Sheets URL is empty.\n"
						+ "Paste your sheet URL first, or switch back to Local CSV.",
				"Mining preferences",
				JOptionPane.WARNING_MESSAGE);
		return false;
	}

	/**
	 * Google Sheet URL / OAuth fields stay enabled for reliable {@link JTextField#getText()} and visible text.
	 * When Local CSV is selected they are read-only; choosing Google again restores the URL from prefs if the box is empty.
	 */
	private void applyMiningGoogleSpreadsheetFieldEditability(boolean googleSelected) {
		if (googleSelected) {
			refillGoogleSheetUrlFromPrefsIfBlank();
		}
		if (miningGoogleSheetsUrlField != null) {
			miningGoogleSheetsUrlField.setEditable(googleSelected);
			miningGoogleSheetsUrlField.setFocusable(googleSelected);
			miningGoogleSheetsUrlField.setEnabled(true);
		}
		if (miningGoogleClientIdField != null) {
			miningGoogleClientIdField.setEditable(googleSelected);
			miningGoogleClientIdField.setFocusable(googleSelected);
			miningGoogleClientIdField.setEnabled(true);
		}
		if (miningGoogleClientSecretField != null) {
			miningGoogleClientSecretField.setEditable(googleSelected);
			miningGoogleClientSecretField.setFocusable(googleSelected);
			miningGoogleClientSecretField.setEnabled(true);
		}
		if (miningGoogleConnectButton != null) {
			miningGoogleConnectButton.setEnabled(googleSelected);
		}
		if (miningGoogleSetupHelpButton != null) {
			miningGoogleSetupHelpButton.setEnabled(true);
		}
		updateMiningGoogleMigrateLegacyButtonEnabled();
	}

	private void refillGoogleSheetUrlFromPrefsIfBlank() {
		if (miningGoogleSheetsUrlField == null) {
			return;
		}
		String cur = miningGoogleSheetsUrlField.getText();
		if (cur != null && !cur.trim().isEmpty()) {
			return;
		}
		String fromPrefs = OverlayPreferences.getMiningGoogleSheetsUrl();
		if (fromPrefs != null && !fromPrefs.isBlank()) {
			miningGoogleSheetsUrlField.setText(fromPrefs);
		}
	}

	private static void persistNonBlankMiningGoogleField(JTextField field, Consumer<String> prefsSet) {
		if (field == null) {
			return;
		}
		String raw = field.getText();
		if (raw != null && !raw.trim().isEmpty()) {
			prefsSet.accept(raw);
		}
	}

	private void showGoogleSheetsSetupInstructions() {
		String msg = "To use Google Sheets for the prospector log:\n\n"
				+ "1. Open Google Cloud Console: https://console.cloud.google.com/\n"
				+ "2. Create a project (or select an existing one).\n"
				+ "3. Enable the Google Sheets API: APIs & Services → Library → search \"Google Sheets API\" → Enable.\n"
				+ "4. Configure OAuth consent screen: APIs & Services → OAuth consent screen. Choose \"External\" if others will use this. Add your app name and support email.\n"
				+ "5. Create credentials: APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID.\n"
				+ "6. Application type: \"Desktop app\". Name it (e.g. \"RockHound\") and click Create.\n"
				+ "7. Copy the Client ID and Client Secret from the credentials page into the fields above.\n"
				+ "8. Paste your Google Sheet edit URL (from the browser) into the URL field. The sheet should have a header row: Run, Asteroid, Timestamp, Type, %, Before, After, Actual, Core, Duds, System, Body, Commander, Ship, Start time, End time (or the app will append missing columns).\n"
				+ "   Mining data is read only from worksheets whose names start with \"CMDR \" (letters CMDR + space) followed by the commander name, e.g. CMDR Villunus. Other tabs in the same file are ignored.\n"
				+ "9. Click \"Connect to Google\". A browser will open; sign in and allow access. The refresh token is stored so you only need to do this once.\n\n"
				+ "No cost: creating a project and using the Sheets API within normal quotas is free.";
		JTextArea area = new JTextArea(msg, 22, 60);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setForeground(EdoUi.User.MAIN_TEXT);
		area.setBackground(EdoUi.User.BACKGROUND);
		area.setCaretColor(EdoUi.User.MAIN_TEXT);
		EdoOptionDialog.showMessage(this, edoScroll(new JScrollPane(area)), "Google Sheets setup", JOptionPane.INFORMATION_MESSAGE);
	}

	private void connectToGoogleAndStoreToken() {
		String clientId = miningGoogleClientIdField != null ? miningGoogleClientIdField.getText().trim() : "";
		String clientSecret = miningGoogleClientSecretField != null ? miningGoogleClientSecretField.getText().trim() : "";
		if (clientId.isEmpty() || clientSecret.isEmpty()) {
			EdoOptionDialog.showMessage(this, "Enter Client ID and Client Secret first, then click Connect to Google.", "Setup required", JOptionPane.WARNING_MESSAGE);
			return;
		}
		boolean ok = GoogleSheetsAuth.runOAuthFlowAndStoreToken(clientId, clientSecret);
		if (ok) {
			EdoOptionDialog.showMessage(this, "Connected. Your prospector log will sync to the selected Google Sheet.", "Success", JOptionPane.INFORMATION_MESSAGE);
		} else {
			String detail = "Could not complete sign-in. Check Client ID and Secret, and try again.";
			OverlayFrame frame = OverlayFrame.overlayFrame;
			if (frame != null) {
				frame.setMiningSheetsStatusError("Mining preferences: " + detail);
				EdoOptionDialog.showMessage(this,
						"Could not complete sign-in. Details are shown in the overlay status bar.",
						"Connection failed",
						JOptionPane.WARNING_MESSAGE);
			} else {
				EdoOptionDialog.showMessage(this, detail, "Connection failed", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void updateMiningGoogleMigrateLegacyButtonEnabled() {
		if (miningGoogleMigrateLegacyButton == null) {
			return;
		}
		boolean google = miningLogBackendGoogleRadio != null && miningLogBackendGoogleRadio.isSelected();
		String url = miningGoogleSheetsUrlField != null ? miningGoogleSheetsUrlField.getText().trim() : "";
		boolean enable = google && !url.isEmpty();
		miningGoogleMigrateLegacyButton.setEnabled(enable);
		if (miningGoogleRepairLayoutButton != null) {
			miningGoogleRepairLayoutButton.setEnabled(enable);
		}
	}

	private void runMiningSheetLegacyMigration() {
		String url = miningGoogleSheetsUrlField != null ? miningGoogleSheetsUrlField.getText().trim() : "";
		if (url.isEmpty()) {
			EdoOptionDialog.showMessage(this, "Enter the Google Sheets URL first.", "Mining sheet", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int confirm = EdoOptionDialog.showConfirm(this,
				"This reads the first worksheet, creates one tab per commander with runs renumbered 1…n per tab, "
						+ "and replaces the first sheet with a short migration note.\n\n"
						+ "Tip: make a copy in Google Drive first if you want a backup.\n\n"
						+ "Continue?",
				"Migrate mining sheet",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		miningGoogleMigrateLegacyButton.setEnabled(false);
		SwingWorker<ProspectorWriteResult, Void> worker = new SwingWorker<>() {
			@Override
			protected ProspectorWriteResult doInBackground() {
				return new GoogleSheetsBackend(url).migrateLegacySheetToCommanderTabs();
			}

			@Override
			protected void done() {
				updateMiningGoogleMigrateLegacyButtonEnabled();
				try {
					ProspectorWriteResult r = get();
					if (r != null && r.isOk()) {
						EdoOptionDialog.showMessage(PreferencesDialog.this,
								"Migration finished. Commander tabs should hold your rows; the first sheet shows a migration note.\n\n"
										+ "Tip: keep a Drive copy of the spreadsheet if you want a backup.",
								"Mining sheet",
								JOptionPane.INFORMATION_MESSAGE);
						OverlayFrame of = OverlayFrame.overlayFrame;
						if (of != null) {
							of.clearMiningSheetsStatusError();
						}
					} else {
						String msg = r != null ? r.getMessage() : "Unknown error";
						EdoOptionDialog.showMessage(PreferencesDialog.this,
								"Migration failed:\n" + msg,
								"Mining sheet",
								JOptionPane.ERROR_MESSAGE);
						OverlayFrame of = OverlayFrame.overlayFrame;
						if (of != null) {
							of.setMiningSheetsStatusError("Mining sheet migration: " + msg);
						}
					}
				} catch (Exception ex) {
					String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
					EdoOptionDialog.showMessage(PreferencesDialog.this,
							"Migration failed:\n" + msg,
							"Mining sheet",
							JOptionPane.ERROR_MESSAGE);
					OverlayFrame of = OverlayFrame.overlayFrame;
					if (of != null) {
						of.setMiningSheetsStatusError("Mining sheet migration: " + msg);
					}
				}
			}
		};
		worker.execute();
	}

	private void runMiningSheetLayoutRepair() {
		String url = miningGoogleSheetsUrlField != null ? miningGoogleSheetsUrlField.getText().trim() : "";
		if (url.isEmpty()) {
			EdoOptionDialog.showMessage(this, "Enter the Google Sheets URL first.", "Mining sheet",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		int confirm = EdoOptionDialog.showConfirm(this,
				"This rewrites every worksheet whose name starts with \"CMDR \" (columns A through P).\n"
						+ "If row 1 is missing the Ship column (Commander then Start time), it is corrected to the standard header.\n"
						+ "Short rows are padded to 16 columns; Start/End cells that only repeat the ship name (no real date) are cleared.\n\n"
						+ "Missing start/end times are not inferred from journals by this action.\n\n"
						+ "Tip: make a copy in Google Drive first.\n\n"
						+ "Continue?",
				"Repair mining sheets",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.OK_OPTION) {
			return;
		}
		if (miningGoogleRepairLayoutButton != null) {
			miningGoogleRepairLayoutButton.setEnabled(false);
		}
		if (miningGoogleMigrateLegacyButton != null) {
			miningGoogleMigrateLegacyButton.setEnabled(false);
		}
		SwingWorker<ProspectorWriteResult, Void> worker = new SwingWorker<>() {
			@Override
			protected ProspectorWriteResult doInBackground() {
				return new GoogleSheetsBackend(url).repairAllCmdrMiningWorksheetLayoutsResult();
			}

			@Override
			protected void done() {
				updateMiningGoogleMigrateLegacyButtonEnabled();
				try {
					ProspectorWriteResult r = get();
					if (r != null && r.isOk()) {
						EdoOptionDialog.showMessage(PreferencesDialog.this,
								"Repair finished. Open your spreadsheet and confirm CMDR tabs look correct.",
								"Mining sheet",
								JOptionPane.INFORMATION_MESSAGE);
						OverlayFrame of = OverlayFrame.overlayFrame;
						if (of != null) {
							of.clearMiningSheetsStatusError();
						}
					} else {
						String msg = r != null ? r.getMessage() : "Unknown error";
						EdoOptionDialog.showMessage(PreferencesDialog.this,
								"Repair failed:\n" + msg,
								"Mining sheet",
								JOptionPane.ERROR_MESSAGE);
						OverlayFrame of = OverlayFrame.overlayFrame;
						if (of != null) {
							of.setMiningSheetsStatusError("Mining sheet repair: " + msg);
						}
					}
				} catch (Exception ex) {
					String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
					EdoOptionDialog.showMessage(PreferencesDialog.this,
							"Repair failed:\n" + msg,
							"Mining sheet",
							JOptionPane.ERROR_MESSAGE);
					OverlayFrame of = OverlayFrame.overlayFrame;
					if (of != null) {
						of.setMiningSheetsStatusError("Mining sheet repair: " + msg);
					}
				}
			}
		};
		worker.execute();
	}

    private void applyAndSavePreferences() {
        // Overlay / Colors tabs
        if (normalTransparencySlider != null) {
            OverlayPreferences.setNormalTransparencyPercent(normalTransparencySlider.getValue());
        }
        if (passThroughTransparencySlider != null) {
            OverlayPreferences.setPassThroughTransparencyPercent(passThroughTransparencySlider.getValue());
        }
        if (passThroughHotkeyCombo != null && passThroughHotkeyCombo.getSelectedItem() != null) {
            int keyCode = ExecShortcutKeys.fromDisplayString(passThroughHotkeyCombo.getSelectedItem().toString());
            OverlayPreferences.setPassThroughToggleKeyCode(keyCode);
        }
        if (nextShownTabHotkeyCombo != null && nextShownTabHotkeyCombo.getSelectedItem() != null) {
            int keyCode = ExecShortcutKeys.fromDisplayString(nextShownTabHotkeyCombo.getSelectedItem().toString());
            OverlayPreferences.setNextShownTabKeyCode(keyCode);
        }

        if (nonOverlayAlwaysOnTopCheckBox != null) {
            OverlayPreferences.setNonOverlayAlwaysOnTop(nonOverlayAlwaysOnTopCheckBox.isSelected());
        }

        if (overlayTabRouteVisibleCheckBox != null) {
            boolean r = overlayTabRouteVisibleCheckBox.isSelected();
            boolean s = overlayTabSystemVisibleCheckBox != null && overlayTabSystemVisibleCheckBox.isSelected();
            boolean b = overlayTabBiologyVisibleCheckBox != null && overlayTabBiologyVisibleCheckBox.isSelected();
            boolean m = overlayTabMiningVisibleCheckBox != null && overlayTabMiningVisibleCheckBox.isSelected();
            boolean ms = overlayTabMissionsVisibleCheckBox != null && overlayTabMissionsVisibleCheckBox.isSelected();
            boolean combat = overlayTabCombatVisibleCheckBox != null && overlayTabCombatVisibleCheckBox.isSelected();
            boolean f = overlayTabFleetCarrierVisibleCheckBox != null && overlayTabFleetCarrierVisibleCheckBox.isSelected();
            boolean eng = overlayTabEngineeringVisibleCheckBox != null && overlayTabEngineeringVisibleCheckBox.isSelected();
            boolean cp = overlayTabControlPanelVisibleCheckBox != null && overlayTabControlPanelVisibleCheckBox.isSelected();
            if (!r && !s && !b && !m && !ms && !combat && !f && !eng && !cp) {
                r = s = b = m = ms = combat = f = eng = cp = true;
            }
            OverlayPreferences.setOverlayTabRouteVisible(r);
            OverlayPreferences.setOverlayTabSystemVisible(s);
            OverlayPreferences.setOverlayTabBiologyVisible(b);
            OverlayPreferences.setOverlayTabMiningVisible(m);
            OverlayPreferences.setOverlayTabMissionsVisible(ms);
            OverlayPreferences.setOverlayTabCombatVisible(combat);
            OverlayPreferences.setOverlayTabFleetCarrierVisible(f);
            OverlayPreferences.setOverlayTabEngineeringVisible(eng);
            OverlayPreferences.setOverlayTabControlPanelVisible(cp);
        }

        for (java.util.Map.Entry<String, JCheckBox> e : combatTargetingCommandCheckBoxes.entrySet()) {
            if (e.getValue() != null) {
                OverlayPreferences.setCombatTabCommandVisible(e.getKey(), e.getValue().isSelected());
            }
        }
        for (java.util.Map.Entry<String, JCheckBox> e : combatFighterCommandCheckBoxes.entrySet()) {
            if (e.getValue() != null) {
                OverlayPreferences.setCombatTabCommandVisible(e.getKey(), e.getValue().isSelected());
            }
        }

        if (autoSwitchGalaxyMapToRouteCheckBox != null) {
            OverlayPreferences.setAutoSwitchRouteOnGalaxyMap(autoSwitchGalaxyMapToRouteCheckBox.isSelected());
        }
        if (routeFuelPredictionCheckBox != null) {
            OverlayPreferences.setRouteFuelPredictionEnabled(routeFuelPredictionCheckBox.isSelected());
        }
        if (routeFuelPredictionConsiderScoopCheckBox != null) {
            OverlayPreferences.setRouteFuelPredictionConsiderScoop(
                    routeFuelPredictionConsiderScoopCheckBox.isSelected());
        }
        if (autoSwitchSystemMapToSystemCheckBox != null) {
            OverlayPreferences.setAutoSwitchSystemOnSystemMap(autoSwitchSystemMapToSystemCheckBox.isSelected());
        }
        if (autoSwitchTabOnFsdTargetCheckBox != null) {
            OverlayPreferences.setAutoSwitchTabOnFsdTarget(autoSwitchTabOnFsdTargetCheckBox.isSelected());
        }
        if (autoSwitchSystemTabOnJumpOrScanCheckBox != null) {
            OverlayPreferences.setAutoSwitchSystemTabOnJumpOrScan(autoSwitchSystemTabOnJumpOrScanCheckBox.isSelected());
        }
        if (autoSwitchMiningOnPlanetaryRingCheckBox != null) {
            OverlayPreferences.setAutoSwitchMiningOnPlanetaryRing(autoSwitchMiningOnPlanetaryRingCheckBox.isSelected());
        }
        if (autoSwitchMiningOnStartupPlanetaryRingCheckBox != null) {
            OverlayPreferences.setAutoSwitchMiningOnStartupPlanetaryRing(autoSwitchMiningOnStartupPlanetaryRingCheckBox.isSelected());
        }
        if (autoSwitchBiologyOnNearBodyCheckBox != null) {
            OverlayPreferences.setAutoSwitchBiologyOnNearLandableAtmosphere(autoSwitchBiologyOnNearBodyCheckBox.isSelected());
        }
        if (autoSwitchFleetCarrierOnJsonDropCheckBox != null) {
            OverlayPreferences.setAutoSwitchFleetCarrierOnJsonDrop(autoSwitchFleetCarrierOnJsonDropCheckBox.isSelected());
        }
        if (systemTabShipRefModeComboBox != null) {
            Object sel = systemTabShipRefModeComboBox.getSelectedItem();
            if (sel instanceof SystemTabShipRefMode) {
                OverlayPreferences.setSystemTabShipRefMode((SystemTabShipRefMode) sel);
            }
        }
        if (systemPlanMapAutoZoomHudTargetCheckBox != null) {
            OverlayPreferences.setSystemPlanMapAutoZoomHudTargetSubsystem(
                    systemPlanMapAutoZoomHudTargetCheckBox.isSelected());
        }

        // Logging tab
        if (autoDetectCheckBox != null && customPathField != null) {
            boolean auto = autoDetectCheckBox.isSelected();
            OverlayPreferences.setAutoLogDir(clientKey, auto);
            if (!auto) {
                OverlayPreferences.setCustomLogDir(clientKey, customPathField.getText().trim());
            }
        }

        // Speech tab
        if (speechEnabledCheckBox != null) {
            OverlayPreferences.setSpeechEnabled(speechEnabledCheckBox.isSelected());
        }

        if (firstDiscoveredSystemAnnouncementCheckBox != null) {
            OverlayPreferences.setFirstDiscoveredSystemAnnouncementEnabled(
                    firstDiscoveredSystemAnnouncementCheckBox.isSelected());
        }

        if (bountyScanFirstAnnouncementCheckBox != null) {
            OverlayPreferences.setBountyScanFirstAnnouncementEnabled(
                    bountyScanFirstAnnouncementCheckBox.isSelected());
        }

        if (bountyScanValuableThresholdSpinner != null) {
            try {
                long v = ((Number) bountyScanValuableThresholdSpinner.getValue()).longValue();
                OverlayPreferences.setBountyScanValuableThresholdCredits(v);
            } catch (Exception ignored) {
            }
        }

        if (combatHighValueBountySpinner != null) {
            try {
                long v = ((Number) combatHighValueBountySpinner.getValue()).longValue();
                OverlayPreferences.setCombatHighValueBountyCredits(v);
            } catch (Exception ignored) {
            }
        }

        if (bountyScanAdditionalAnnouncementCheckBox != null) {
            OverlayPreferences.setBountyScanAdditionalAnnouncementEnabled(
                    bountyScanAdditionalAnnouncementCheckBox.isSelected());
        }

        if (missionProgressAnnouncementCheckBox != null) {
            OverlayPreferences.setMissionProgressAnnouncementEnabled(
                    missionProgressAnnouncementCheckBox.isSelected());
        }

        if (miningLowLimpetReminderEnabledCheckBox != null) {
            OverlayPreferences.setMiningLowLimpetReminderEnabled(miningLowLimpetReminderEnabledCheckBox.isSelected());
        }

        if (fighterPilotReminderEnabledCheckBox != null) {
            OverlayPreferences.setFighterPilotReminderEnabled(fighterPilotReminderEnabledCheckBox.isSelected());
        }

        if (speechEngineCombo != null && speechEngineCombo.getSelectedItem() != null) {
            OverlayPreferences.setSpeechEngine(speechEngineCombo.getSelectedItem().toString());
        }

        if (speechCacheDirField != null) {
            OverlayPreferences.setSpeechCacheDir(speechCacheDirField.getText().trim());
        }

        if (speechVoiceCombo != null && speechVoiceCombo.getSelectedItem() != null) {
            String newVoice = speechVoiceCombo.getSelectedItem().toString();
            OverlayPreferences.setSpeechVoiceId(newVoice);

            // If this voice has no local WAV cache (e.g. user cleared the folder), try GitHub pack download
            if (!VoicePackManager.isVoicePackInstalled(newVoice)) {
                VoicePackManager.downloadAndInstallVoicePack(this, newVoice, null);
            }
        }

        if (speechSampleRateField != null) {
            String s = speechSampleRateField.getText().trim();
            try {
                int hz = Integer.parseInt(s);
                OverlayPreferences.setSpeechSampleRateHz(hz);
            } catch (Exception e) {
                // ignore, keep previous/default
            }
        }

        // Fonts
        if (uiFontNameCombo != null) {
            Object sel = uiFontNameCombo.getSelectedItem();
            if (sel != null) {
                OverlayPreferences.setUiFontName(sel.toString());
            }
        }
        if (uiFontSizeSpinner != null) {
            try {
                int sz = ((Number) uiFontSizeSpinner.getValue()).intValue();
                OverlayPreferences.setUiFontSize(sz);
            } catch (Exception e) {
                // ignore
            }
        }


        // Colors
        if (uiMainTextColorButton != null) {
            OverlayPreferences.setUiMainTextRgb(colorToRgb(uiMainTextColorButton.getBackground()));
        }
        if (uiBackgroundColorButton != null) {
            int rgb = colorToRgb(uiBackgroundColorButton.getBackground());
            OverlayPreferences.setUiBackgroundRgb(rgb);

            // Keep the overlay background in sync with the UI theme background.
            OverlayPreferences.setNormalBackgroundRgb(rgb);
            OverlayPreferences.setPassThroughBackgroundRgb(rgb);
        }
        if (uiSneakerColorButton != null) {
            OverlayPreferences.setUiSneakerRgb(colorToRgb(uiSneakerColorButton.getBackground()));
        }
        if (uiPrimaryHighlightColorButton != null) {
            OverlayPreferences.setUiPrimaryHighlightRgb(colorToRgb(uiPrimaryHighlightColorButton.getBackground()));
        }
        if (uiSecondaryHighlightColorButton != null) {
            OverlayPreferences.setUiSecondaryHighlightRgb(colorToRgb(uiSecondaryHighlightColorButton.getBackground()));
        }

        // Mining
        if (prospectorMaterialsField != null) {
            OverlayPreferences.setProspectorMaterialsCsv(prospectorMaterialsField.getText());
        }
        if (prospectorMinPropSpinner != null) {
            try {
                double p = ((Number) prospectorMinPropSpinner.getValue()).doubleValue();
                OverlayPreferences.setProspectorMinProportionPercent(p);
            } catch (Exception e) {
                // ignore
            }
        }

        if (prospectorMinAvgValueSpinner != null) {
            try {
                int v = ((Number) prospectorMinAvgValueSpinner.getValue()).intValue();
                OverlayPreferences.setProspectorMinAvgValueCrPerTon(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningLogCommanderNameField != null) {
            OverlayPreferences.setMiningLogCommanderName(miningLogCommanderNameField.getText());
        }
        if (miningLogBackendLocalRadio != null && miningLogBackendGoogleRadio != null) {
            String prevBackend = OverlayPreferences.getMiningLogBackend();
            String newBackend;
            if (miningLogBackendBothRadio != null && miningLogBackendBothRadio.isSelected()) {
                newBackend = "both";
            } else if (miningLogBackendGoogleRadio.isSelected()) {
                newBackend = "google";
            } else {
                newBackend = "local";
            }
            // When toggling off Both, clear the per-commander synced-once flags so re-enabling Both runs the auto-sync again.
            if ("both".equals(prevBackend) && !"both".equals(newBackend)) {
                OverlayPreferences.clearAllMiningLogBothSyncedOnce();
            }
            OverlayPreferences.setMiningLogBackend(newBackend);
        }
        // Merge-save: never persist an empty field over an existing stored URL/credentials (disabled fields on some
        // LAFs used to yield blank text; Local CSV vs Google must not wipe the saved sheet link).
        persistNonBlankMiningGoogleField(miningGoogleSheetsUrlField, OverlayPreferences::setMiningGoogleSheetsUrl);
        persistNonBlankMiningGoogleField(miningGoogleClientIdField, OverlayPreferences::setMiningGoogleSheetsClientId);
        persistNonBlankMiningGoogleField(miningGoogleClientSecretField, OverlayPreferences::setMiningGoogleSheetsClientSecret);

        if (miningLowLimpetReminderCountRadio != null && miningLowLimpetReminderPercentRadio != null) {
            if (miningLowLimpetReminderPercentRadio.isSelected()) {
                OverlayPreferences.setMiningLowLimpetReminderMode(OverlayPreferences.MiningLimpetReminderMode.PERCENT);
            } else {
                OverlayPreferences.setMiningLowLimpetReminderMode(OverlayPreferences.MiningLimpetReminderMode.COUNT);
            }
        }

        if (miningLowLimpetReminderThresholdSpinner != null) {
            try {
                int v = ((Number) miningLowLimpetReminderThresholdSpinner.getValue()).intValue();
                OverlayPreferences.setMiningLowLimpetReminderThreshold(v);
            } catch (Exception e) {
                // ignore
            }
        }

        if (miningLowLimpetReminderPercentSpinner != null) {
            try {
                int v = ((Number) miningLowLimpetReminderPercentSpinner.getValue()).intValue();
                OverlayPreferences.setMiningLowLimpetReminderThresholdPercent(v);
            } catch (Exception e) {
                // ignore
            }
        }

        if (miningAnimGunSizeSpinner != null) {
            try {
                int v = ((Number) miningAnimGunSizeSpinner.getValue()).intValue();
                OverlayPreferences.setMiningAnimationGunSizePercent(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningAnimAsteroidSizeSpinner != null) {
            try {
                int v = ((Number) miningAnimAsteroidSizeSpinner.getValue()).intValue();
                OverlayPreferences.setMiningAnimationAsteroidSizePercent(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningAnimShowLaserCheckBox != null) {
            OverlayPreferences.setMiningAnimationShowLaser(miningAnimShowLaserCheckBox.isSelected());
        }
        if (miningAnimShowAsteroidCheckBox != null) {
            OverlayPreferences.setMiningAnimationShowAsteroid(miningAnimShowAsteroidCheckBox.isSelected());
        }
        if (miningScatterAsteroidIconsAllPointsCheckBox != null) {
            OverlayPreferences.setMiningScatterAsteroidIconsAllPoints(miningScatterAsteroidIconsAllPointsCheckBox.isSelected());
        }

        if (bioValuableThresholdMillionSpinner != null) {
            try {
                double v = ((Number) bioValuableThresholdMillionSpinner.getValue()).doubleValue();
                OverlayPreferences.setBioValuableThresholdMillionCredits(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (autoExpandBioOnTargetedBodyCheckBox != null) {
            OverlayPreferences.setAutoExpandBioOnTargetedBody(autoExpandBioOnTargetedBodyCheckBox.isSelected());
        }
        if (biologyMapDisplayModeComboBox != null) {
            Object selected = biologyMapDisplayModeComboBox.getSelectedItem();
            if (selected instanceof OverlayPreferences.BiologyMapDisplayMode mode) {
                OverlayPreferences.setBiologyMapDisplayMode(mode);
            }
        }

        OverlayPreferences.flushBackingStore();
    }

	/**
	 * Removes all files and subfolders under {@code dir}; leaves {@code dir} itself.
	 */
	private static void deleteSpeechCacheDirectoryContents(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			for (Path child : stream.toList()) {
				deletePathRecursive(child);
			}
		}
	}

	private static void deletePathRecursive(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			try (Stream<Path> walk = Files.walk(path)) {
				for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(p);
				}
			}
		} else {
			Files.deleteIfExists(path);
		}
	}

		private void applyLiveOverlayBackgroundPreview(boolean passThroughSection) {
			if (!(getOwner() instanceof OverlayUiPreviewHost)) {
				return;
			}

			OverlayUiPreviewHost f = (OverlayUiPreviewHost) getOwner();
			// Only preview the section that corresponds to the overlay's current mode.
			if (f.isPassThroughEnabled() != passThroughSection) {
				return;
			}

			// Background RGB is driven by the unified theme in the Colors tab.
			int rgb = OverlayPreferences.getUiBackgroundRgb();
			int pct;
			if (passThroughSection) {
				pct = passThroughTransparencySlider != null ? passThroughTransparencySlider.getValue() : 100;
			} else {
				pct = normalTransparencySlider != null ? normalTransparencySlider.getValue() : 100;
			}

			f.applyOverlayBackgroundPreview(passThroughSection, rgb, pct);
			livePreviewDirty = true;
		}

		private interface OverlaySectionBinder {
			void bind(JSlider transparencySlider, JLabel transparencyValueLabel);
		}

		private JPanel createOverlayAppearanceSection(
				String title,
				int initialTransparencyPct,
				OverlaySectionBinder binder,
				Runnable onPreview
				) {
			JPanel panel = new JPanel(new GridBagLayout());
			panel.setOpaque(false);
			panel.setBorder(BorderFactory.createTitledBorder(
					BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
					title));

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(6, 8, 6, 8);

			panel.add(new JLabel("Background transparency:"), gbc);

			gbc.gridx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			// Cap track to ~half the dialog; trailing glue absorbs the rest.
			gbc.weightx = 0.5;
			JSlider slider = new JSlider(0, 100, clampPct(initialTransparencyPct));
			slider.setPaintTicks(true);
			slider.setMajorTickSpacing(25);
			slider.setMinorTickSpacing(5);
			OverlaySliderUI.apply(slider);
			panel.add(slider, gbc);

			gbc.gridx = 2;
			gbc.fill = GridBagConstraints.NONE;
			gbc.weightx = 0;
			JLabel valueLabel = new JLabel(slider.getValue() + "%");
			panel.add(valueLabel, gbc);

			gbc.gridx = 3;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 0.5;
			JPanel sliderSpacer = new JPanel();
			sliderSpacer.setOpaque(false);
			panel.add(sliderSpacer, gbc);

			slider.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					valueLabel.setText(slider.getValue() + "%");
					if (!slider.getValueIsAdjusting() && onPreview != null) {
						onPreview.run();
					}
				}
			});

			if (binder != null) {
				binder.bind(slider, valueLabel);
			}

			return panel;
		}

		private static int clampPct(int pct) {
			if (pct < 0) {
				return 0;
			}
			if (pct > 100) {
				return 100;
			}
			return pct;
		}

		private static Color rgbToColor(int rgb) {
			return EdoUi.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
		}

		private static int colorToRgb(Color c) {
			if (c == null) {
				return 0x000000;
			}
			return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
		}

}
