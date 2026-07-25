package org.dce.ed.exec;

/** Journal / overlay events that can launch configured JARs. */
public enum ExecTriggerId {

    /** No automatic trigger — launch via Control Panel or Run now only. */
    NONE("None"),
    /** Launch context when started manually (not shown in Exec tab trigger list). */
    MANUAL("Manual (Run now)"),
    FLEET_COOLDOWN_COMPLETE("Fleet cooldown complete"),
    FLEET_TRITIUM_LOW("Fleet carrier tritium low"),
    ROUTE_COPY_NEXT_DESTINATION("Copy next destination (Route)"),
    FLEET_CARRIER_COPY_NEXT_DESTINATION("Copy next destination (Fleet Carrier)"),
    /** Ship hyperspace arrival ({@code FSDJump}); not fleet carrier jumps. */
    SHIP_JUMP_COMPLETE("Ship jump complete"),
    JOURNAL_EVENT("Journal event"),
    SHORTCUT_KEY("Shortcut key");

    private final String label;

    ExecTriggerId(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Triggers available in the Exec tab dropdown (excludes {@link #MANUAL}). */
    public static ExecTriggerId[] configurableValues() {
        return new ExecTriggerId[] {
                NONE,
                FLEET_COOLDOWN_COMPLETE,
                FLEET_TRITIUM_LOW,
                ROUTE_COPY_NEXT_DESTINATION,
                FLEET_CARRIER_COPY_NEXT_DESTINATION,
                SHIP_JUMP_COMPLETE,
                JOURNAL_EVENT,
                SHORTCUT_KEY
        };
    }
}
