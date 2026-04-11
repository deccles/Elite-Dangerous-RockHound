package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionData;
import org.dce.ed.session.FleetCarrierSessionMapper;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.ui.SystemNameAutocomplete;
import org.dce.ed.util.SpanshClient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Fleet Carrier tab:
 * - Imports a Spansh fleet-carrier route (JSON or CSV export).
 * - Reacts to carrier jump scheduling ({@code CarrierJumpRequest}), completion ({@code CarrierJump}),
 *   cancellation ({@code CarrierJumpCancelled}), and {@code CarrierLocation}.
 * - Refreshes the route status column after FSS events (same {@link org.dce.ed.cache.SystemCache} data as
 *   the Route tab; does not reload {@code NavRoute.json}).
 * - After each carrier jump, copies the next system name to clipboard (and shows the “Copied: …” toast).
 */
public class FleetCarrierTabPanel extends RouteTabPanel {
	private static final long serialVersionUID = 1L;

	private final String defaultStatusText = "Drop a Spansh file to import";

	private volatile boolean spanshRouteLoaded = false;

	private final SpanshClient spanshClient = new SpanshClient();
	private final JPanel bottomBar;
	private final JLabel statusLabel;
	private final JLabel destinationLabel;
	private final JTextField destinationField;
	private final JButton calculateButton;
	private final JButton importButton;
	private final JButton copyNextDestinationButton;
	private final Timer copyNextDestinationRefreshTimer;

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

		destinationLabel = new JLabel("Destination:");
		destinationLabel.setOpaque(false);
		destinationLabel.setForeground(EdoUi.User.MAIN_TEXT);
		destinationLabel.setFont(base);

		destinationField = new JTextField();
		destinationField.setFocusable(true);
		destinationField.setForeground(EdoUi.User.MAIN_TEXT);
		destinationField.setCaretColor(EdoUi.User.MAIN_TEXT);
		destinationField.setFont(base);
		destinationField.setToolTipText("Destination system name (EDSM autocomplete, resolved via Spansh for fetch)");

		new SystemNameAutocomplete(destinationField, edsmClient());

