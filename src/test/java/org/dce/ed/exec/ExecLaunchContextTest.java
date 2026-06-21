package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
