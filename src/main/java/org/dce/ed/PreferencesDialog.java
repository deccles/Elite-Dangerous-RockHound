package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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

import org.dce.ed.mining.GoogleSheetsAuth;
import org.dce.ed.mining.GoogleSheetsBackend;
import org.dce.ed.mining.ProspectorWriteResult;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;
import org.dce.ed.tts.VoiceCacheWarmer;
import org.dce.ed.tts.VoicePackManager;

/**
 * Preferences dialog for the overlay.
 */
public class PreferencesDialog extends JDialog {

	/** Index of the Mining tab in {@link #PreferencesDialog(Window, String)}'s tabbed pane (Colors, Exobiology, Fonts, Logging, Mining, …). */
	public static final int MINING_TAB_INDEX = 4;

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

	public final String clientKey;

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

	// Logging-tab fields so OK can read them
	private JCheckBox autoDetectCheckBox;
	private JTextField customPathField;

	// Speech-tab fields so OK can read them
	private JCheckBox speechEnabledCheckBox;
	private JCheckBox speechUseAwsCheckBox;
	private JComboBox<String> speechEngineCombo;
	private JComboBox<String> speechVoiceCombo;
	private JTextField speechRegionField;
	private JTextField speechAwsProfileField;
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

	// Mining tab: log / spreadsheet backend (local vs Google Sheets)
	private JRadioButton miningLogBackendLocalRadio;
	private JRadioButton miningLogBackendGoogleRadio;
	private JTextField miningGoogleSheetsUrlField;
	private JTextField miningGoogleClientIdField;
	private JTextField miningGoogleClientSecretField;
	private JButton miningGoogleConnectButton;
	private JButton miningGoogleSetupHelpButton;
	private JButton miningGoogleMigrateLegacyButton;
	private JButton miningGoogleRepairLayoutButton;

	// Mining tab: limpet reminder
	private JCheckBox miningLowLimpetReminderEnabledCheckBox;
	private JRadioButton miningLowLimpetReminderCountRadio;
	private JSpinner miningLowLimpetReminderThresholdSpinner;
	private JRadioButton miningLowLimpetReminderPercentRadio;
	private JSpinner miningLowLimpetReminderPercentSpinner;
	private JSpinner miningAnimGunSizeSpinner;
	private JSpinner miningAnimAsteroidSizeSpinner;
	private JCheckBox miningAnimShowLaserCheckBox;
	private JCheckBox miningAnimShowAsteroidCheckBox;

	private JCheckBox overlayTabRouteVisibleCheckBox;
	private JCheckBox overlayTabSystemVisibleCheckBox;
	private JCheckBox overlayTabBiologyVisibleCheckBox;
	private JCheckBox overlayTabMiningVisibleCheckBox;
	private JCheckBox overlayTabFleetCarrierVisibleCheckBox;

	private JSpinner bioValuableThresholdMillionSpinner;
	private JCheckBox autoExpandBioOnTargetedBodyCheckBox;

	/** Root tabbed pane (Colors, Exobiology, …); used to jump to a specific tab from helpers. */
	private JTabbedPane preferenceTabs;

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
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		setMinimumSize(new Dimension(560, 380));

		this.preferenceTabs = new JTabbedPane();
		preferenceTabs.addTab("Colors", createColorsPanel());
		preferenceTabs.addTab("Exobiology", createExobiologyPanel());
		preferenceTabs.addTab("Fonts", createFontsPanel());
		preferenceTabs.addTab("Logging", createLoggingPanel());
		preferenceTabs.addTab("Mining", createMiningPanel());
		preferenceTabs.addTab("Overlay", createOverlayPanel());
		preferenceTabs.addTab("Speech", createSpeechPanel());

