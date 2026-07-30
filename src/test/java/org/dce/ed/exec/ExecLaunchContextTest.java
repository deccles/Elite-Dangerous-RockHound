package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExecLaunchContextTest {

    @Test
    void toEnvironment_doesNotIncludeDestinationForCopyTriggers() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION)
                .clipboard("Sol")
                .build();
        Map<String, String> env = context.toEnvironment();
        assertEquals("route_copy_next_destination", env.get("EDO_TRIGGER"));
        assertNull(env.get("EDO_DESTINATION"));
        assertEquals("Sol", env.get("EDO_CLIPBOARD"));
    }

    @Test
    void toEnvironment_doesNotIncludeDestinationForFleetCooldownComplete() {
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                .carrierSystemName("Magellan")
                .build();
        Map<String, String> env = context.toEnvironment();
        assertEquals("fleet_cooldown_complete", env.get("EDO_TRIGGER"));
        assertEquals("Magellan", env.get("EDO_CARRIER_SYSTEM"));
        assertNull(env.get("EDO_DESTINATION"));
        assertNull(env.get("EDO_CLIPBOARD"));
    }

}
