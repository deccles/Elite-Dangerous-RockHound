package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.dce.ed.exec.ExecTriggerId;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.OwnedFleetCarrierJournalBootstrap;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierJumpRequestEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.FleetCarrierSessionData;
import org.dce.ed.session.FleetCarrierSessionMapper;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.SelectiveHitSupport;
import org.dce.ed.ui.SystemNameAutocomplete;
import org.dce.ed.util.SpanshClient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Fleet Carrier tab:
 * - Loads a Spansh fleet-carrier route from JSON/CSV (e.g. file drop) or from the built-in Spansh query.
 * - Reacts to carrier jump scheduling ({@code CarrierJumpRequest}), completion ({@code CarrierJump}),
 *   cancellation ({@code CarrierJumpCancelled}), and {@code CarrierLocation}.
 * - Refreshes the route status column after FSS events (same {@link org.dce.ed.cache.SystemCache} data as
 *   the Route tab; does not reload {@code NavRoute.json}).
 * - After each carrier jump, copies the next system name to clipboard (and shows the “Copied: …” toast).
 */
public class FleetCarrierTabPanel extends RouteTabPanel {
	private static final long serialVersionUID = 1L;

	@Override
	protected ExecTriggerId copyNextDestinationTrigger() {
		return ExecTriggerId.FLEET_CARRIER_COPY_NEXT_DESTINATION;
	}

	private final String defaultStatusText = " ";

	private volatile boolean spanshRouteLoaded = false;

	/** {@code true} when a Spansh fleet-carrier route is loaded on this tab. */
	public boolean isSpanshRouteLoaded() {
		return spanshRouteLoaded;
	}
	private volatile boolean pendingJumpFromOwnedCarrier;

	private final OwnedFleetCarrierTracker ownedFleetCarrierTracker;

	private final SpanshClient spanshClient = new SpanshClient();
	private final JPanel topBar;
	private final JLabel statusLabel;
	private final JLabel destinationLabel;
	private final JTextField destinationField;
	private final JButton calculateButton;
	private final JButton clearRouteButton;
	private final SystemNameAutocomplete destinationAutocomplete;

	public FleetCarrierTabPanel(BooleanSupplier passThroughEnabledSupplier) {
		this(passThroughEnabledSupplier, new OwnedFleetCarrierTracker());
	}

