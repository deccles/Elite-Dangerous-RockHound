package org.dce.ed.engineering;

import org.dce.ed.logreader.event.ModuleRetrieveEvent;

/**
 * Retrieve/store patch the live loadout immediately. Missing engineering is treated as stock.
 * The "waiting for Loadout" banner is disabled.
 */
public final class EngineeringLoadoutFreshness {

    public static final String WAIT_MESSAGE =
            "Waiting for Loadout. Missing engineering is treated as stock until you leave stored modules.";
    public static final String WAIT_TOOLTIP =
            "Elite only writes a full ship Loadout when you leave stored modules, leave Outfitting, "
                    + "switch ships, or reload. A retrieve without grade is applied as stock for now; "
                    + "the next Loadout confirms it.";

    private EngineeringLoadoutFreshness() {
    }

    public static boolean isAwaitingLoadout() {
        return false;
    }

    public static void onModuleRetrieve(ModuleRetrieveEvent retrieve) {
        // Banner disabled; retrieve still patches the live loadout.
    }

    public static void onModuleStore() {
        // Banner disabled.
    }

    public static void markAwaitingLoadout() {
        // Banner disabled.
    }

    public static void clear() {
        // Banner disabled.
    }

    /** Test helper. */
    static void resetForTests() {
        // Banner disabled.
    }
}
