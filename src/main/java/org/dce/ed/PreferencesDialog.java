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
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.dce.ed.mining.GoogleSheetsAuth;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.tts.VoicePackManager;

/**
 * Preferences dialog for the overlay.
 */
public class PreferencesDialog extends JDialog {

	public final String clientKey;

	// Overlay-tab fields so OK can read them
	private JSlider normalTransparencySlider;
	private JLabel normalTransparencyValueLabel;

	private JSlider passThroughTransparencySlider;
	private JLabel passThroughTransparencyValueLabel;

	private JComboBox<String> passThroughHotkeyCombo;
	private JCheckBox nonOverlayAlwaysOnTopCheckBox;

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

	// Mining tab: limpet reminder
	private JCheckBox miningLowLimpetReminderEnabledCheckBox;
	private JRadioButton miningLowLimpetReminderCountRadio;
	private JSpinner miningLowLimpetReminderThresholdSpinner;
	private JRadioButton miningLowLimpetReminderPercentRadio;
	private JSpinner miningLowLimpetReminderPercentSpinner;

	// Mining tab: value estimation (used by Mining tab only)
	private JSpinner miningTonsLowSpinner;
	private JSpinner miningTonsMediumSpinner;
	private JSpinner miningTonsHighSpinner;
	private JSpinner miningTonsCoreSpinner;

	private JCheckBox overlayTabRouteVisibleCheckBox;
	private JCheckBox overlayTabSystemVisibleCheckBox;
	private JCheckBox overlayTabBiologyVisibleCheckBox;
	private JCheckBox overlayTabMiningVisibleCheckBox;
	private JCheckBox overlayTabFleetCarrierVisibleCheckBox;

	private JSpinner nearbySphereRadiusSpinner;
	private JSpinner nearbyMaxSystemsSpinner;
	private JSpinner nearbyMinValueMillionSpinner;
	private JSpinner bioValuableThresholdMillionSpinner;

	private boolean okPressed;
	private final Font originalUiFont;
	private final int originalNormalTransparencyPct;
	private final int originalPassThroughTransparencyPct;
	private final int originalPassThroughToggleKeyCode;

	private final int originalUiMainTextRgb;
	private final int originalUiBackgroundRgb;
	private final int originalUiSneakerRgb;


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

		this.originalUiMainTextRgb = OverlayPreferences.getUiMainTextRgb();
		this.originalUiBackgroundRgb = OverlayPreferences.getUiBackgroundRgb();
		this.originalUiSneakerRgb = OverlayPreferences.getUiSneakerRgb();


		this.okPressed = false;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		setMinimumSize(new Dimension(560, 380));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Colors", createColorsPanel());
		tabs.addTab("Exobiology", createExobiologyPanel());
		tabs.addTab("Fonts", createFontsPanel());
		tabs.addTab("Logging", createLoggingPanel());
		tabs.addTab("Mining", createMiningPanel());
		tabs.addTab("Overlay", createOverlayPanel());
		tabs.addTab("Speech", createSpeechPanel());

		add(tabs, BorderLayout.CENTER);
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

		// --- Normal mode ---
		JPanel normalPanel = createOverlayAppearanceSection(
				"Normal mode",
				originalNormalTransparencyPct,
				(slider, valueLabel) -> {
					normalTransparencySlider = slider;
					normalTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(false)
				);

		content.add(normalPanel, outer);

		// --- Pass-through mode ---
		outer.gridy++;
		JPanel ptPanel = createOverlayAppearanceSection(
				"Mouse-pass through mode",
				originalPassThroughTransparencyPct,
				(slider, valueLabel) -> {
					passThroughTransparencySlider = slider;
					passThroughTransparencyValueLabel = valueLabel;
				},
				() -> applyLiveOverlayBackgroundPreview(true)
				);
		content.add(ptPanel, outer);

		// --- Hotkey ---
		outer.gridy++;
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
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;

		JButton resetColorsButton = new JButton("Reset to defaults");
		resetColorsButton.addActionListener(e -> {
			uiMainTextColorButton.setBackground(new Color(255, 140, 0));
			uiBackgroundColorButton.setBackground(new Color(10, 10, 10));
			uiSneakerColorButton.setBackground(new Color(206, 44, 44));
			applyLiveColorPreviewFromButtons();
		});
		grid.add(resetColorsButton, gbc);

		panel.add(grid);
		panel.add(Box.createVerticalGlue());

		return panel;
	}
	private void applyLiveColorPreviewFromButtons() {
		if (uiMainTextColorButton == null || uiBackgroundColorButton == null || uiSneakerColorButton == null) {
			return;
		}
		int mainRgb = colorToRgb(uiMainTextColorButton.getBackground());
		int bgRgb = colorToRgb(uiBackgroundColorButton.getBackground());
		int sneakerRgb = colorToRgb(uiSneakerColorButton.getBackground());
		applyLiveColorPreview(mainRgb, bgRgb, sneakerRgb);
	}