	public FleetCarrierTabPanel(BooleanSupplier passThroughEnabledSupplier, OwnedFleetCarrierTracker ownedFleetCarrierTracker) {
		super(passThroughEnabledSupplier);
		this.ownedFleetCarrierTracker = ownedFleetCarrierTracker != null
				? ownedFleetCarrierTracker
				: new OwnedFleetCarrierTracker();

		setHeaderLabelText("Fleet Carrier: (no data)");

		setOpaque(false);
		setBackground(EdoUi.Internal.TRANSPARENT);

		Font base = OverlayPreferences.getUiFont();

		destinationLabel = new JLabel("Destination:");
		destinationLabel.setOpaque(false);
		destinationLabel.setForeground(EdoUi.User.MAIN_TEXT);
		destinationLabel.setFont(base);

		destinationField = new JTextField();
		// Click-to-focus only — avoid stealing focus when the overlay/dialog opens.
		destinationField.setFocusable(false);
		destinationField.setOpaque(true);
		destinationField.setForeground(EdoUi.User.MAIN_TEXT);
		destinationField.setCaretColor(EdoUi.User.MAIN_TEXT);
		destinationField.setBackground(EdoUi.Internal.DARK_ALPHA_220);
		destinationField.setFont(base);
		destinationField.setToolTipText("Destination system name (EDSM autocomplete)");
		destinationField.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				destinationField.setFocusable(true);
				destinationField.requestFocusInWindow();
				// Click-to-focus often leaves the whole value selected; place the caret at the click.
				SwingUtilities.invokeLater(() -> {
					if (!destinationField.isFocusOwner()) {
						return;
					}
					int pos = destinationField.viewToModel2D(e.getPoint());
					if (pos < 0) {
						pos = destinationField.getText().length();
					}
					destinationField.setCaretPosition(pos);
				});
			}
		});
		destinationField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				if (e.isTemporary()) {
					return;
				}
				// Keep focusable while navigating the autocomplete popup list.
				Component opposite = e.getOppositeComponent();
				if (opposite != null) {
					for (Component c = opposite; c != null; c = c.getParent()) {
						if (c instanceof JPopupMenu) {
							return;
						}
					}
				}
				// Drop any highlight so the field doesn't look "selected" while unfocused.
				int caret = destinationField.getCaretPosition();
				destinationField.select(caret, caret);
				destinationField.setFocusable(false);
			}
		});

		destinationAutocomplete = new SystemNameAutocomplete(destinationField, edsmClient());

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
		styleFleetSecondaryButton(calculateButton);
		calculateButton.addActionListener(e -> fetchRouteFromSpansh());

		clearRouteButton = new JButton("Clear");
		styleCopyNextDestinationButton(clearRouteButton, base);
		clearRouteButton.setToolTipText("Clear the loaded fleet carrier route");
		clearRouteButton.addActionListener(e -> clearFleetCarrierRoute());
		addCopyStripComponentLeft(Box.createHorizontalStrut(10));
		addCopyStripComponentLeft(clearRouteButton);

		JPanel fetchRow = new JPanel(new BorderLayout(10, 0));
		fetchRow.setOpaque(false);
		fetchRow.setBackground(EdoUi.Internal.TRANSPARENT);
		fetchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		fetchRow.add(destinationLabel, BorderLayout.WEST);
		fetchRow.add(destinationField, BorderLayout.CENTER);
		fetchRow.add(calculateButton, BorderLayout.EAST);

		statusLabel = new JLabel(" ", SwingConstants.LEFT);
		statusLabel.setOpaque(false);
		statusLabel.setForeground(EdoUi.Internal.MAIN_TEXT_ALPHA_180);
		statusLabel.setFont(base);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		topBar = new JPanel();
		topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
		topBar.setOpaque(false);
		topBar.setBackground(EdoUi.Internal.TRANSPARENT);
		topBar.setBorder(new EmptyBorder(0, 4, 4, 4));
		topBar.add(fetchRow);
		topBar.add(Box.createVerticalStrut(4));
		topBar.add(statusLabel);

		// Destination above "Route: n systems"; keep the parent's title row under this stack.
		Component oldNorth = null;
		if (getLayout() instanceof BorderLayout bl) {
			oldNorth = bl.getLayoutComponent(BorderLayout.NORTH);
		}
		if (oldNorth != null) {
			remove(oldNorth);
			if (oldNorth instanceof JComponent jc) {
				jc.setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			topBar.add(Box.createVerticalStrut(6));
			topBar.add(oldNorth);
		}
		add(topBar, BorderLayout.NORTH);

		applyOverlayBackground(EdoUi.Internal.TRANSPARENT, OverlayPreferences.overlayChromeRequestsTransparency(this));
	}

	/** Selective mode: destination field, Calculate, and route controls stay clickable. */
	@Override
	public boolean isPointerOverInteractiveRegion(Point screenPoint) {
		if (destinationField != null && destinationField.isFocusOwner()) {
			return true;
		}
		// Hit the field/button directly (not only topBar) so layout struts cannot shrink the target.
		if (SelectiveHitSupport.containsScreenPoint(destinationField, screenPoint)
				|| SelectiveHitSupport.containsScreenPoint(calculateButton, screenPoint)
				|| SelectiveHitSupport.containsScreenPoint(clearRouteButton, screenPoint)
				|| SelectiveHitSupport.containsScreenPoint(topBar, screenPoint)) {
			return true;
		}
		if (destinationAutocomplete != null && destinationAutocomplete.isPointerOverPopup(screenPoint)) {
			return true;
		}
		return super.isPointerOverInteractiveRegion(screenPoint);
	}

	private static void styleFleetSecondaryButton(JButton b) {
		b.setFocusable(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setMargin(new Insets(5, 12, 5, 12));
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setBorderPainted(true);
		b.setBackground(EdoUi.Internal.TRANSPARENT);
		b.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_180, 1),
				new EmptyBorder(2, 4, 2, 4)));
	}

	/** Clears the loaded carrier route (session rows, header, pending-jump display state). */
	private void clearFleetCarrierRoute() {
		routeSession.clearAfterNavRouteClearEvent();
		spanshRouteLoaded = false;
		setHeaderLabelText("Fleet Carrier: (no data)");
		statusLabel.setText("Route cleared.");
		rebuildDisplayedEntries();
		fireSessionStateChanged();
		flushSessionToDisk();
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
				long sourceAddr = ownedFleetCarrierTracker.getOwnedSystemAddress();
				String sourceName = ownedFleetCarrierTracker.getOwnedSystemName();
				Long sourceId = sourceAddr != 0L ? Long.valueOf(sourceAddr) : null;
				if (sourceId == null && sourceName != null && !sourceName.isBlank()) {
					sourceId = spanshClient.resolveSystemId64(sourceName);
				}
				if (sourceId == null || sourceId == 0L) {
					SwingUtilities.invokeLater(() -> {
						calculateButton.setEnabled(true);
						if (!ownedFleetCarrierTracker.hasOwnedCarrierLocation()) {
							statusLabel.setText(
									"Unknown owned carrier location. Open your carrier management or wait for your carrier to jump.");
						} else {
							statusLabel.setText("Could not resolve owned carrier system for Spansh.");
						}
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
	public void applyUiFont(Font font) {
		super.applyUiFont(font);
		if (font != null) {
			if (statusLabel != null) {
				statusLabel.setFont(font);
			}
			if (destinationLabel != null) {
				destinationLabel.setFont(font);
			}
			if (destinationField != null) {
				destinationField.setFont(font);
			}
			if (clearRouteButton != null) {
				styleCopyNextDestinationButton(clearRouteButton, font);
			}
		}
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
        if (ownedFleetCarrierTracker != null) {
            d.setOwnedCarrierId(ownedFleetCarrierTracker.hasOwnedCarrierId()
                    ? Long.valueOf(ownedFleetCarrierTracker.getOwnedCarrierId()) : null);
            d.setOwnedCarrierSystemName(ownedFleetCarrierTracker.getOwnedSystemName());
            d.setOwnedCarrierSystemAddress(ownedFleetCarrierTracker.getOwnedSystemAddress() != 0L
                    ? Long.valueOf(ownedFleetCarrierTracker.getOwnedSystemAddress()) : null);
            d.setOwnedCarrierStarPos(ownedFleetCarrierTracker.getOwnedStarPos());
            if (ownedFleetCarrierTracker.hasOwnedCarrierLocation()) {
                d.setCurrentSystemName(ownedFleetCarrierTracker.getOwnedSystemName());
                d.setCurrentSystemAddress(ownedFleetCarrierTracker.getOwnedSystemAddress() != 0L
                        ? Long.valueOf(ownedFleetCarrierTracker.getOwnedSystemAddress()) : null);
                d.setCurrentStarPos(ownedFleetCarrierTracker.getOwnedStarPos());
            }
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
			restoreOwnedCarrierFromSession(d);
			if (destinationField != null) {
				String q = d.getSpanshDestinationQuery();
				destinationField.setText(q != null ? q : "");
				destinationField.select(0, 0);
			}
		}
		bootstrapOwnedFleetCarrierFromJournalIfNeeded();
		if (isOwnedCarrierJumpPending()) {
			pendingJumpFromOwnedCarrier = true;
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
	protected void reconcileRouteCurrentWithPostRescanCache() {
		if (ownedFleetCarrierTracker.hasOwnedCarrierLocation()) {
			applyOwnedCarrierLocationToRouteSession();
		}
	}

	@Override
	protected boolean shouldUpdateOnCarrierJump(CarrierJumpEvent jump) {
		return ownedFleetCarrierTracker.isOwnedCarrierJump(jump, pendingJumpFromOwnedCarrier);
	}

	/** Carrier jumps burn tritium, not the ship's main tank — ship fuel prediction doesn't apply. */
	@Override
	protected boolean routeFuelPredictionApplies() {
		return false;
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
			if (!acceptOwnedCarrierRequest(req)) {
				return;
			}
			pendingJumpFromOwnedCarrier = true;
			applyScheduledJumpDestinationIfNeeded(req);
			// The journal request is authoritative: a manual jump may be off-route, so only fall
			// back to the loaded route's next hop when the request carries no system name.
			String blinkName = req.getSystemName();
			long blinkAddr = req.getSystemAddress();
			if ((blinkName == null || blinkName.isBlank()) && spanshRouteLoaded) {
				String routeNext = RouteTabPanel.nextRouteDestinationSystemName(routeSession);
				if (routeNext != null && !routeNext.isBlank()) {
					blinkName = routeNext;
					blinkAddr = routeSystemAddress(routeNext);
				}
			}
			startPendingJumpBlink(blinkName, blinkAddr, req.getDepartureTime());
			return;
		}
		if (event.getType() == EliteEventType.CARRIER_JUMP_CANCELLED) {
			pendingJumpFromOwnedCarrier = false;
			stopPendingJumpBlink();
			return;
		}
		if (event instanceof CarrierJumpEvent jump) {
			if (!ownedFleetCarrierTracker.isOwnedCarrierJump(jump, pendingJumpFromOwnedCarrier)) {
				return;
			}
			pendingJumpFromOwnedCarrier = false;
			ownedFleetCarrierTracker.onOwnedCarrierJumpCompleted(jump);
			syncOwnedCarrierRouteMarker();
			super.handleLogEvent(event);
			if (spanshRouteLoaded) {
				SwingUtilities.invokeLater(() -> copyNextSystemFromBaseRoute(jump.getSystemAddress()));
			}
		} else if (event instanceof CarrierLocationEvent loc) {
			if (!ownedFleetCarrierTracker.isOwnedCarrierId(loc.getCarrierId())) {
				return;
			}
			// CarrierLocation often appears before CarrierJump when aboard; with a Spansh route we only advance
			// on jump completion. Off-carrier owners get CarrierLocation at DepartureTime instead of CarrierJump.
			if (!spanshRouteLoaded) {
				ownedFleetCarrierTracker.onOwnedCarrierLocationArrival(loc);
				syncOwnedCarrierRouteMarker();
				super.handleLogEvent(event);
				return;
			}
			if (routeSession.isPendingCarrierJumpArrival(loc)) {
				pendingJumpFromOwnedCarrier = false;
				ownedFleetCarrierTracker.onOwnedCarrierLocationArrival(loc);
				syncOwnedCarrierRouteMarker();
				super.handleLogEvent(event);
				SwingUtilities.invokeLater(() -> copyNextSystemFromBaseRoute(loc.getSystemAddress()));
			}
		}
	}

	/** True while an owned-carrier jump is scheduled or in progress (journal latch or route session pending hop). */
	public boolean isOwnedCarrierJumpPending() {
		if (pendingJumpFromOwnedCarrier) {
			return true;
		}
		String locked = routeSession.getPendingJumpLockedName();
		return (locked != null && !locked.isBlank()) || routeSession.getPendingJumpLockedAddress() != 0L;
	}

	@Override
	protected void startPendingJumpBlink(String destName, long destAddress, java.time.Instant departureTime) {
		pendingJumpFromOwnedCarrier = true;
		super.startPendingJumpBlink(destName, destAddress, departureTime);
	}

	private boolean acceptOwnedCarrierRequest(CarrierJumpRequestEvent req) {
		if (req == null) {
			return false;
		}
		if (ownedFleetCarrierTracker.hasOwnedCarrierId()) {
			return ownedFleetCarrierTracker.isOwnedCarrierId(req.getCarrierId());
		}
		return req.getCarrierId() == 0L;
	}

	private void restoreOwnedCarrierFromSession(FleetCarrierSessionData d) {
		if (d == null) {
			return;
		}
		ownedFleetCarrierTracker.applyPersisted(
				d.getOwnedCarrierId(),
				d.getOwnedCarrierSystemName(),
				d.getOwnedCarrierSystemAddress(),
				d.getOwnedCarrierStarPos());
		applyOwnedCarrierLocationToRouteSession();
	}

	private void applyOwnedCarrierLocationToRouteSession() {
		if (!ownedFleetCarrierTracker.hasOwnedCarrierLocation()) {
			return;
		}
		routeSession.applyKnownCurrentSystem(
				ownedFleetCarrierTracker.getOwnedSystemName(),
				ownedFleetCarrierTracker.getOwnedSystemAddress(),
				ownedFleetCarrierTracker.getOwnedStarPos());
		rebuildDisplayedEntries();
	}

	void bootstrapOwnedFleetCarrierFromJournalIfNeeded() {
		boolean needId = !ownedFleetCarrierTracker.hasOwnedCarrierId();
		boolean needLoc = !ownedFleetCarrierTracker.hasOwnedCarrierLocation();
		if (!needId && !needLoc) {
			syncOwnedCarrierRouteMarker();
			return;
		}
		OwnedFleetCarrierJournalBootstrap.replayInto(ownedFleetCarrierTracker);
		syncOwnedCarrierRouteMarker();
	}

	public void syncOwnedCarrierRouteMarker() {
		applyOwnedCarrierLocationToRouteSession();
	}

	OwnedFleetCarrierTracker ownedFleetCarrierTrackerForTests() {
		return ownedFleetCarrierTracker;
	}

	void applyScheduledJumpDestinationIfNeeded(CarrierJumpRequestEvent req) {
		if (req == null || spanshRouteLoaded || destinationField == null) {
			return;
		}
		String current = destinationField.getText();
		if (current != null && !current.isBlank()) {
			return;
		}
		String requestedDestination = req.getSystemName();
		if (requestedDestination == null || requestedDestination.isBlank()) {
			return;
		}

		Runnable update = () -> {
			if (spanshRouteLoaded) {
				return;
			}
			String latest = destinationField.getText();
			if (latest != null && !latest.isBlank()) {
				return;
			}
			destinationField.setText(requestedDestination.trim());
			statusLabel.setText("Destination set from scheduled carrier jump.");
			fireSessionStateChanged();
		};

		if (SwingUtilities.isEventDispatchThread()) {
			update.run();
		} else {
			SwingUtilities.invokeLater(update);
		}
	}

	private long routeSystemAddress(String systemName) {
		if (systemName == null || systemName.isBlank()) {
			return 0L;
		}
		for (RouteEntry e : routeSession.getBaseRouteEntries()) {
			if (e == null || e.isBodyRow) {
				continue;
			}
			if (systemName.equals(e.systemName)) {
				return e.systemAddress;
			}
		}
		return 0L;
	}

	String destinationQueryForTests() {
		return destinationField != null ? destinationField.getText() : null;
	}

	void setDestinationQueryForTests(String destination) {
		if (destinationField != null) {
			destinationField.setText(destination != null ? destination : "");
		}
	}

	/**
	 * Update button opacity/colors when the overlay transparency changes.
	 * This keeps the tab consistent with the original Route/System tabs.
	 */
	@Override
	public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
		super.applyOverlayBackground(bgWithAlpha, treatAsTransparent);
		boolean opaque = !treatAsTransparent;

		topBar.setOpaque(false);
		statusLabel.setOpaque(false);
		destinationLabel.setOpaque(false);

		calculateButton.setForeground(EdoUi.User.MAIN_TEXT);
		if (clearRouteButton != null) {
			styleCopyNextDestinationButton(clearRouteButton, OverlayPreferences.getUiFont());
		}

		// Always paint a real background. Opaque=false + alpha chrome leaves layered pixels at
		// alpha 0, and Windows ignores clicks there even after selective mode clears WS_EX_TRANSPARENT.
		destinationField.setOpaque(true);
		if (opaque) {
			destinationField.setBackground(EdoUi.Internal.GRAY_180);
		} else {
			destinationField.setBackground(EdoUi.Internal.DARK_ALPHA_220);
		}
		revalidate();
		repaint();
	}
}