		add(preferenceTabs, BorderLayout.CENTER);
		add(createButtonPanel(), BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(owner);

		// If the user closes the dialog or hits Cancel, revert any live preview.
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				revertLivePreviewIfNeeded();
			}

			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				revertLivePreviewIfNeeded();
			}
		});
	}

	private JPanel createOverlayPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);

		GridBagConstraints outer = new GridBagConstraints();
		outer.gridx = 0;
		outer.gridy = 0;
		outer.fill = GridBagConstraints.HORIZONTAL;
		outer.anchor = GridBagConstraints.NORTHWEST;
		outer.weightx = 1.0;
		outer.insets = new Insets(6, 6, 6, 6);

		// --- Controls / Hotkeys ---
		JPanel hotkeyPanel = new JPanel(new GridBagLayout());
		hotkeyPanel.setOpaque(false);
		hotkeyPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 4, 4, 4);

		JLabel hotkeyLabel = new JLabel("Mouse-pass through toggle key:");
		hotkeyPanel.add(hotkeyLabel, gbc);

		gbc.gridx = 1;
		passThroughHotkeyCombo = new JComboBox<>(buildFunctionKeyChoices());
		passThroughHotkeyCombo.setSelectedItem(keyCodeToDisplayString(originalPassThroughToggleKeyCode));
		hotkeyPanel.add(passThroughHotkeyCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		JLabel nextTabLabel = new JLabel("Next shown tab key:");
		hotkeyPanel.add(nextTabLabel, gbc);

		gbc.gridx = 1;
		nextShownTabHotkeyCombo = new JComboBox<>(buildFunctionKeyChoices());
		nextShownTabHotkeyCombo.setSelectedItem(keyCodeToDisplayString(originalNextShownTabKeyCode));
		hotkeyPanel.add(nextShownTabHotkeyCombo, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;

		nonOverlayAlwaysOnTopCheckBox = new JCheckBox("Always on top (non-overlay mode)");
		nonOverlayAlwaysOnTopCheckBox.setOpaque(false);
		nonOverlayAlwaysOnTopCheckBox.setSelected(OverlayPreferences.isNonOverlayAlwaysOnTop());
		hotkeyPanel.add(nonOverlayAlwaysOnTopCheckBox, gbc);

		gbc.gridwidth = 1;

		content.add(hotkeyPanel, outer);

		outer.gridy++;
		JPanel tabsPanel = new JPanel(new GridBagLayout());
		tabsPanel.setOpaque(false);
		tabsPanel.setBorder(BorderFactory.createTitledBorder("Visible tabs"));

		GridBagConstraints tgc = new GridBagConstraints();
		tgc.gridx = 0;
		tgc.gridy = 0;
		tgc.anchor = GridBagConstraints.WEST;
		tgc.insets = new Insets(2, 4, 2, 4);

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
		overlayTabBiologyVisibleCheckBox = new JCheckBox("Biology");
		overlayTabBiologyVisibleCheckBox.setOpaque(false);
		overlayTabBiologyVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabBiologyVisible());
		tabsPanel.add(overlayTabBiologyVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabMiningVisibleCheckBox = new JCheckBox("Mining");
		overlayTabMiningVisibleCheckBox.setOpaque(false);
		overlayTabMiningVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabMiningVisible());
		tabsPanel.add(overlayTabMiningVisibleCheckBox, tgc);

		tgc.gridy++;
		overlayTabFleetCarrierVisibleCheckBox = new JCheckBox("Fleet Carrier");
		overlayTabFleetCarrierVisibleCheckBox.setOpaque(false);
		overlayTabFleetCarrierVisibleCheckBox.setSelected(OverlayPreferences.isOverlayTabFleetCarrierVisible());
		tabsPanel.add(overlayTabFleetCarrierVisibleCheckBox, tgc);

		content.add(tabsPanel, outer);

		outer.gridy++;
		JPanel autoSwitchPanel = new JPanel(new GridBagLayout());
		autoSwitchPanel.setOpaque(false);
		autoSwitchPanel.setBorder(BorderFactory.createTitledBorder("Auto-switch tabs"));

		GridBagConstraints agc = new GridBagConstraints();
		agc.gridx = 0;
		agc.gridy = 0;
		agc.anchor = GridBagConstraints.WEST;
		agc.insets = new Insets(2, 4, 2, 4);

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
		autoSwitchBiologyOnNearBodyCheckBox = new JCheckBox("Near landable body with atmosphere → Biology tab");
		autoSwitchBiologyOnNearBodyCheckBox.setOpaque(false);
		autoSwitchBiologyOnNearBodyCheckBox.setSelected(OverlayPreferences.isAutoSwitchBiologyOnNearLandableAtmosphere());
		autoSwitchPanel.add(autoSwitchBiologyOnNearBodyCheckBox, agc);

		agc.gridy++;
		autoSwitchFleetCarrierOnJsonDropCheckBox = new JCheckBox("Dropping a carrier route file (JSON or CSV) → Fleet Carrier tab");
		autoSwitchFleetCarrierOnJsonDropCheckBox.setOpaque(false);
		autoSwitchFleetCarrierOnJsonDropCheckBox.setSelected(OverlayPreferences.isAutoSwitchFleetCarrierOnJsonDrop());
		autoSwitchPanel.add(autoSwitchFleetCarrierOnJsonDropCheckBox, agc);

		content.add(autoSwitchPanel, outer);

		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	/**
	 * Logging tab: choose between auto-detected live folder and a custom test folder.
	 */
	private JPanel createLoggingPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 4, 4, 4);

		// --- Auto-detect checkbox ---
		JLabel journalLabel = new JLabel("Use auto-detected ED log folder:");
		autoDetectCheckBox = new JCheckBox();
		autoDetectCheckBox.setOpaque(false);

		// Load current prefs
		boolean auto = OverlayPreferences.isAutoLogDir(clientKey);
		autoDetectCheckBox.setSelected(auto);

		content.add(journalLabel, gbc);
		gbc.gridx = 1;
		content.add(autoDetectCheckBox, gbc);

		// --- Custom path field + browse button ---
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel pathLabel = new JLabel("Custom journal folder:");
		content.add(pathLabel, gbc);

		gbc.gridx = 1;
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
		content.add(pathPanel, gbc);

		// Enable/disable fields based on auto-detect state
		Runnable updateEnabled = () -> {
			boolean useAuto = autoDetectCheckBox.isSelected();
			customPathField.setEnabled(!useAuto);
			browseButton.setEnabled(!useAuto);
		};
		autoDetectCheckBox.addActionListener(e -> updateEnabled.run());
		updateEnabled.run();

		panel.add(content, BorderLayout.NORTH);
		return panel;
	}


	private JPanel createFontsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 6, 6, 6);

		// Font family
		JLabel fontLabel = new JLabel("Font:");
		content.add(fontLabel, gbc);

		gbc.gridx = 1;
		String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getAvailableFontFamilyNames();
		uiFontNameCombo = new JComboBox<>(families);
		uiFontNameCombo.setSelectedItem(OverlayPreferences.getUiFontName());
		uiFontNameCombo.setPrototypeDisplayValue("Segoe UI Semibold");
		content.add(uiFontNameCombo, gbc);

		// Font size
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel sizeLabel = new JLabel("Size:");
		content.add(sizeLabel, gbc);

		gbc.gridx = 1;
		int sz = OverlayPreferences.getUiFontSize();
		uiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(sz, 8, 72, 1));
		((JSpinner.DefaultEditor) uiFontSizeSpinner.getEditor()).getTextField().setColumns(4);
		content.add(uiFontSizeSpinner, gbc);

		// Preview
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;

		uiFontNameCombo.addActionListener(e -> updatePreviewLabelFont());
		uiFontSizeSpinner.addChangeListener(e -> updatePreviewLabelFont());

		panel.add(content, BorderLayout.NORTH);
		return panel;
	}


	private JPanel createColorsPanel() {
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));

		int initialMainRgb = OverlayPreferences.getUiMainTextRgb();
		int initialBgRgb = OverlayPreferences.getUiBackgroundRgb();
		int initialSneakerRgb = OverlayPreferences.getUiSneakerRgb();
		int initialPrimaryHighlightRgb = OverlayPreferences.getUiPrimaryHighlightRgb();
		int initialSecondaryHighlightRgb = OverlayPreferences.getUiSecondaryHighlightRgb();

		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(6, 6, 6, 6);

		grid.add(new JLabel("Main text:"), gbc);

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
		grid.add(uiMainTextColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		grid.add(new JLabel("Background:"), gbc);

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
		grid.add(uiBackgroundColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.WEST;
		grid.add(new JLabel("Sneaker (landable icon):"), gbc);

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
		grid.add(uiSneakerColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.anchor = GridBagConstraints.WEST;
		grid.add(new JLabel("Primary highlight (complete exob, prospector match):"), gbc);

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
		grid.add(uiPrimaryHighlightColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		grid.add(new JLabel("Secondary highlight (exob in progress):"), gbc);

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
		grid.add(uiSecondaryHighlightColorButton, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;

		JButton resetColorsButton = new JButton("Reset to defaults");
		resetColorsButton.addActionListener(e -> {
			uiMainTextColorButton.setBackground(new Color(255, 140, 0));
			uiBackgroundColorButton.setBackground(new Color(10, 10, 10));
			uiSneakerColorButton.setBackground(new Color(206, 44, 44));
			uiPrimaryHighlightColorButton.setBackground(new Color(0, 200, 0));
			uiSecondaryHighlightColorButton.setBackground(new Color(255, 255, 0));
			applyLiveColorPreviewFromButtons();
		});
		grid.add(resetColorsButton, gbc);

		panel.add(grid);

		// -----------------------------------------------------------------
		// Overlay background transparency (moved from Overlay tab)
		// -----------------------------------------------------------------
		panel.add(Box.createVerticalStrut(10));

		JPanel normalPanel = createOverlayAppearanceSection(
				"Overlay background (Normal mode)",
				originalNormalTransparencyPct,
				(slider, valueLabel) -> {
					normalTransparencySlider = slider;
					normalTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(false)
				);
		normalPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		panel.add(normalPanel);

		panel.add(Box.createVerticalStrut(8));

		JPanel ptPanel = createOverlayAppearanceSection(
				"Overlay background (Mouse-pass through mode)",
				originalPassThroughTransparencyPct,
				(slider, valueLabel) -> {
					passThroughTransparencySlider = slider;
					passThroughTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(true)
				);
		ptPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		panel.add(ptPanel);

		panel.add(Box.createVerticalGlue());

		return panel;
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

			// Push overlay fill first so rebuildTabbedPane() copies the correct parent background.
			f.applyOverlayBackgroundPreview(pt, bgRgb, pct);
			f.applyThemeFromPreferences();
		}
	}

	private JPanel createMiningPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel outer = new JPanel();
		outer.setOpaque(false);
		outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

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

		outer.add(prospectorBox);
		outer.add(Box.createVerticalStrut(10));

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
		miningLogBackendLocalRadio = new JRadioButton("Local CSV (file in ~/.edo/)");
		miningLogBackendLocalRadio.setOpaque(false);
		miningLogBackendGoogleRadio = new JRadioButton("Google Sheets");
		miningLogBackendGoogleRadio.setOpaque(false);
		logBackendGroup.add(miningLogBackendLocalRadio);
		logBackendGroup.add(miningLogBackendGoogleRadio);
		boolean useGoogle = "google".equals(OverlayPreferences.getMiningLogBackend());
		miningLogBackendLocalRadio.setSelected(!useGoogle);
		miningLogBackendGoogleRadio.setSelected(useGoogle);

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
		miningGoogleConnectButton.setEnabled(useGoogle);
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
				"For Google Sheets: rewrites each CMDR … tab (A:P). Fixes row 1 when it skipped the Ship column (Commander / "
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
		applyMiningGoogleSpreadsheetFieldEditability(useGoogle);
		miningLogBackendGoogleRadio.addActionListener(ev -> applyMiningGoogleSpreadsheetFieldEditability(miningLogBackendGoogleRadio.isSelected()));
		miningLogBackendLocalRadio.addActionListener(ev -> applyMiningGoogleSpreadsheetFieldEditability(miningLogBackendGoogleRadio.isSelected()));

		outer.add(logBackendBox);
		outer.add(Box.createVerticalStrut(10));

		// -----------------------------------------------------------------
		// Limpet reminder (checkbox + two radio rows)
		// -----------------------------------------------------------------
		JPanel limpetPanel = new JPanel();
		limpetPanel.setOpaque(false);
		limpetPanel.setLayout(new BoxLayout(limpetPanel, BoxLayout.Y_AXIS));

		JPanel limpetCheckRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		limpetCheckRow.setOpaque(false);

		miningLowLimpetReminderEnabledCheckBox = new JCheckBox("Low limpet announcement");
		miningLowLimpetReminderEnabledCheckBox.setOpaque(false);
		miningLowLimpetReminderEnabledCheckBox.setSelected(OverlayPreferences.isMiningLowLimpetReminderEnabled());

		limpetCheckRow.add(miningLowLimpetReminderEnabledCheckBox);
		limpetPanel.add(limpetCheckRow);

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

		limpetPanel.add(limpetCountRow);

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

		limpetPanel.add(limpetPercentRow);

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
			boolean enabled = miningLowLimpetReminderEnabledCheckBox.isSelected();
			boolean percentSelected = miningLowLimpetReminderPercentRadio.isSelected();
			boolean countSelected = !percentSelected;

			// Radios themselves should remain clickable when enabled
			miningLowLimpetReminderCountRadio.setEnabled(enabled);
			miningLowLimpetReminderPercentRadio.setEnabled(enabled);

			// COUNT line: disable everything except the radio when not selected
			miningLowLimpetReminderThresholdSpinner.setEnabled(enabled && countSelected);
			limpetCountUnitsLabel.setEnabled(enabled && countSelected);

			// PERCENT line: disable everything except the radio when not selected
			miningLowLimpetReminderPercentSpinner.setEnabled(enabled && percentSelected);
			limpetPercentUnitsLabel.setEnabled(enabled && percentSelected);
		};

		miningLowLimpetReminderEnabledCheckBox.addActionListener(e -> updateLimpetEnabled.run());
		miningLowLimpetReminderCountRadio.addActionListener(e -> updateLimpetEnabled.run());
		miningLowLimpetReminderPercentRadio.addActionListener(e -> updateLimpetEnabled.run());
		updateLimpetEnabled.run();


		limpetPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		JPanel limpetWrap = new JPanel(new BorderLayout());
		limpetWrap.setOpaque(false);
		limpetWrap.add(limpetPanel, BorderLayout.WEST);

		outer.add(limpetWrap);
		outer.add(Box.createVerticalStrut(10));

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
		outer.add(animBox);

		panel.add(outer, BorderLayout.NORTH);
		return panel;
	}

	/** Exobiology tab: valuable-bio threshold. */
	private JPanel createExobiologyPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

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
		gbc.insets = new Insets(8, 10, 8, 10);

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

		panel.add(box, BorderLayout.NORTH);
		return panel;
	}

	private void updatePreviewLabelFont() {
		Font f = buildSelectedUiFont();
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

	private void revertLivePreviewIfNeeded() {
		if (okPressed) {
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

		// Revert font (clear preview overrides so icon sizing matches saved prefs)
		f.revertUiFontLivePreview(originalUiFont);

		// Revert overlay fill, then rebuild so tabbed pane inherits the restored background.
		boolean pt = f.isPassThroughEnabled();
		int pct = pt ? originalPassThroughTransparencyPct : originalNormalTransparencyPct;
		f.applyOverlayBackgroundPreview(pt, originalUiBackgroundRgb, pct);
		f.applyThemeFromPreferences();
	}

	private JPanel createSpeechPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(4, 4, 4, 4);

		// Enabled
		JLabel enabledLabel = new JLabel("Enable speech (Amazon Polly):");
		speechEnabledCheckBox = new JCheckBox();
		speechEnabledCheckBox.setOpaque(false);
		speechEnabledCheckBox.setSelected(OverlayPreferences.isSpeechEnabled());

		content.add(enabledLabel, gbc);
		gbc.gridx = 1;
		content.add(speechEnabledCheckBox, gbc);

		// Voice (keep list small and “safe”) — used for Polly and for offline voice packs
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel voiceLabel = new JLabel("Voice (Standard):");
		content.add(voiceLabel, gbc);

		gbc.gridx = 1;
		speechVoiceCombo = new JComboBox<>(STANDARD_US_ENGLISH_VOICES);
		speechVoiceCombo.setSelectedItem(OverlayPreferences.getSpeechVoiceName());
		content.add(speechVoiceCombo, gbc);

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
				deleteSpeechCacheDirectoryContents(root);
				JOptionPane.showMessageDialog(PreferencesDialog.this,
						"Speech cache cleared.",
						"Speech cache",
						JOptionPane.INFORMATION_MESSAGE);
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
						+ "so offline packs should already have clips. Uses voice, cache folder, engine, region, and sample rate from this dialog.");
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

		// --- AWS / Polly (bottom block) ---
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.insets = new Insets(16, 4, 4, 4);

		JPanel awsPanel = new JPanel(new GridBagLayout());
		awsPanel.setOpaque(false);
		awsPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120, 1),
				"AWS (Amazon Polly)"));

		GridBagConstraints agbc = new GridBagConstraints();
		agbc.gridx = 0;
		agbc.gridy = 0;
		agbc.anchor = GridBagConstraints.WEST;
		agbc.insets = new Insets(4, 8, 4, 4);

		JLabel useAwsLabel = new JLabel("Use AWS to generate speech:");
		awsPanel.add(useAwsLabel, agbc);
		agbc.gridx = 1;
		speechUseAwsCheckBox = new JCheckBox();
		speechUseAwsCheckBox.setOpaque(false);
		speechUseAwsCheckBox.setSelected(OverlayPreferences.isSpeechUseAwsSynthesis());
		awsPanel.add(speechUseAwsCheckBox, agbc);

		agbc.gridx = 0;
		agbc.gridy++;
		JLabel engineLabel = new JLabel("Engine:");
		awsPanel.add(engineLabel, agbc);
		agbc.gridx = 1;
		speechEngineCombo = new JComboBox<>(new String[] { "standard", "neural" });
		speechEngineCombo.setSelectedItem(OverlayPreferences.getSpeechEngine());
		awsPanel.add(speechEngineCombo, agbc);

		agbc.gridx = 0;
		agbc.gridy++;
		JLabel regionLabel = new JLabel("AWS Region:");
		awsPanel.add(regionLabel, agbc);
		agbc.gridx = 1;
		speechRegionField = new JTextField(12);
		speechRegionField.setText(OverlayPreferences.getSpeechAwsRegion());
		awsPanel.add(speechRegionField, agbc);

		agbc.gridx = 0;
		agbc.gridy++;
		JLabel profileLabel = new JLabel("AWS profile (optional):");
		awsPanel.add(profileLabel, agbc);
		agbc.gridx = 1;
		speechAwsProfileField = new JTextField(18);
		speechAwsProfileField.setText(OverlayPreferences.getSpeechAwsProfile());
		awsPanel.add(speechAwsProfileField, agbc);

		content.add(awsPanel, gbc);
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		gbc.insets = new Insets(4, 4, 4, 4);

		// Enable/disable: speech master switch; AWS fields only when “Use AWS” is checked
		Runnable updateSpeechPanelEnabled = () -> {
			boolean speechOn = speechEnabledCheckBox.isSelected();
			boolean awsOn = speechOn && speechUseAwsCheckBox.isSelected();

			voiceLabel.setEnabled(speechOn);
			speechVoiceCombo.setEnabled(speechOn);
			cacheDirLabel.setEnabled(speechOn);
			speechCacheDirField.setEnabled(speechOn);
			browseCacheButton.setEnabled(speechOn);
			clearSpeechCacheButton.setEnabled(speechOn);
			rateLabel.setEnabled(speechOn);
			speechSampleRateField.setEnabled(speechOn);
			prospectorSampleLabel.setEnabled(speechOn);
			previewProspectorSpeechButton.setEnabled(speechOn);

			useAwsLabel.setEnabled(speechOn);
			speechUseAwsCheckBox.setEnabled(speechOn);

			engineLabel.setEnabled(awsOn);
			speechEngineCombo.setEnabled(awsOn);
			regionLabel.setEnabled(awsOn);
			speechRegionField.setEnabled(awsOn);
			profileLabel.setEnabled(awsOn);
			speechAwsProfileField.setEnabled(awsOn);
		};
		speechEnabledCheckBox.addActionListener(e -> updateSpeechPanelEnabled.run());
		speechUseAwsCheckBox.addActionListener(e -> updateSpeechPanelEnabled.run());
		updateSpeechPanelEnabled.run();

		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		panel.setBorder(new EmptyBorder(8, 8, 8, 8));
		panel.setOpaque(false);

		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");

		ok.addActionListener(e -> {
			if (!validateMiningGoogleSettingsBeforeSave()) {
				return;
			}
			okPressed = true;
			applyAndSavePreferences();

			if (getOwner() instanceof OverlayUiPreviewHost) {
				OverlayUiPreviewHost f = (OverlayUiPreviewHost) getOwner();
				f.applyOverlayBackgroundFromPreferences(f.isPassThroughEnabled());
				f.applyUiFontPreferences();
				f.applyThemeFromPreferences();

				if (!f.isPassThroughEnabled()) {
					if (getOwner() instanceof Window) {
						Window w = (Window) getOwner();
						w.setAlwaysOnTop(OverlayPreferences.isNonOverlayAlwaysOnTop());
					}
				}
			}

			dispose();
		});

		cancel.addActionListener(e -> {
			revertLivePreviewIfNeeded();
			dispose();
		});

		panel.add(cancel);
		panel.add(ok);
		return panel;
	}

	private boolean validateMiningGoogleSettingsBeforeSave() {
		if (miningLogBackendGoogleRadio == null || !miningLogBackendGoogleRadio.isSelected()) {
			return true;
		}
		String url = miningGoogleSheetsUrlField != null ? miningGoogleSheetsUrlField.getText() : "";
		if (url != null && !url.trim().isEmpty()) {
			return true;
		}
		JOptionPane.showMessageDialog(
				this,
				"Google Sheets is selected but the Google Sheets URL is empty.\n"
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
		JOptionPane.showMessageDialog(this, new JScrollPane(area), "Google Sheets setup", JOptionPane.INFORMATION_MESSAGE);
	}

	private void connectToGoogleAndStoreToken() {
		String clientId = miningGoogleClientIdField != null ? miningGoogleClientIdField.getText().trim() : "";
		String clientSecret = miningGoogleClientSecretField != null ? miningGoogleClientSecretField.getText().trim() : "";
		if (clientId.isEmpty() || clientSecret.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Enter Client ID and Client Secret first, then click Connect to Google.", "Setup required", JOptionPane.WARNING_MESSAGE);
			return;
		}
		boolean ok = GoogleSheetsAuth.runOAuthFlowAndStoreToken(clientId, clientSecret);
		if (ok) {
			JOptionPane.showMessageDialog(this, "Connected. Your prospector log will sync to the selected Google Sheet.", "Success", JOptionPane.INFORMATION_MESSAGE);
		} else {
			String detail = "Could not complete sign-in. Check Client ID and Secret, and try again.";
			OverlayFrame frame = OverlayFrame.overlayFrame;
			if (frame != null) {
				frame.setMiningSheetsStatusError("Mining preferences: " + detail);
				JOptionPane.showMessageDialog(this,
						"Could not complete sign-in. Details are shown in the overlay status bar.",
						"Connection failed",
						JOptionPane.WARNING_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(this, detail, "Connection failed", JOptionPane.ERROR_MESSAGE);
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
			JOptionPane.showMessageDialog(this, "Enter the Google Sheets URL first.", "Mining sheet", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int confirm = JOptionPane.showConfirmDialog(this,
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
						JOptionPane.showMessageDialog(PreferencesDialog.this,
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
						JOptionPane.showMessageDialog(PreferencesDialog.this,
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
					JOptionPane.showMessageDialog(PreferencesDialog.this,
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
			JOptionPane.showMessageDialog(this, "Enter the Google Sheets URL first.", "Mining sheet",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		int confirm = JOptionPane.showConfirmDialog(this,
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
						JOptionPane.showMessageDialog(PreferencesDialog.this,
								"Repair finished. Open your spreadsheet and confirm CMDR tabs look correct.",
								"Mining sheet",
								JOptionPane.INFORMATION_MESSAGE);
						OverlayFrame of = OverlayFrame.overlayFrame;
						if (of != null) {
							of.clearMiningSheetsStatusError();
						}
					} else {
						String msg = r != null ? r.getMessage() : "Unknown error";
						JOptionPane.showMessageDialog(PreferencesDialog.this,
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
					JOptionPane.showMessageDialog(PreferencesDialog.this,
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
            int keyCode = displayStringToKeyCode(passThroughHotkeyCombo.getSelectedItem().toString());
            OverlayPreferences.setPassThroughToggleKeyCode(keyCode);
        }
        if (nextShownTabHotkeyCombo != null && nextShownTabHotkeyCombo.getSelectedItem() != null) {
            int keyCode = displayStringToKeyCode(nextShownTabHotkeyCombo.getSelectedItem().toString());
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
            boolean f = overlayTabFleetCarrierVisibleCheckBox != null && overlayTabFleetCarrierVisibleCheckBox.isSelected();
            if (!r && !s && !b && !m && !f) {
                r = s = b = m = f = true;
            }
            OverlayPreferences.setOverlayTabRouteVisible(r);
            OverlayPreferences.setOverlayTabSystemVisible(s);
            OverlayPreferences.setOverlayTabBiologyVisible(b);
            OverlayPreferences.setOverlayTabMiningVisible(m);
            OverlayPreferences.setOverlayTabFleetCarrierVisible(f);
        }

        if (autoSwitchGalaxyMapToRouteCheckBox != null) {
            OverlayPreferences.setAutoSwitchRouteOnGalaxyMap(autoSwitchGalaxyMapToRouteCheckBox.isSelected());
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

        if (speechUseAwsCheckBox != null) {
            OverlayPreferences.setSpeechUseAwsSynthesis(speechUseAwsCheckBox.isSelected());
        }

        if (speechEngineCombo != null && speechEngineCombo.getSelectedItem() != null) {
            OverlayPreferences.setSpeechEngine(speechEngineCombo.getSelectedItem().toString());
        }

        if (speechVoiceCombo != null && speechVoiceCombo.getSelectedItem() != null) {
            String newVoice = speechVoiceCombo.getSelectedItem().toString();
            OverlayPreferences.setSpeechVoiceId(newVoice);

            // If this voice has no local WAV cache (e.g. user cleared the folder), try GitHub pack download
            if (!VoicePackManager.isVoicePackInstalled(newVoice)) {
                VoicePackManager.downloadAndInstallVoicePack(this, newVoice, null);
            }
        }

        if (speechRegionField != null) {
            OverlayPreferences.setSpeechAwsRegion(speechRegionField.getText().trim());
        }

        if (speechAwsProfileField != null) {
            OverlayPreferences.setSpeechAwsProfile(speechAwsProfileField.getText().trim());
        }

        if (speechCacheDirField != null) {
            OverlayPreferences.setSpeechCacheDir(speechCacheDirField.getText().trim());
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
            OverlayPreferences.setMiningLogBackend(miningLogBackendGoogleRadio.isSelected() ? "google" : "local");
        }
        // Merge-save: never persist an empty field over an existing stored URL/credentials (disabled fields on some
        // LAFs used to yield blank text; Local CSV vs Google must not wipe the saved sheet link).
        persistNonBlankMiningGoogleField(miningGoogleSheetsUrlField, OverlayPreferences::setMiningGoogleSheetsUrl);
        persistNonBlankMiningGoogleField(miningGoogleClientIdField, OverlayPreferences::setMiningGoogleSheetsClientId);
        persistNonBlankMiningGoogleField(miningGoogleClientSecretField, OverlayPreferences::setMiningGoogleSheetsClientSecret);

        if (miningLowLimpetReminderEnabledCheckBox != null) {
            OverlayPreferences.setMiningLowLimpetReminderEnabled(miningLowLimpetReminderEnabledCheckBox.isSelected());
        }

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
			panel.setBorder(BorderFactory.createTitledBorder(title));

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.insets = new Insets(4, 4, 4, 4);

			panel.add(new JLabel("Background transparency:"), gbc);

			gbc.gridx = 1;
			JSlider slider = new JSlider(0, 100, clampPct(initialTransparencyPct));
			slider.setPaintTicks(true);
			slider.setMajorTickSpacing(25);
			slider.setMinorTickSpacing(5);
			panel.add(slider, gbc);

			gbc.gridx = 2;
			JLabel valueLabel = new JLabel(slider.getValue() + "%");
			panel.add(valueLabel, gbc);

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

		private static String[] buildFunctionKeyChoices() {
			String[] keys = new String[12];
			for (int i = 0; i < 12; i++) {
				keys[i] = "F" + (i + 1);
			}
			return keys;
		}

		private static String keyCodeToDisplayString(int keyCode) {
			// Only map F1-F12 for now.
			switch (keyCode) {
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F1:
				return "F1";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F2:
				return "F2";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F3:
				return "F3";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F4:
				return "F4";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F5:
				return "F5";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F6:
				return "F6";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F7:
				return "F7";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F8:
				return "F8";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F9:
				return "F9";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F10:
				return "F10";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F11:
				return "F11";
			case com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F12:
				return "F12";
			default:
				return "F9";
			}
		}

		private static int displayStringToKeyCode(String display) {
			if (display == null) {
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F9;
			}
			String s = display.trim().toUpperCase();
			switch (s) {
			case "F1":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F1;
			case "F2":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F2;
			case "F3":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F3;
			case "F4":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F4;
			case "F5":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F5;
			case "F6":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F6;
			case "F7":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F7;
			case "F8":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F8;
			case "F9":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F9;
			case "F10":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F10;
			case "F11":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F11;
			case "F12":
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F12;
			default:
				return com.github.kwhat.jnativehook.keyboard.NativeKeyEvent.VC_F9;
			}
		}

	}