		Timer destinationPersistDebounce = new Timer(750, e -> fireSessionStateChanged());
		destinationPersistDebounce.setRepeats(false);
		destinationField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				destinationPersistDebounce.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				destinationPersistDebounce.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				destinationPersistDebounce.restart();
			}
		});

		calculateButton = new JButton("Calculate");
		calculateButton.setFocusable(false);
		calculateButton.setForeground(EdoUi.User.MAIN_TEXT);
		calculateButton.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
		calculateButton.setBackground(EdoUi.Internal.DARK_ALPHA_220);
		calculateButton.addActionListener(e -> fetchRouteFromSpansh());

		importButton = new JButton("Import Spansh");
		importButton.setFocusable(false);
		importButton.setForeground(EdoUi.User.MAIN_TEXT);
		importButton.setOpaque(!OverlayPreferences.overlayChromeRequestsTransparency());
		importButton.setBackground(EdoUi.Internal.DARK_ALPHA_220);

		copyNextDestinationButton = new JButton("Copy next destination");
		copyNextDestinationButton.setFocusable(false);
		copyNextDestinationButton.setOpaque(false);
		copyNextDestinationButton.setForeground(EdoUi.User.MAIN_TEXT);
		copyNextDestinationButton.setFont(base.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 3));
		copyNextDestinationButton.setToolTipText("Copy the next route system name to the clipboard (same as Route/Nearby copy)");
		copyNextDestinationButton.addActionListener(e -> copyNextRouteDestinationToClipboard());

		importButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Import Spansh fleet-carrier route (JSON or CSV)");
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(new FileNameExtensionFilter("JSON or CSV", "json", "csv"));

			java.awt.Window w = SwingUtilities.getWindowAncestor(this);
			int result = chooser.showOpenDialog(w);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				doImport(chooser.getSelectedFile().toPath());
			}
		});

		JPanel statusWrap = new JPanel(new BorderLayout());
		statusWrap.setOpaque(false);
		statusWrap.add(statusLabel, BorderLayout.CENTER);

		JPanel bottomEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		bottomEast.setOpaque(false);
		bottomEast.setBackground(EdoUi.Internal.TRANSPARENT);
		bottomEast.add(copyNextDestinationButton);

		bottomBar.add(importButton, BorderLayout.WEST);
		bottomBar.add(statusWrap, BorderLayout.CENTER);
		bottomBar.add(bottomEast, BorderLayout.EAST);

		JPanel fetchRow = new JPanel(new BorderLayout(8, 0));
		fetchRow.setOpaque(false);
		fetchRow.setBackground(EdoUi.Internal.TRANSPARENT);
		fetchRow.add(destinationLabel, BorderLayout.WEST);
		fetchRow.add(destinationField, BorderLayout.CENTER);
		fetchRow.add(calculateButton, BorderLayout.EAST);

		JPanel southOuter = new JPanel(new BorderLayout());
		southOuter.setOpaque(false);
		southOuter.setBackground(EdoUi.Internal.TRANSPARENT);
		southOuter.add(fetchRow, BorderLayout.NORTH);
		southOuter.add(bottomBar, BorderLayout.CENTER);

		add(southOuter, BorderLayout.SOUTH);
		applyOverlayBackground(EdoUi.Internal.TRANSPARENT, OverlayPreferences.overlayChromeRequestsTransparency());

		copyNextDestinationRefreshTimer = new Timer(1_000, e -> updateCopyNextDestinationButton());
		copyNextDestinationRefreshTimer.setRepeats(true);
		copyNextDestinationRefreshTimer.start();
		updateCopyNextDestinationButton();
	}

	private void updateCopyNextDestinationButton() {
		String next = RouteTabPanel.nextRouteDestinationSystemName(routeSession);
		copyNextDestinationButton.setEnabled(next != null && !next.isBlank());
	}

	private void copyNextRouteDestinationToClipboard() {
		String next = RouteTabPanel.nextRouteDestinationSystemName(routeSession);
		if (next == null || next.isBlank()) {
			return;
		}
		StringSelection selection = new StringSelection(next);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
		SystemTableHoverCopyManager.showCopiedToast(destinationField, next);
	}

	@Override
	public void applyUiFont(Font font) {
		super.applyUiFont(font);
		if (copyNextDestinationButton != null && font != null) {
			copyNextDestinationButton.setFont(font.deriveFont(Font.BOLD, OverlayPreferences.getUiFontSize() + 3));
		}
	}

	private void fetchRouteFromSpansh() {
		String dest = destinationField.getText();
		if (dest == null || dest.isBlank()) {
			statusLabel.setText("Enter a destination system name.");
			return;
		}
		calculateButton.setEnabled(false);
		statusLabel.setText("Calculating route…");
		new Thread(() -> {
			try {
				long sourceAddr = routeSession.getCurrentSystemAddress();
				String sourceName = routeSession.getCurrentSystemName();
				Long sourceId = sourceAddr != 0L ? Long.valueOf(sourceAddr) : null;
				if (sourceId == null && sourceName != null && !sourceName.isBlank()) {
					sourceId = spanshClient.resolveSystemId64(sourceName);
				}
				if (sourceId == null || sourceId == 0L) {
					SwingUtilities.invokeLater(() -> {
						calculateButton.setEnabled(true);
						statusLabel.setText("Could not resolve current system. Jump once or wait for Location.");
					});
					return;
				}
				Long destId = spanshClient.resolveSystemId64(dest.trim());
				if (destId == null || destId == 0L) {
					SwingUtilities.invokeLater(() -> {
						calculateButton.setEnabled(true);
						statusLabel.setText("Could not resolve destination system name.");
					});
					return;
				}
				if (destId.equals(sourceId)) {
					SwingUtilities.invokeLater(() -> {
						calculateButton.setEnabled(true);
						statusLabel.setText("Destination is the same as current system.");
					});
					return;
				}
				String json = spanshClient.queryFleetCarrierRoute(sourceId.longValue(),
						Collections.singletonList(destId), "fleet", 0, true);
				SwingUtilities.invokeLater(() -> {
					calculateButton.setEnabled(true);
					if (json == null) {
						String err = spanshClient.getLastResultsPollError();
						statusLabel.setText(err != null ? ("Spansh: " + err) : "Spansh route failed or timed out.");
						return;
					}
					JsonObject root;
					try {
						root = JsonParser.parseString(json).getAsJsonObject();
					} catch (Exception ex) {
						statusLabel.setText("Could not parse Spansh response.");
						return;
					}
					importSpanshFleetCarrierRouteFromResultsJson(root);
				});
			} catch (Exception ex) {
				ex.printStackTrace();
				SwingUtilities.invokeLater(() -> {
					calculateButton.setEnabled(true);
					String msg = ex.getMessage();
					statusLabel.setText(msg != null ? ("Error: " + msg) : "Error fetching Spansh route.");
				});
			}
		}, "SpanshFleetCarrierFetch").start();
	}

	@Override
	public void fillSessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		FleetCarrierSessionData d = FleetCarrierSessionMapper.fromRouteSession(routeSession);
		if (destinationField != null) {
			String t = destinationField.getText();
			d.setSpanshDestinationQuery(t != null && !t.isBlank() ? t.trim() : null);
		}
		state.setFleetCarrier(d);
	}

	@Override
	public void applySessionState(EdoSessionState state) {
		if (state == null) {
			return;
		}
		if (state.getFleetCarrier() != null) {
			FleetCarrierSessionData d = state.getFleetCarrier();
			FleetCarrierSessionMapper.applyToRouteSession(routeSession, d);
			if (destinationField != null) {
				String q = d.getSpanshDestinationQuery();
				destinationField.setText(q != null ? q : "");
			}
		}
		int n = state.getFleetCarrier() != null ? state.getFleetCarrier().baseRouteEntriesOrEmpty().size() : 0;
		spanshRouteLoaded = n > 0;
		if (spanshRouteLoaded) {
			setHeaderLabelText("Route: " + n + " systems");
			statusLabel.setText(defaultStatusText);
		} else {
			setHeaderLabelText("Fleet Carrier: (no data)");
		}
		reconcileRouteCurrentWithPostRescanCache();
		rebuildDisplayedEntries();
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
			statusLabel.setText("Invalid/unsupported Spansh fleet-carrier JSON or CSV");
		} else {
			statusLabel.setText(defaultStatusText);
			flushSessionToDisk();
		}
		return ok;
	}

	@Override
	public boolean importSpanshFleetCarrierRouteFromResultsJson(JsonObject root) {
		boolean ok = super.importSpanshFleetCarrierRouteFromResultsJson(root);
		spanshRouteLoaded = ok;
		if (!ok) {
			statusLabel.setText("Invalid/unsupported Spansh fleet-carrier JSON.");
		} else {
			statusLabel.setText(defaultStatusText);
			flushSessionToDisk();
		}
		return ok;
	}

	private static void flushSessionToDisk() {
		OverlayFrame frame = OverlayFrame.overlayFrame;
		if (frame != null) {
			frame.flushSessionStateNow();
		}
	}

	private void doImport(Path file) {
		// Import might throw; ensure the UI stays usable.
		try {
			importSpanshFleetCarrierRouteFile(Objects.requireNonNull(file, "file"));
		} catch (Exception ex) {
			ex.printStackTrace();
			spanshRouteLoaded = false;
			statusLabel.setText("Invalid/unsupported Spansh fleet-carrier JSON or CSV");
		}
	}

	@Override
	public void handleLogEvent(EliteLogEvent event) {
		if (event == null) {
			return;
		}
		// FSS updates SystemCache via System tab; rebuild rows so ?/check matches Route (no NavRoute reload).
		if (event instanceof FssAllBodiesFoundEvent || event instanceof FssDiscoveryScanEvent) {
			rebuildDisplayedEntries();
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
		destinationLabel.setOpaque(false);

		// JButton needs explicit opacity so it doesn't look “wrong” in transparent mode.
		importButton.setOpaque(!opaque ? false : true);
		importButton.setBackground(opaque ? EdoUi.Internal.GRAY_180 : EdoUi.Internal.DARK_ALPHA_220);
		importButton.setForeground(EdoUi.User.MAIN_TEXT);
		calculateButton.setOpaque(!opaque ? false : true);
		calculateButton.setBackground(opaque ? EdoUi.Internal.GRAY_180 : EdoUi.Internal.DARK_ALPHA_220);
		calculateButton.setForeground(EdoUi.User.MAIN_TEXT);
		copyNextDestinationButton.setOpaque(!opaque ? false : true);
		copyNextDestinationButton.setBackground(opaque ? EdoUi.Internal.GRAY_180 : EdoUi.Internal.DARK_ALPHA_220);
		copyNextDestinationButton.setForeground(EdoUi.User.MAIN_TEXT);
		destinationField.setOpaque(opaque);
		if (opaque) {
			destinationField.setBackground(EdoUi.Internal.GRAY_180);
		} else {
			destinationField.setBackground(EdoUi.Internal.DARK_ALPHA_220);
		}
		revalidate();
		repaint();
	}
}

