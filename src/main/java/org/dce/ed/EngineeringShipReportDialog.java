package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Plain-text ship engineering report, opened from the build progress dialog.
 * Copy to Clipboard copies the report text; the Coriolis button copies the raw
 * journal Loadout JSON (which Coriolis/EDSY import, engineering included) and
 * opens the Coriolis import page.
 */
final class EngineeringShipReportDialog extends JDialog {

	private static final int HOVER_CLICK_DELAY_MS = 500;
	private static final String CORIOLIS_IMPORT_URL = "https://coriolis.io/import";

	private EngineeringShipReportDialog(Window owner,
			String shipTitle,
			String reportText,
			String loadoutJson,
			Font baseFont,
			int fontSize,
			BooleanSupplier passThroughEnabledSupplier) {
		super(owner, "Ship report — " + shipTitle, ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(12, 14, 12, 14));
		root.setBackground(EdoUi.User.BACKGROUND);
		root.setOpaque(true);

		JLabel title = new JLabel("Ship report — " + shipTitle);
		title.setFont(baseFont.deriveFont(Font.BOLD, fontSize + 2));
		title.setForeground(EdoUi.User.MAIN_TEXT);
		root.add(title, BorderLayout.NORTH);

		JTextArea area = new JTextArea(reportText != null ? reportText : "");
		area.setEditable(false);
		area.setLineWrap(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(11, fontSize - 1)));
		area.setForeground(EdoUi.User.MAIN_TEXT);
		area.setBackground(EdoUi.User.PANEL_BG);
		area.setCaretColor(EdoUi.User.MAIN_TEXT);
		area.setSelectionColor(EdoUi.ED_ORANGE_LESS_TRANS);
		area.setBorder(new EmptyBorder(8, 10, 8, 10));
		area.setCaretPosition(0);

		JScrollPane scroll = new JScrollPane(area);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		root.add(scroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);

		JButton copyBtn = new JButton("Copy to Clipboard");
		OverlayOutlineButtonStyle.applyChip(copyBtn, baseFont, false);
		copyBtn.setToolTipText("Copy the report text — paste into an AI chat or anywhere else");
		Runnable copyAction = () -> {
			copyToClipboard(area.getText());
			flash(copyBtn, "Copied!");
		};
		copyBtn.addActionListener(e -> copyAction.run());
		HoverClickPoller.register(copyBtn, HOVER_CLICK_DELAY_MS, copyAction, passThroughEnabledSupplier);
		south.add(copyBtn);

		JButton coriolisBtn = new JButton("Coriolis");
		OverlayOutlineButtonStyle.applyChip(coriolisBtn, baseFont, false);
		if (loadoutJson != null && !loadoutJson.isBlank()) {
			coriolisBtn.setToolTipText(
					"Copy the journal Loadout JSON (engineering included) and open " + CORIOLIS_IMPORT_URL
							+ " — paste into the import box");
			final String json = loadoutJson;
			Runnable coriolisAction = () -> {
				copyToClipboard(json);
				openCoriolisImport();
				flash(coriolisBtn, "JSON copied!");
			};
			coriolisBtn.addActionListener(e -> coriolisAction.run());
			HoverClickPoller.register(coriolisBtn, HOVER_CLICK_DELAY_MS, coriolisAction, passThroughEnabledSupplier);
		} else {
			coriolisBtn.setEnabled(false);
			coriolisBtn.setToolTipText("No stored loadout JSON for this ship yet");
		}
		south.add(coriolisBtn);

		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, baseFont, false);
		closeBtn.addActionListener(e -> dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);

		root.add(south, BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(560, 400));
		setPreferredSize(new Dimension(760, 680));
		pack();
		setLocationRelativeTo(owner);
		setAlwaysOnTop(true);
	}

	static void show(Window owner,
			String shipTitle,
			String reportText,
			String loadoutJson,
			Font baseFont,
			int fontSize,
			BooleanSupplier passThroughEnabledSupplier) {
		EngineeringShipReportDialog dialog = new EngineeringShipReportDialog(
				owner, shipTitle, reportText, loadoutJson, baseFont, fontSize, passThroughEnabledSupplier);
		dialog.setVisible(true);
		SwingUtilities.invokeLater(() -> dialog.toFront());
	}

	private static void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text != null ? text : ""), null);
	}

	private static void openCoriolisImport() {
		try {
			if (java.awt.Desktop.isDesktopSupported()
					&& java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(CORIOLIS_IMPORT_URL));
			}
		} catch (Exception ignored) {
			// Clipboard already has the JSON; user can open Coriolis manually.
		}
	}

	/** Brief button-label feedback after a copy. */
	private static void flash(JButton button, String text) {
		String original = button.getText();
		button.setText(text);
		button.setEnabled(false);
		Timer timer = new Timer(1400, e -> {
			button.setText(original);
			button.setEnabled(true);
		});
		timer.setRepeats(false);
		timer.start();
	}
}
