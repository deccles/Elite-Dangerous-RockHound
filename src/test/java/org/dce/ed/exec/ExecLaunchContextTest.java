package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExecLaunchContextTest {

    @Test
    void toEnvironment_includesDestinationForCopyTriggers() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION)
                .destination("Sol")
                .clipboard("Sol")
                .build();
        Map<String, String> env = context.toEnvironment();
        assertEquals("route_copy_next_destination", env.get("EDO_TRIGGER"));
        assertEquals("Sol", env.get("EDO_DESTINATION"));
        assertEquals("Sol", env.get("EDO_CLIPBOARD"));
    }

    @Test
    void toEnvironment_includesDestinationForFleetCooldownComplete() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                .destination("Sol")
                .clipboard("Sol")
                .carrierSystemName("Magellan")
                .build();
        Map<String, String> env = context.toEnvironment();
        assertEquals("fleet_cooldown_complete", env.get("EDO_TRIGGER"));
        assertEquals("Magellan", env.get("EDO_CARRIER_SYSTEM"));
        assertEquals("Sol", env.get("EDO_DESTINATION"));
        assertEquals("Sol", env.get("EDO_CLIPBOARD"));
    }

    @Test
    void toEnvironment_includesClipboardClearedFlag() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                .carrierSystemName("Magellan")
                .clipboardCleared(true)
                .build();
        Map<String, String> env = context.toEnvironment();
        assertEquals("1", env.get("EDO_CLIPBOARD_CLEARED"));
        assertNull(env.get("EDO_DESTINATION"));
        assertNull(env.get("EDO_CLIPBOARD"));
    }
}
