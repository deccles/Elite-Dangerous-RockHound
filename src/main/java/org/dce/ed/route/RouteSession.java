package org.dce.ed.route;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.dce.ed.logreader.CarrierJumpCooldown;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.CarrierLocationEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;

import com.google.gson.JsonObject;

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
    /**
     * Index into {@link #baseRouteEntries} for the CURRENT hop. Advances on arrival to a match
     * at/after this index so custom-route loops (duplicate systems) do not snap back to hop 0.
     */
    private int currentBaseIndex;
    private boolean customRouteLoopEnabledForArrivals;
    private String pendingJumpSystemName;
    private String pendingJumpLockedName;
    private long pendingJumpLockedAddress;
    private Instant pendingJumpDepartureTime;
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

    /** Base-route index of the CURRENT hop (monotonic for custom-route loops). */
    public int getCurrentBaseIndex() {
        return currentBaseIndex;
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

    public Instant getPendingJumpDepartureTime() {
        return pendingJumpDepartureTime;
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
        applyKnownCurrentSystem(name, systemAddress, starPos, customRouteLoopEnabledForArrivals);
    }

    public void applyKnownCurrentSystem(String name, long systemAddress, double[] starPos,
            boolean customRouteLoopEnabled) {
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
        boolean wrapToStart = customRouteLoopEnabled
                && baseRouteEntries.size() > 1
                && currentBaseIndex == baseRouteEntries.size() - 1
                && RouteGeometry.rowMatchesSystem(baseRouteEntries.get(0), name, systemAddress);
        if (wrapToStart) {
            currentBaseIndex = 0;
        } else {
            advanceCurrentBaseIndexForArrival(this.currentSystemName, this.currentSystemAddress);
        }
        targetState.clearTargetIfMatchesArrival(this.currentSystemName, this.currentSystemAddress);
    }

    public void setCustomRouteLoopEnabledForArrivals(boolean enabled) {
        customRouteLoopEnabledForArrivals = enabled;
    }

    /**
     * Moves {@link #currentBaseIndex} forward to the first hop at/after the current index that
     * matches the arrival system. Never moves backward (earlier duplicates stay behind).
     */
    public void advanceCurrentBaseIndexForArrival(String name, long systemAddress) {
        if (baseRouteEntries == null || baseRouteEntries.isEmpty()) {
            currentBaseIndex = 0;
            return;
        }
        clampCurrentBaseIndex();
        RouteEntry atCursor = baseRouteEntries.get(currentBaseIndex);
        boolean alreadyMatch = RouteGeometry.rowMatchesSystem(atCursor, name, systemAddress);
        if (alreadyMatch) {
            return;
        }
        // Prefer strictly after the cursor so a false address/name hit on the current hop
        // cannot keep us stuck when the real arrival is the next loop visit.
        int foundAfter = RouteGeometry.findSystemRowFrom(
                baseRouteEntries, name, systemAddress, currentBaseIndex + 1);
        int found = foundAfter;
        if (found < 0) {
            found = RouteGeometry.findSystemRowFrom(
                    baseRouteEntries, name, systemAddress, currentBaseIndex);
        }
        if (found >= 0) {
            currentBaseIndex = found;
        }
    }

    private void clampCurrentBaseIndex() {
        if (baseRouteEntries == null || baseRouteEntries.isEmpty()) {
            currentBaseIndex = 0;
            return;
        }
        if (currentBaseIndex < 0) {
            currentBaseIndex = 0;
        } else if (currentBaseIndex >= baseRouteEntries.size()) {
            currentBaseIndex = baseRouteEntries.size() - 1;
        }
    }

    private void resetCurrentBaseIndex() {
        currentBaseIndex = 0;
    }

    /**
     * Replaces base route after NavRoute.json parse (successful). Clears FSD target and Status destination
     * fields like the previous {@code RouteTabPanel} implementation.
     */
    public void applyNavRouteReloadParsed(List<RouteEntry> parsedEntries) {
        baseRouteEntries = RouteGeometry.deepCopy(parsedEntries != null ? parsedEntries : List.of());
        renumberBaseIndexes();
        targetState.applyNavRouteClear();
        resetCurrentBaseIndex();
        if (currentSystemName != null && !currentSystemName.isBlank()) {
            advanceCurrentBaseIndexForArrival(currentSystemName, currentSystemAddress);
        }
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
        resetCurrentBaseIndex();
    }

    /**
     * After a successful Spansh import: replace base list and clear target/destination/pending state.
     */
    public void applySpanshImport(List<RouteEntry> entries) {
        baseRouteEntries = RouteGeometry.deepCopy(entries != null ? entries : List.of());
        renumberBaseIndexes();
        targetState.applyNavRouteClear();
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
        inHyperspace = false;
        jumpFlash.stopTimer();
        resetCurrentBaseIndex();
        if (currentSystemName != null && !currentSystemName.isBlank()) {
            advanceCurrentBaseIndexForArrival(currentSystemName, currentSystemAddress);
        }
    }

    /**
     * Replace base route list only (used when restoring fleet carrier session from persistence).
     * Does not clear target state; caller should apply a persistence snapshot first if needed.
     * Preserves {@link #currentBaseIndex} when the hop at that index still matches the commander.
     */
    public void replaceBaseRouteEntries(List<RouteEntry> entries) {
        baseRouteEntries = RouteGeometry.deepCopy(entries != null ? entries : List.of());
        renumberBaseIndexes();
        clampCurrentBaseIndex();
        if (currentSystemName != null && !currentSystemName.isBlank()) {
            if (baseRouteEntries.isEmpty()
                    || !RouteGeometry.rowMatchesSystem(
                            baseRouteEntries.get(currentBaseIndex), currentSystemName, currentSystemAddress)) {
                advanceCurrentBaseIndexForArrival(currentSystemName, currentSystemAddress);
            }
        }
    }

    /**
     * Appends a plotted hop to the end of the base route (e.g. pasted system names).
     * Synthetic / body flags on the entry are cleared so the hop participates in route advance.
     */
    public void appendBaseRouteEntry(RouteEntry entry) {
        if (entry == null || entry.systemName == null || entry.systemName.isBlank()) {
            return;
        }
        RouteEntry copy = entry.copy();
        copy.isSynthetic = false;
        copy.isBodyRow = false;
        copy.indentLevel = 0;
        copy.index = baseRouteEntries.size();
        if (copy.status == null) {
            copy.status = RouteScanStatus.UNKNOWN;
        }
        if (copy.starClass == null || copy.starClass.isBlank()) {
            copy.starClass = "?";
        }
        baseRouteEntries.add(copy);
        renumberBaseIndexes();
    }

    /**
     * Ensures the commander’s current system is the first base hop when missing, so pasted
     * destinations append after “you are here” instead of appearing as the route origin.
     */
    public void ensureCurrentSystemAtStartIfMissing(String systemName, long systemAddress, double[] starPos) {
        if (systemName == null || systemName.isBlank()) {
            return;
        }
        if (RouteGeometry.findSystemRow(baseRouteEntries, systemName, systemAddress) >= 0) {
            return;
        }
        RouteEntry here = new RouteEntry(0, systemName.trim(), systemAddress, "?", 0.0, RouteScanStatus.UNKNOWN);
        if (starPos != null && starPos.length == 3) {
            here.x = Double.valueOf(starPos[0]);
            here.y = Double.valueOf(starPos[1]);
            here.z = Double.valueOf(starPos[2]);
        }
        baseRouteEntries.add(0, here);
        renumberBaseIndexes();
        // New "you are here" is hop 0; shift any prior cursor.
        currentBaseIndex = 0;
    }

    /**
     * Moves a base-route hop. {@code toIndex} is the insertion index before the move
     * ({@code 0..size}); after removing {@code fromIndex}, the entry is inserted at the
     * adjusted position. Returns {@code false} when the move is a no-op or out of range.
     */
    public boolean moveBaseRouteEntry(int fromIndex, int toIndex) {
        int size = baseRouteEntries.size();
        if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex > size) {
            return false;
        }
        if (toIndex == fromIndex || toIndex == fromIndex + 1) {
            return false;
        }
        int oldCurrent = currentBaseIndex;
        RouteEntry moved = baseRouteEntries.remove(fromIndex);
        int insertAt = toIndex;
        if (insertAt > fromIndex) {
            insertAt--;
        }
        baseRouteEntries.add(insertAt, moved);
        renumberBaseIndexes();
        if (oldCurrent == fromIndex) {
            currentBaseIndex = insertAt;
        } else {
            int idx = oldCurrent;
            if (fromIndex < idx) {
                idx--;
            }
            if (insertAt <= idx) {
                idx++;
            }
            currentBaseIndex = idx;
        }
        clampCurrentBaseIndex();
        return true;
    }

    private void renumberBaseIndexes() {
        for (int i = 0; i < baseRouteEntries.size(); i++) {
            RouteEntry e = baseRouteEntries.get(i);
            if (e != null) {
                e.index = i;
            }
        }
    }

    /**
     * When there is no plotted NavRoute, keep a one-row “you are here” list in sync with arrivals.
     * <p>
     * First arrival seeds the row; later {@code Location}/{@code FSDJump}/carrier arrivals rewrite that
     * same placeholder so the Route tab does not keep showing the system where the seed was created.
     * Real plotted routes have 2+ hops and are left untouched.
     */
    public void ensureSingleSystemRowIfBaseEmpty(String systemName, long systemAddress) {
        syncNoRouteCurrentSystemPlaceholder(systemName, systemAddress);
    }

    /**
     * Seeds or updates the solitary “no plotted route” placeholder row for the commander’s location.
     */
    public void syncNoRouteCurrentSystemPlaceholder(String systemName, long systemAddress) {
        if (systemName == null || systemName.isBlank() || baseRouteEntries == null) {
            return;
        }
        if (baseRouteEntries.isEmpty()) {
            baseRouteEntries.add(new RouteEntry(0, systemName, systemAddress, "?", 0.0, RouteScanStatus.UNKNOWN));
            return;
        }
        if (baseRouteEntries.size() != 1) {
            return;
        }
        RouteEntry only = baseRouteEntries.get(0);
        if (only == null || only.isSynthetic || only.isBodyRow) {
            return;
        }
        boolean sameName = systemName.equals(only.systemName);
        boolean sameAddress = systemAddress == 0L || only.systemAddress == systemAddress;
        if (sameName && sameAddress) {
            return;
        }
        only.systemName = systemName;
        if (systemAddress != 0L) {
            only.systemAddress = systemAddress;
        }
        only.starClass = "?";
        only.status = RouteScanStatus.UNKNOWN;
        only.x = null;
        only.y = null;
        only.z = null;
        only.distanceLy = null;
        only.markerKind = RouteMarkerKind.NONE;
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
            applyKnownCurrentSystem(loc.getStarSystem(), loc.getSystemAddress(), loc.getStarPos());
            clearPendingJumpState();
            inHyperspace = false;
            syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof FsdJumpEvent jump) {
            applyKnownCurrentSystem(jump.getStarSystem(), jump.getSystemAddress(), jump.getStarPos());
            clearPendingJumpState();
            inHyperspace = false;
            jumpFlash.stopTimer();
            syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof CarrierJumpEvent jump) {
            if (!carrierJumpPolicy.shouldUpdateCurrentSystem(jump)) {
                return new RouteJournalApplyOutcome(false, false);
            }
            applyKnownCurrentSystem(jump.getStarSystem(), jump.getSystemAddress(), jump.getStarPos());
            clearPendingJumpState();
            inHyperspace = false;
            jumpFlash.stopTimer();
            syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof CarrierLocationEvent loc) {
            applyKnownCurrentSystem(loc.getStarSystem(), loc.getSystemAddress(), null);
            clearPendingJumpState();
            inHyperspace = false;
            syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
            return new RouteJournalApplyOutcome(false, true);
        }
        if (event instanceof SupercruiseExitEvent sc) {
            if (sc.getStarSystem() != null && !sc.getStarSystem().isBlank()) {
                applyKnownCurrentSystem(sc.getStarSystem(), sc.getSystemAddress(), null);
                clearPendingJumpState();
                inHyperspace = false;
                syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
                return new RouteJournalApplyOutcome(false, true);
            }
            return new RouteJournalApplyOutcome(false, false);
        }
        if (event.getType() == EliteEventType.DOCKED) {
            JsonObject obj = event.getRawJson();
            if (obj != null && obj.has("StarSystem") && !obj.get("StarSystem").isJsonNull()) {
                String dockedSystem = obj.get("StarSystem").getAsString();
                long addr = obj.has("SystemAddress") && !obj.get("SystemAddress").isJsonNull()
                        ? obj.get("SystemAddress").getAsLong()
                        : 0L;
                if (dockedSystem != null && !dockedSystem.isBlank()) {
                    applyKnownCurrentSystem(dockedSystem, addr, null);
                    clearPendingJumpState();
                    inHyperspace = false;
                    syncNoRouteCurrentSystemPlaceholder(getCurrentSystemName(), currentSystemAddress);
                    return new RouteJournalApplyOutcome(false, true);
                }
            }
            return new RouteJournalApplyOutcome(false, false);
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
        pendingJumpDepartureTime = null;
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
        startCarrierPendingJumpBlink(destName, destAddress, null);
    }

    public void startCarrierPendingJumpBlink(String destName, long destAddress, Instant departureTime) {
        pendingJumpLockedName = (destName != null && !destName.isBlank()) ? destName : null;
        pendingJumpLockedAddress = destAddress;
        pendingJumpDepartureTime = departureTime;
        pendingJumpSystemName = pendingJumpLockedName;
        jumpFlash.startTimer();
    }

    public void stopCarrierPendingJumpBlink() {
        jumpFlash.stopTimer();
        pendingJumpSystemName = null;
        pendingJumpLockedName = null;
        pendingJumpLockedAddress = 0L;
        pendingJumpDepartureTime = null;
    }

    /**
     * Off-carrier commanders get {@code CarrierLocation} at {@code DepartureTime} instead of {@code CarrierJump}.
     */
    public boolean isPendingCarrierJumpArrival(CarrierLocationEvent loc) {
        if (loc == null) {
            return false;
        }
        boolean hasPending = (pendingJumpLockedName != null && !pendingJumpLockedName.isBlank())
                || pendingJumpLockedAddress != 0L;
        if (!hasPending) {
            return false;
        }
        Long pendingAddr = pendingJumpLockedAddress != 0L ? Long.valueOf(pendingJumpLockedAddress) : null;
        if (!CarrierJumpCooldown.carrierLocationMatchesPendingJump(
                loc.getStarSystem(), loc.getSystemAddress(), pendingJumpLockedName, pendingAddr)) {
            return false;
        }
        if (pendingJumpDepartureTime != null) {
            return CarrierJumpCooldown.isCarrierLocationJumpArrival(loc.getTimestamp(), pendingJumpDepartureTime);
        }
        return true;
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
                Boolean.valueOf(inHyperspace),
                Integer.valueOf(currentBaseIndex));
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
        if (snap.currentBaseIndex() != null) {
            currentBaseIndex = Math.max(0, snap.currentBaseIndex().intValue());
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
        // Marker assignment and the hollow-triangle renderer only "charge blink" while jumpFlash.isTimerRunning().
        // Live journal events start the timer; after session restore we must restart it or the pending row
        // stays static (especially common after restart with a scheduled carrier jump).
        boolean hasPendingJumpHighlight = (pendingJumpLockedName != null && !pendingJumpLockedName.isBlank())
                || pendingJumpLockedAddress != 0L;
        if (hasPendingJumpHighlight) {
            pendingJumpSystemName = pendingJumpLockedName;
            jumpFlash.startTimer();
        } else {
            pendingJumpSystemName = null;
            jumpFlash.stopTimer();
        }
        clampCurrentBaseIndex();
        // If saved index no longer matches identity (list edited offline), advance from that point.
        if (currentSystemName != null && !currentSystemName.isBlank() && !baseRouteEntries.isEmpty()) {
            if (!RouteGeometry.rowMatchesSystem(
                    baseRouteEntries.get(currentBaseIndex), currentSystemName, currentSystemAddress)) {
                advanceCurrentBaseIndexForArrival(currentSystemName, currentSystemAddress);
            }
        }
    }

    public RouteDisplaySnapshot buildDisplaySnapshot(Consumer<List<RouteEntry>> afterDeepCopyBeforeSynthetics,
            RouteCoordsResolver coordsResolver,
            boolean customRouteActive) {
        displayRevision++;
        List<RouteEntry> rows = RouteLayoutEngine.buildDisplayedEntries(
                baseRouteEntries,
                afterDeepCopyBeforeSynthetics,
                currentSystemName,
                currentSystemAddress,
                currentStarPos,
                currentBaseIndex,
                targetState,
                pendingJumpLockedName,
                pendingJumpLockedAddress,
                coordsResolver,
                customRouteActive,
                jumpFlash.isTimerRunning());
        return new RouteDisplaySnapshot(displayRevision, rows);
    }
}
