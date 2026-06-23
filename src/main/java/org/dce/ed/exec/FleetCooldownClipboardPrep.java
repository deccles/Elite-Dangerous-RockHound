package org.dce.ed.exec;

/** Outcome of preparing the clipboard before a {@link ExecTriggerId#FLEET_COOLDOWN_COMPLETE} launch. */
public record FleetCooldownClipboardPrep(String destination, boolean clipboardCleared) {

    public static FleetCooldownClipboardPrep copied(String destination) {
        return new FleetCooldownClipboardPrep(destination, false);
    }

    public static FleetCooldownClipboardPrep cleared() {
        return new FleetCooldownClipboardPrep(null, true);
    }

    public static FleetCooldownClipboardPrep unavailable() {
        return new FleetCooldownClipboardPrep(null, false);
    }
}
