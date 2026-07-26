package org.dce.ed.route;

import java.util.List;

import org.dce.ed.logreader.event.FsdTargetEvent;
import org.dce.ed.logreader.event.StatusEvent;

/**
 * Holds route target and destination state driven by journal events.
 * Encapsulates side-trip clearing logic. Used by {@link RouteSession}; UI and file I/O stay in the tab panel.
 */
public final class RouteTargetState {

    private String targetSystemName = null;
    private long targetSystemAddress = 0L;
    /** Star class from the latest {@link FsdTargetEvent}; used for synthetic side-trip rows / fuel scoop. */
    private String targetStarClass = null;
    private Long destinationSystemAddress = null;
    private Integer destinationBodyId = null;
    private String destinationName = null;

    private boolean clearedSideTrip = false;

    public record RouteSystemRef(String systemName, long systemAddress) {
    }

    public String getTargetSystemName() {
        return targetSystemName;
    }

    public long getTargetSystemAddress() {
        return targetSystemAddress;
    }

    public String getTargetStarClass() {
        return targetStarClass;
    }

    public Long getDestinationSystemAddress() {
        return destinationSystemAddress;
    }

    public Integer getDestinationBodyId() {
        return destinationBodyId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public boolean wasSideTripCleared() {
        return clearedSideTrip;
    }

    public void applyNavRouteClear() {
        targetSystemName = null;
        targetSystemAddress = 0L;
        targetStarClass = null;
        destinationSystemAddress = null;
        destinationBodyId = null;
        destinationName = null;
        clearedSideTrip = false;
    }

    /** Restore target/destination fields from persisted overlay session (not used for NavRoute clear). */
    public void restoreFromPersistence(String targetName, Long targetAddr,
            Long destSystemAddr, Integer destBodyId, String destName) {
        if (targetName != null) {
            targetSystemName = targetName;
        } else {
            targetSystemName = null;
        }
        if (targetAddr != null) {
            targetSystemAddress = targetAddr.longValue();
        } else {
            targetSystemAddress = 0L;
        }
        // Journal FsdTarget will refill this; persistence does not store star class.
        targetStarClass = null;
        destinationSystemAddress = destSystemAddr;
        destinationBodyId = destBodyId;
        destinationName = destName;
        clearedSideTrip = false;
    }

    /**
     * Clears the plotted FSD target when the journal confirms we are already in that system.
     * Otherwise {@code hasSideTripTarget} stays true, {@link RouteMarkerAssignment} skips the
     * main-route next-hop marker, and the target row is skipped too because it is already CURRENT.
     */
    public void clearTargetIfMatchesArrival(String arrivalName, long arrivalAddress) {
        if (targetSystemName == null || targetSystemName.isBlank()) {
            return;
        }
        boolean matchByAddr = arrivalAddress != 0L && targetSystemAddress != 0L
                && targetSystemAddress == arrivalAddress;
        boolean matchByName = arrivalName != null && arrivalName.equals(targetSystemName);
        if (matchByAddr || matchByName) {
            targetSystemName = null;
            targetSystemAddress = 0L;
            targetStarClass = null;
        }
    }

    public void applyFsdTargetEvent(FsdTargetEvent e, boolean inHyperspace, boolean timerRunning) {
        clearedSideTrip = false;
        if (inHyperspace || timerRunning) {
            return;
        }
        String newName = e != null ? e.getName() : null;
        long newAddr = e != null ? e.getSystemAddress() : 0L;
        if (newName == null || newName.isBlank() || newAddr == 0L) {
            targetSystemName = null;
            targetSystemAddress = 0L;
            targetStarClass = null;
        } else {
            targetSystemName = newName;
            targetSystemAddress = newAddr;
            String sc = e.getStarClass();
            targetStarClass = (sc != null && !sc.isBlank()) ? sc.trim() : null;
        }
    }

    /**
     * @return true if the target was cleared (caller may rebuild and return early without firing session)
     */
    public boolean applyStatusEvent(StatusEvent e, List<RouteSystemRef> baseRoute) {
        clearedSideTrip = false;
        if (e == null) {
            return false;
        }
        destinationSystemAddress = e.getDestinationSystem();
        Integer body = e.getDestinationBody();
        destinationBodyId = (body != null && body.intValue() != 0) ? body : null;
        destinationName = e.getDestinationDisplayName();

        String statusDestName = destinationName;
        if (statusDestName == null || statusDestName.isBlank()) {
            if (targetSystemName != null) {
                targetSystemName = null;
                targetSystemAddress = 0L;
                targetStarClass = null;
                clearedSideTrip = true;
            }
            return clearedSideTrip;
        }

        boolean statusDestIsOnRoute = false;
        boolean targetIsOnRoute = false;
        if (baseRoute != null && !baseRoute.isEmpty()) {
            for (RouteSystemRef ref : baseRoute) {
                if (ref == null) {
                    continue;
                }
                if (statusDestName.equals(ref.systemName())) {
                    statusDestIsOnRoute = true;
                }
                if (targetSystemName != null && !targetSystemName.isBlank()) {
                    if (targetSystemName.equals(ref.systemName())) {
                        targetIsOnRoute = true;
                    }
                }
                if (targetSystemAddress != 0L && ref.systemAddress() != 0L) {
                    if (ref.systemAddress() == targetSystemAddress) {
                        targetIsOnRoute = true;
                    }
                }
                if (statusDestIsOnRoute && targetIsOnRoute) {
                    break;
                }
            }
        }

        if (statusDestIsOnRoute && targetSystemName != null && !targetIsOnRoute) {
            targetSystemName = null;
            targetSystemAddress = 0L;
            targetStarClass = null;
            clearedSideTrip = true;
        }
        return clearedSideTrip;
    }
}