	private void applyLiveColorPreview(int mainRgb, int bgRgb, int sneakerRgb) {
		// Live preview: write to preferences so the existing theme plumbing picks it up.
		// If the user cancels, revertLivePreviewIfNeeded() restores the original values.
		OverlayPreferences.setUiMainTextRgb(mainRgb);
		OverlayPreferences.setUiBackgroundRgb(bgRgb);
		OverlayPreferences.setNormalBackgroundRgb(bgRgb);
		OverlayPreferences.setPassThroughBackgroundRgb(bgRgb);
		OverlayPreferences.setUiSneakerRgb(sneakerRgb);
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
		miningGoogleSheetsUrlField.setEnabled(useGoogle);
		logBackendBox.add(miningGoogleSheetsUrlField, gbcLog);
		miningLogBackendGoogleRadio.addActionListener(e -> miningGoogleSheetsUrlField.setEnabled(miningLogBackendGoogleRadio.isSelected()));
		miningLogBackendLocalRadio.addActionListener(e -> miningGoogleSheetsUrlField.setEnabled(miningLogBackendGoogleRadio.isSelected()));

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
		miningGoogleClientIdField.setEnabled(useGoogle);
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
		miningGoogleClientSecretField.setEnabled(useGoogle);
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
		miningLogBackendGoogleRadio.addActionListener(ev -> {
			boolean on = miningLogBackendGoogleRadio.isSelected();
			miningGoogleSheetsUrlField.setEnabled(on);
			miningGoogleClientIdField.setEnabled(on);
			miningGoogleClientSecretField.setEnabled(on);
			miningGoogleConnectButton.setEnabled(on);
			miningGoogleSetupHelpButton.setEnabled(on);
		});
		miningLogBackendLocalRadio.addActionListener(ev -> {
			boolean on = miningLogBackendGoogleRadio.isSelected();
			miningGoogleSheetsUrlField.setEnabled(on);
			miningGoogleClientIdField.setEnabled(on);
			miningGoogleClientSecretField.setEnabled(on);
			miningGoogleConnectButton.setEnabled(on);
			miningGoogleSetupHelpButton.setEnabled(on);
		});

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
		// Value estimation box
		// -----------------------------------------------------------------
		JPanel estBox = new JPanel(new GridBagLayout());
		estBox.setOpaque(false);
		estBox.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120),
						"Mining tab value estimation (tons)"
						)
				);


		GridBagConstraints ebc = new GridBagConstraints();
		ebc.gridx = 0;
		ebc.gridy = 0;
		ebc.anchor = GridBagConstraints.WEST;
		ebc.insets = new Insets(6, 8, 6, 8);

		JLabel lowTonsLabel = new JLabel("Content=Low total tons:");
		estBox.add(lowTonsLabel, ebc);

		ebc.gridx = 1;
		miningTonsLowSpinner = new JSpinner(new SpinnerNumberModel(OverlayPreferences.getMiningEstimateTonsLow(), 0.0, 200.0, 1.0));
		((JSpinner.DefaultEditor) miningTonsLowSpinner.getEditor()).getTextField().setColumns(6);
		estBox.add(miningTonsLowSpinner, ebc);

		ebc.gridx = 0;
		ebc.gridy++;
		JLabel medTonsLabel = new JLabel("Content=Medium total tons:");
		estBox.add(medTonsLabel, ebc);

		ebc.gridx = 1;
		miningTonsMediumSpinner = new JSpinner(new SpinnerNumberModel(OverlayPreferences.getMiningEstimateTonsMedium(), 0.0, 200.0, 1.0));
		((JSpinner.DefaultEditor) miningTonsMediumSpinner.getEditor()).getTextField().setColumns(6);
		estBox.add(miningTonsMediumSpinner, ebc);

		ebc.gridx = 0;
		ebc.gridy++;
		JLabel highTonsLabel = new JLabel("Content=High total tons:");
		estBox.add(highTonsLabel, ebc);

		ebc.gridx = 1;
		miningTonsHighSpinner = new JSpinner(new SpinnerNumberModel(OverlayPreferences.getMiningEstimateTonsHigh(), 0.0, 200.0, 1.0));
		((JSpinner.DefaultEditor) miningTonsHighSpinner.getEditor()).getTextField().setColumns(6);
		estBox.add(miningTonsHighSpinner, ebc);

		ebc.gridx = 0;
		ebc.gridy++;
		JLabel coreTonsLabel = new JLabel("Core total tons:");
		estBox.add(coreTonsLabel, ebc);

		ebc.gridx = 1;
		miningTonsCoreSpinner = new JSpinner(new SpinnerNumberModel(OverlayPreferences.getMiningEstimateTonsCore(), 0.0, 200.0, 1.0));
		((JSpinner.DefaultEditor) miningTonsCoreSpinner.getEditor()).getTextField().setColumns(6);
		estBox.add(miningTonsCoreSpinner, ebc);

		outer.add(estBox);

		panel.add(outer, BorderLayout.NORTH);
		return panel;
	}

	private JPanel createExobiologyPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setOpaque(false);

		JPanel content = new JPanel(new GridBagLayout());
		content.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.insets = new Insets(6, 6, 6, 6);

		// --- Nearby tab (exobiology sphere search) ---
		JPanel nearbyPanel = new JPanel(new GridBagLayout());
		nearbyPanel.setOpaque(false);
		nearbyPanel.setBorder(BorderFactory.createTitledBorder("Nearby tab (sphere search)"));

		GridBagConstraints npc = new GridBagConstraints();
		npc.gridx = 0;
		npc.gridy = 0;
		npc.anchor = GridBagConstraints.WEST;
		npc.insets = new Insets(4, 4, 4, 4);

		nearbyPanel.add(new JLabel("Sphere radius (ly):"), npc);
		npc.gridx = 1;
		nearbySphereRadiusSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getNearbySphereRadiusLy(), 1, 100, 1));
		((JSpinner.DefaultEditor) nearbySphereRadiusSpinner.getEditor()).getTextField().setColumns(4);
		nearbyPanel.add(nearbySphereRadiusSpinner, npc);

		npc.gridx = 0;
		npc.gridy++;
		nearbyPanel.add(new JLabel("Max systems (closest first, limits API calls):"), npc);
		npc.gridx = 1;
		nearbyMaxSystemsSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getNearbyMaxSystems(), 1, 200, 1));
		((JSpinner.DefaultEditor) nearbyMaxSystemsSpinner.getEditor()).getTextField().setColumns(4);
		nearbyPanel.add(nearbyMaxSystemsSpinner, npc);

		npc.gridx = 0;
		npc.gridy++;
		nearbyPanel.add(new JLabel("Min value (million credits):"), npc);
		npc.gridx = 1;
		nearbyMinValueMillionSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getNearbyMinValueMillionCredits(), 0.0, 1000.0, 0.5));
		((JSpinner.DefaultEditor) nearbyMinValueMillionSpinner.getEditor()).getTextField().setColumns(6);
		nearbyPanel.add(nearbyMinValueMillionSpinner, npc);

		content.add(nearbyPanel, gbc);

		gbc.gridy++;
		JPanel systemExoPanel = new JPanel(new GridBagLayout());
		systemExoPanel.setOpaque(false);
		systemExoPanel.setBorder(BorderFactory.createTitledBorder("System tab"));

		GridBagConstraints sec = new GridBagConstraints();
		sec.gridx = 0;
		sec.gridy = 0;
		sec.anchor = GridBagConstraints.WEST;
		sec.insets = new Insets(4, 4, 4, 4);

		systemExoPanel.add(new JLabel("Valuable bio threshold (million credits):"), sec);
		sec.gridx = 1;
		bioValuableThresholdMillionSpinner = new JSpinner(new SpinnerNumberModel(
				OverlayPreferences.getBioValuableThresholdMillionCredits(), 0.0, 1000.0, 0.5));
		((JSpinner.DefaultEditor) bioValuableThresholdMillionSpinner.getEditor()).getTextField().setColumns(6);
		systemExoPanel.add(bioValuableThresholdMillionSpinner, sec);

		gbc.weighty = 0.0;
		content.add(systemExoPanel, gbc);

		gbc.gridy++;
		gbc.weighty = 1.0;
		content.add(new JLabel(""), gbc);

		panel.add(content, BorderLayout.NORTH);
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
		OverlayPreferences.applyThemeToEdoUi();

		// Revert font
		f.applyUiFontPreview(originalUiFont);

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


		// Use AWS to generate missing speech
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel useAwsLabel = new JLabel("Use AWS to generate speech:");
		content.add(useAwsLabel, gbc);

		gbc.gridx = 1;
		speechUseAwsCheckBox = new JCheckBox();
		speechUseAwsCheckBox.setOpaque(false);
		speechUseAwsCheckBox.setSelected(OverlayPreferences.isSpeechUseAwsSynthesis());
		content.add(speechUseAwsCheckBox, gbc);


		// Engine (Standard only by default)
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel engineLabel = new JLabel("Engine:");
		content.add(engineLabel, gbc);

		gbc.gridx = 1;
		speechEngineCombo = new JComboBox<>(new String[] { "standard", "neural" });
		speechEngineCombo.setSelectedItem(OverlayPreferences.getSpeechEngine());
		content.add(speechEngineCombo, gbc);

		// Voice (keep list small and “safe”)
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel voiceLabel = new JLabel("Voice (Standard):");
		content.add(voiceLabel, gbc);

		gbc.gridx = 1;
		speechVoiceCombo = new JComboBox<>(STANDARD_US_ENGLISH_VOICES);
		speechVoiceCombo.setSelectedItem(OverlayPreferences.getSpeechVoiceName());
		content.add(speechVoiceCombo, gbc);

		// Region
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel regionLabel = new JLabel("AWS Region:");
		content.add(regionLabel, gbc);

		gbc.gridx = 1;
		speechRegionField = new JTextField(12);
		speechRegionField.setText(OverlayPreferences.getSpeechAwsRegion());
		content.add(speechRegionField, gbc);

		// Profile
		gbc.gridx = 0;
		gbc.gridy++;
		JLabel profileLabel = new JLabel("AWS profile (optional):");
		content.add(profileLabel, gbc);

		gbc.gridx = 1;
		speechAwsProfileField = new JTextField(18);
		speechAwsProfileField.setText(OverlayPreferences.getSpeechAwsProfile());
		content.add(speechAwsProfileField, gbc);

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

		cachePanel.add(speechCacheDirField, BorderLayout.CENTER);
		cachePanel.add(browseCacheButton, BorderLayout.EAST);
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

		// Enable/disable everything but the checkbox based on enabled
		Runnable updateEnabled = () -> {
			boolean enabled = speechEnabledCheckBox.isSelected();
			speechEngineCombo.setEnabled(enabled);
			speechVoiceCombo.setEnabled(enabled);
			speechRegionField.setEnabled(enabled);
			speechAwsProfileField.setEnabled(enabled);
			speechCacheDirField.setEnabled(enabled);
			browseCacheButton.setEnabled(enabled);
			speechSampleRateField.setEnabled(enabled);
			speechUseAwsCheckBox.setEnabled(enabled);
		};
		speechEnabledCheckBox.addActionListener(e -> updateEnabled.run());
		updateEnabled.run();

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

	private void showGoogleSheetsSetupInstructions() {
		String msg = "To use Google Sheets for the prospector log:\n\n"
				+ "1. Open Google Cloud Console: https://console.cloud.google.com/\n"
				+ "2. Create a project (or select an existing one).\n"
				+ "3. Enable the Google Sheets API: APIs & Services → Library → search \"Google Sheets API\" → Enable.\n"
				+ "4. Configure OAuth consent screen: APIs & Services → OAuth consent screen. Choose \"External\" if others will use this. Add your app name and support email.\n"
				+ "5. Create credentials: APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID.\n"
				+ "6. Application type: \"Desktop app\". Name it (e.g. \"EDO Overlay\") and click Create.\n"
				+ "7. Copy the Client ID and Client Secret from the credentials page into the fields above.\n"
				+ "8. Paste your Google Sheet edit URL (from the browser) into the URL field. The sheet should have a header row: Run, Body, Timestamp, Type, Percentage, Before Amount, After Amount, Actual, Email Address (or the app will append it).\n"
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
			JOptionPane.showMessageDialog(this, "Could not complete sign-in. Check Client ID and Secret, and try again.", "Connection failed", JOptionPane.ERROR_MESSAGE);
		}
	}

    private void applyAndSavePreferences() {
        // Overlay tab
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
            String oldVoice = OverlayPreferences.getSpeechVoiceName();
            OverlayPreferences.setSpeechVoiceId(newVoice);

            // If voice changed and no local cache exists, try to download voice pack
            if (!newVoice.equalsIgnoreCase(oldVoice) && !VoicePackManager.isVoicePackInstalled(newVoice)) {
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
        if (miningGoogleSheetsUrlField != null) {
            OverlayPreferences.setMiningGoogleSheetsUrl(miningGoogleSheetsUrlField.getText());
        }
        if (miningGoogleClientIdField != null) {
            OverlayPreferences.setMiningGoogleSheetsClientId(miningGoogleClientIdField.getText());
        }
        if (miningGoogleClientSecretField != null) {
            OverlayPreferences.setMiningGoogleSheetsClientSecret(miningGoogleClientSecretField.getText());
        }

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

        if (miningTonsLowSpinner != null) {
            try {
                double v = ((Number) miningTonsLowSpinner.getValue()).doubleValue();
                OverlayPreferences.setMiningEstimateTonsLow(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningTonsMediumSpinner != null) {
            try {
                double v = ((Number) miningTonsMediumSpinner.getValue()).doubleValue();
                OverlayPreferences.setMiningEstimateTonsMedium(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningTonsHighSpinner != null) {
            try {
                double v = ((Number) miningTonsHighSpinner.getValue()).doubleValue();
                OverlayPreferences.setMiningEstimateTonsHigh(v);
            } catch (Exception e) {
                // ignore
            }
        }
        if (miningTonsCoreSpinner != null) {
            try {
                double v = ((Number) miningTonsCoreSpinner.getValue()).doubleValue();
                OverlayPreferences.setMiningEstimateTonsCore(v);
            } catch (Exception e) {
                // ignore
            }
        }

        if (nearbySphereRadiusSpinner != null) {
            try {
                int r = ((Number) nearbySphereRadiusSpinner.getValue()).intValue();
                OverlayPreferences.setNearbySphereRadiusLy(r);
            } catch (Exception e) {
                // ignore
            }
        }
        if (nearbyMaxSystemsSpinner != null) {
            try {
                int m = ((Number) nearbyMaxSystemsSpinner.getValue()).intValue();
                OverlayPreferences.setNearbyMaxSystems(m);
            } catch (Exception e) {
                // ignore
            }
        }

        if (nearbyMinValueMillionSpinner != null) {
            try {
                double v = ((Number) nearbyMinValueMillionSpinner.getValue()).doubleValue();
                OverlayPreferences.setNearbyMinValueMillionCredits(v);
            } catch (Exception e) {
                // ignore
            }
        }

        if (bioValuableThresholdMillionSpinner != null) {
            try {
                double v = ((Number) bioValuableThresholdMillionSpinner.getValue()).doubleValue();
                OverlayPreferences.setBioValuableThresholdMillionCredits(v);
            } catch (Exception e) {
                // ignore
            }
        }

        // Other tabs can be wired into OverlayPreferences later as needed.
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
