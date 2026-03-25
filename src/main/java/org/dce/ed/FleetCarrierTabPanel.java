package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.ui.EdoUi;

/**
 * Fleet Carrier tab:
 * - Imports a Spansh fleet-carrier route JSON.
 * - Reacts to carrier jump scheduling ({@code CarrierJumpRequest}), completion ({@code CarrierJump}),
 *   cancellation ({@code CarrierJumpCancelled}), and {@code CarrierLocation}.
 * - After each carrier jump, copies the next system name to clipboard (and shows the “Copied: …” toast).
 */
public class FleetCarrierTabPanel extends RouteTabPanel {
	private static final long serialVersionUID = 1L;

	private final String defaultStatusText = "Drop a Spansh fleet-carrier JSON to load the route";

	private volatile boolean spanshRouteLoaded = false;

	private final JPanel bottomBar;
	private final JLabel statusLabel;
	private final JButton importButton;

	public FleetCarrierTabPanel(BooleanSupplier passThroughEnabledSupplier) {
		super(passThroughEnabledSupplier);

		// This tab title should be obvious even though we reuse RouteTabPanel rendering.
		setHeaderLabelText("Fleet Carrier: (no data)");

		setOpaque(false);
		setBackground(EdoUi.Internal.TRANSPARENT);

		bottomBar = new JPanel(new BorderLayout());
		bottomBar.setOpaque(false);
		bottomBar.setBackground(EdoUi.Internal.TRANSPARENT);

		statusLabel = new JLabel(defaultStatusText, SwingConstants.LEFT);
		statusLabel.setOpaque(false);
		statusLabel.setForeground(EdoUi.User.MAIN_TEXT);
		Font base = OverlayPreferences.getUiFont();
		statusLabel.setFont(base);

		importButton = new JButton("Import Spansh Fleet Carrier JSON");
		importButton.setFocusable(false);
		importButton.setForeground(EdoUi.User.MAIN_TEXT);
		importButton.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
		importButton.setBackground(EdoUi.Internal.DARK_ALPHA_220);

		importButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Import Spansh fleet-carrier route JSON");
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));

			java.awt.Window w = SwingUtilities.getWindowAncestor(this);
			int result = chooser.showOpenDialog(w);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				doImport(chooser.getSelectedFile().toPath());
			}
		});

		JPanel statusWrap = new JPanel(new BorderLayout());
		statusWrap.setOpaque(false);
		statusWrap.add(statusLabel, BorderLayout.CENTER);

		bottomBar.add(importButton, BorderLayout.WEST);
		bottomBar.add(statusWrap, BorderLayout.CENTER);

		add(bottomBar, BorderLayout.SOUTH);
		applyOverlayBackground(EdoUi.Internal.TRANSPARENT, OverlayPreferences.overlayChromeRequestsTransparency());
	}

	@Override
	protected boolean shouldUpdateOnCarrierJump(CarrierJumpEvent jump) {
		// Fleet Carrier tab updates from every carrier jump (regardless of docked status),
		// per user request.
		return true;
	}

	@Override
	public boolean importSpanshFleetCarrierRouteFile(Path file) {
		boolean ok = super.importSpanshFleetCarrierRouteFile(file);
		spanshRouteLoaded = ok;
		if (!ok) {
			statusLabel.setText("Invalid/unsupported Spansh fleet-carrier JSON");
		} else {
			statusLabel.setText(defaultStatusText);
		}
		return ok;
	}

	private void doImport(Path file) {
		// Import might throw; ensure the UI stays usable.
		try {
			importSpanshFleetCarrierRouteFile(Objects.requireNonNull(file, "file"));
		} catch (Exception ex) {
			ex.printStackTrace();
			spanshRouteLoaded = false;
			statusLabel.setText("Invalid/unsupported Spansh fleet-carrier JSON");
		}
	}

	@Override
	public void handleLogEvent(EliteLogEvent event) {
		if (event == null) {
			return;
		}
		// Only update on carrier events; ignore everything else so ship jumps / NavRoute don't affect this tab.
		if (event instanceof CarrierJumpRequestEvent req) {
			startPendingJumpBlink(req.getSystemName(), req.getSystemAddress());
			return;
		}
		if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
			stopPendingJumpBlink();
			return;
		}
		if (event instanceof CarrierJumpEvent jump) {
			super.handleLogEvent(event);
			if (spanshRouteLoaded) {
				SwingUtilities.invokeLater(() -> copyNextSystemFromBaseRoute(jump.getSystemAddress()));
			}
		} else if (event instanceof CarrierLocationEvent loc) {
			super.handleLogEvent(event);
		}
	}

	/**
	 * Update button opacity/colors when the overlay transparency changes.
	 * This keeps the tab consistent with the original Route/System tabs.
	 */
	public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
		boolean opaque = !treatAsTransparent;

		// Bottom container stays non-opaque; the card panel background does the heavy lifting.
		bottomBar.setOpaque(false);
		statusLabel.setOpaque(false);

		// JButton needs explicit opacity so it doesn't look “wrong” in transparent mode.
		importButton.setOpaque(!opaque ? false : true);
		importButton.setBackground(opaque ? EdoUi.Internal.GRAY_180 : EdoUi.Internal.DARK_ALPHA_220);
		importButton.setForeground(EdoUi.User.MAIN_TEXT);
		revalidate();
		repaint();
	}
}

