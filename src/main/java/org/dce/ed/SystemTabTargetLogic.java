package org.dce.ed;

import org.dce.ed.logreader.event.StatusEvent;

/**
 * Pure logic for System tab target/destination from Status events.
 * Extracted so "destination is other system" and effective dest name/body can be unit tested.
 */
public final class SystemTabTargetLogic {

    private SystemTabTargetLogic() {}

    /**
     * True when the Status destination refers to a different system (e.g. next jump target),
     * so we should not highlight any body in the current system.
     */
    public static boolean isDestinationOtherSystem(StatusEvent e, long currentSystemAddress) {
        if (e == null) {
            return false;
        }
        Long destSystem = e.getDestinationSystem();
        return destSystem != null
                && currentSystemAddress != 0L
                && destSystem.longValue() != currentSystemAddress;
    }

    /**
     * Destination body for the current system, or null if destination is another system or body id is 0.
     */
    public static Integer effectiveDestBody(StatusEvent e, long currentSystemAddress) {
        if (e == null || isDestinationOtherSystem(e, currentSystemAddress)) {
            return null;
        }
        Integer b = e.getDestinationBody();
        return (b != null && b.intValue() != 0) ? b : null;
    }

    /**
     * Destination display name when in current system, or null if destination is another system or blank.
     */
    public static String effectiveDestName(StatusEvent e, long currentSystemAddress) {
        if (e == null || isDestinationOtherSystem(e, currentSystemAddress)) {
            return null;
        }
        String s = e.getDestinationDisplayName();
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    /**
     * For FSD charge / {@code StartJump}: prefer the System tab when the travel target is a specific
     * body or station (non-zero {@code Destination.Body} in Status). Prefer the Route tab when the
     * target is the system only (body id absent or zero).
     * <p>
     * When {@code jumpTargetSystemAddress} is set (journal {@code StartJump}), the destination system
     * in Status must match it; otherwise a mismatched snapshot is treated as system-only (Route tab).
     */
    public static boolean preferSystemTabForFsdTarget(StatusEvent e, Long jumpTargetSystemAddress,
            long currentSystemAddress) {
        if (e == null) {
            return false;
        }
        Integer destBody = e.getDestinationBody();
        if (destBody == null || destBody.intValue() == 0) {
            return false;
        }
        Long destSys = e.getDestinationSystem();
        if (destSys == null) {
            return false;
        }
        if (jumpTargetSystemAddress != null && jumpTargetSystemAddress.longValue() != 0L) {
            return destSys.longValue() == jumpTargetSystemAddress.longValue();
        }
        return currentSystemAddress != 0L;
    }
}
