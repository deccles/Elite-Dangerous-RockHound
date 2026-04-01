package org.dce.ed.route;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;

/**
 * Single owner of route navigation state and base plotted list (no Swing).
 * <p>
 * <b>Threading:</b> Production journal delivery uses {@code LiveJournalMonitor}'s worker thread
 * ({@code Elite-LiveJournalMonitor}), which invokes {@code EliteOverlayTabbedPane#processJournalEvent}
 * without marshaling to the EDT (see {@code OverlayFrame#installTabbedPaneJournalListener}). The route
 * tab may therefore update this session from that background thread while the jump-flash Swing timer
 * and table repaints run on the EDT. This type is not thread-safe; callers should treat journal handling
 * and UI-driven rebuilds as a single-writer sequence or marshal to one thread if the pipeline changes.
 */
public final class RouteSession {

    private final RouteJumpFlashHandle jumpFlash;
    private final RouteCarrierJumpPolicy carrierJumpPolicy;

    private final RouteTargetState targetState = new RouteTargetState();

    private List<RouteEntry> baseRouteEntries = new ArrayList<>();
    private String currentSystemName;
    private long currentSystemAddress;
    private double[] currentStarPos;
    private String pendingJumpSystemName;
    private String pendingJumpLockedName;
    private long pendingJumpLockedAddress;
    private boolean inHyperspace;

    private long displayRevision;

    public RouteSession(RouteJumpFlashHandle jumpFlash, RouteCarrierJumpPolicy carrierJumpPolicy) {
        this.jumpFlash = jumpFlash != null ? jumpFlash : new RouteJumpFlashHandle() {
            @Override
            public boolean isTimerRunning() {
                return false;
            }

            @Override
            public void startTimer() {
            }

            @Override
            public void stopTimer() {
            }
        };
        this.carrierJumpPolicy = carrierJumpPolicy != null ? carrierJumpPolicy : j -> false;
    }

    public RouteTargetState getTargetState() {
        return targetState;
    }

    public List<RouteEntry> getBaseRouteEntries() {
        return baseRouteEntries;
    }

    public String getCurrentSystemName() {
        return currentSystemName;
    }

    public long getCurrentSystemAddress() {
        return currentSystemAddress;
    }

    public double[] getCurrentStarPos() {
        return currentStarPos;
    }

    public String getPendingJumpSystemName() {
        return pendingJumpSystemName;
    }

    public String getPendingJumpLockedName() {
        return pendingJumpLockedName;
    }

    public long getPendingJumpLockedAddress() {
        return pendingJumpLockedAddress;
    }

    public boolean isInHyperspace() {
        return inHyperspace;
    }

    public long getDisplayRevision() {
        return displayRevision;
    }

    public void setCurrentSystemName(String name) {
        if (name == null) {
            return;
        }
        this.currentSystemName = name;
    }

    /**
     * Sets current system name/address/position together (e.g. from {@link org.dce.ed.cache.SystemCache}
     * after startup rescan). Avoids a name-only update leaving a stale {@code currentSystemAddress},
     * which would make {@link RouteGeometry#findSystemRow} match the wrong route row.
     */
    public void applyKnownCurrentSystem(String name, long systemAddress, double[] starPos) {
        if (name == null || name.isBlank()) {
            return;
        }
        this.currentSystemName = name;
        if (systemAddress != 0L) {
            this.currentSystemAddress = systemAddress;
        }
        if (starPos != null && starPos.length >= 3) {
            this.currentStarPos = starPos.clone();
        }
        targetState.clearTargetIfMatchesArrival(this.currentSystemName, this.currentSystemAddress);
    }

    /**
     * Replaces base route after NavRoute.json parse (successful). Clears FSD target and Status destination
     * fields like the previous {@code RouteTabPanel} implementation.
     */
    public void applyNavRouteReloadParsed(List<RouteEntry> parsedEntries) {
        baseRouteEntries = RouteGeometry.deepCopy(parsedEntries != null ? parsedEntries : List.of());
        targetState.applyNavRouteClear();
    }

    /**
     * Clears plotted route and navigation latch state after {@code NavRouteClear} (after optional reload).
     */
    public void clearAfterNavRouteClearEvent() {
        baseRouteEntries.clear();
        targetState.applyNavRouteClear();
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
        inHyperspace = false;
        jumpFlash.stopTimer();
    }

