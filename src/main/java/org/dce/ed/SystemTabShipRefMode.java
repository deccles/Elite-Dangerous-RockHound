package org.dce.ed;

/**
 * How the System tab picks the commander reference body for ship-centric distances, plan-map “You” highlight,
 * and ship position on the plan map (see {@link org.dce.ed.SystemTabPanel}).
 */
public enum SystemTabShipRefMode {

    /**
     * Proximity-driven anchor: {@code ApproachBody} / Status near-body / supercruise drop / sticky last visit,
     * with fleet-carrier parked body overriding when docked on a carrier (see panel implementation).
     */
    APPROACH_BODY,

    /**
     * Prefer the HUD navigation body target when set; when cleared, keep the last targeted body until a new target
     * is chosen. Active {@code ApproachBody} always wins; fleet-carrier parked body overrides when docked on a carrier.
     */
    TARGETED_BODY;

    public String displayName() {
        return this == APPROACH_BODY ? "Approach body" : "HUD target (sticky)";
    }

    public static SystemTabShipRefMode fromPrefsString(String s) {
        if (s != null && "targeted".equalsIgnoreCase(s.trim())) {
            return TARGETED_BODY;
        }
        return APPROACH_BODY;
    }

    public String toPrefsString() {
        return this == TARGETED_BODY ? "targeted" : "approach";
    }
}