    /**
     * After a successful Spansh import: replace base list and clear target/destination/pending state.
     */
    public void applySpanshImport(List<RouteEntry> entries) {
        baseRouteEntries = RouteGeometry.deepCopy(entries != null ? entries : List.of());
        targetState.applyNavRouteClear();
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
        inHyperspace = false;
        jumpFlash.stopTimer();
    }

    /**
     * Replace base route list only (used when restoring fleet carrier session from persistence).
     * Does not clear target state; caller should apply a persistence snapshot first if needed.
     */
    public void replaceBaseRouteEntries(List<RouteEntry> entries) {
        baseRouteEntries = RouteGeometry.deepCopy(entries != null ? entries : List.of());
    }

    /**
     * When the route table is empty, FSD jump can seed a one-row list.
     */
    public void ensureSingleSystemRowIfBaseEmpty(String systemName, long systemAddress) {
        if (systemName == null || baseRouteEntries == null || !baseRouteEntries.isEmpty()) {
            return;
        }
        RouteEntry entry = new RouteEntry(0, systemName, systemAddress, "?", 0.0, RouteScanStatus.UNKNOWN);
        baseRouteEntries.add(entry);
    }

    /**
     * Journal events other than NavRoute file triggers (handled by the panel).
     */
    public RouteJournalApplyOutcome applySecondaryJournalEvent(EliteLogEvent event) {
        if (event == null) {
            return new RouteJournalApplyOutcome(false, false);
        }
        if (event instanceof FsdTargetEvent target) {
            if (inHyperspace || jumpFlash.isTimerRunning()) {
                return new RouteJournalApplyOutcome(false, false);
            }
            targetState.applyFsdTargetEvent(target, inHyperspace, jumpFlash.isTimerRunning());
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof LocationEvent loc) {
            setCurrentSystemName(loc.getStarSystem());
            currentSystemAddress = loc.getSystemAddress();
            currentStarPos = loc.getStarPos();
            targetState.clearTargetIfMatchesArrival(loc.getStarSystem(), loc.getSystemAddress());
            clearPendingJumpState();
            inHyperspace = false;
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof FsdJumpEvent jump) {
            setCurrentSystemName(jump.getStarSystem());
            currentSystemAddress = jump.getSystemAddress();
            currentStarPos = jump.getStarPos();
            targetState.clearTargetIfMatchesArrival(jump.getStarSystem(), jump.getSystemAddress());
            clearPendingJumpState();
            inHyperspace = false;
            jumpFlash.stopTimer();
            ensureSingleSystemRowIfBaseEmpty(getCurrentSystemName(), currentSystemAddress);
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof CarrierJumpEvent jump) {
            if (!carrierJumpPolicy.shouldUpdateCurrentSystem(jump)) {
                return new RouteJournalApplyOutcome(false, false);
            }
            setCurrentSystemName(jump.getStarSystem());
            currentSystemAddress = jump.getSystemAddress();
            currentStarPos = jump.getStarPos();
            targetState.clearTargetIfMatchesArrival(jump.getStarSystem(), jump.getSystemAddress());
            clearPendingJumpState();
            inHyperspace = false;
            jumpFlash.stopTimer();
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof CarrierLocationEvent loc) {
            setCurrentSystemName(loc.getStarSystem());
            currentSystemAddress = loc.getSystemAddress();
            targetState.clearTargetIfMatchesArrival(loc.getStarSystem(), loc.getSystemAddress());
            clearPendingJumpState();
            inHyperspace = false;
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof StatusEvent se) {
            return applyStatusEvent(se);
        }
        return new RouteJournalApplyOutcome(false, false);
    }

    private void clearPendingJumpState() {
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
    }

    private RouteJournalApplyOutcome applyStatusEvent(StatusEvent se) {
        boolean hyperdriveCharging = se.isFsdHyperdriveCharging();
        boolean inHyperspaceNow = se.isFsdJump();
        inHyperspace = inHyperspaceNow;
        boolean preJumpCharging = hyperdriveCharging && !inHyperspaceNow;
        boolean timerRunning = jumpFlash.isTimerRunning();

        List<RouteTargetState.RouteSystemRef> refs = new ArrayList<>();
        for (RouteEntry e : baseRouteEntries) {
            if (e == null) {
                continue;
            }
            refs.add(new RouteTargetState.RouteSystemRef(e.systemName, e.systemAddress));
        }
        boolean clearedSideTrip = targetState.applyStatusEvent(se, refs);
        if (clearedSideTrip) {
            return new RouteJournalApplyOutcome(true, true);
        }
        if (preJumpCharging && !timerRunning) {
            pendingJumpLockedName = targetState.getDestinationName();
            pendingJumpLockedAddress = (targetState.getDestinationSystemAddress() != null)
                    ? targetState.getDestinationSystemAddress().longValue()
                    : 0L;
            pendingJumpSystemName = se.getDestinationDisplayName();
            jumpFlash.startTimer();
        }
        if (!preJumpCharging && !inHyperspaceNow && timerRunning) {
            jumpFlash.stopTimer();
            pendingJumpSystemName = null;
            pendingJumpLockedName = null;
            pendingJumpLockedAddress = 0L;
        }
        return new RouteJournalApplyOutcome(false, true);
    }

    /** Fleet carrier pending-jump blink (mirrors Status charging latch). */
    public void startCarrierPendingJumpBlink(String destName, long destAddress) {
        pendingJumpLockedName = (destName != null && !destName.isBlank()) ? destName : null;
        pendingJumpLockedAddress = destAddress;
        pendingJumpSystemName = pendingJumpLockedName;
        jumpFlash.startTimer();
    }

    public void stopCarrierPendingJumpBlink() {
        jumpFlash.stopTimer();
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
    }

    public RoutePersistenceSnapshot toPersistenceSnapshot() {
        return new RoutePersistenceSnapshot(
                currentSystemName,
                currentSystemAddress != 0L ? Long.valueOf(currentSystemAddress) : null,
                currentStarPos,
                targetState.getTargetSystemName(),
                targetState.getTargetSystemAddress() != 0L ? Long.valueOf(targetState.getTargetSystemAddress()) : null,
                targetState.getDestinationSystemAddress(),
                targetState.getDestinationBodyId(),
                targetState.getDestinationName(),
                pendingJumpLockedName,
                pendingJumpLockedAddress != 0L ? Long.valueOf(pendingJumpLockedAddress) : null,
                Boolean.valueOf(inHyperspace));
    }

    public void applyPersistenceSnapshot(RoutePersistenceSnapshot snap) {
        if (snap == null) {
            return;
        }
        if (snap.currentSystemName() != null) {
            currentSystemName = snap.currentSystemName();
        }
        if (snap.currentSystemAddress() != null) {
            currentSystemAddress = snap.currentSystemAddress().longValue();
        }
        if (snap.currentStarPos() != null && snap.currentStarPos().length >= 3) {
            currentStarPos = snap.currentStarPos();
        }
        targetState.restoreFromPersistence(
                snap.targetSystemName(),
                snap.targetSystemAddress(),
                snap.destinationSystemAddress(),
                snap.destinationBodyId(),
                snap.destinationName());
        pendingJumpLockedName = snap.pendingJumpLockedName();
        pendingJumpLockedAddress = (snap.pendingJumpLockedAddress() != null)
                ? snap.pendingJumpLockedAddress().longValue()
                : 0L;
        if (snap.inHyperspace() != null) {
            inHyperspace = snap.inHyperspace().booleanValue();
        }
    }

    public RouteDisplaySnapshot buildDisplaySnapshot(Consumer<List<RouteEntry>> afterDeepCopyBeforeSynthetics,
            RouteCoordsResolver coordsResolver) {
        displayRevision++;
        List<RouteEntry> rows = RouteLayoutEngine.buildDisplayedEntries(
                baseRouteEntries,
                afterDeepCopyBeforeSynthetics,
                currentSystemName,
                currentSystemAddress,
                currentStarPos,
                targetState,
                pendingJumpLockedName,
                pendingJumpLockedAddress,
                coordsResolver,
                jumpFlash.isTimerRunning());
        return new RouteDisplaySnapshot(displayRevision, rows);
    }
}
