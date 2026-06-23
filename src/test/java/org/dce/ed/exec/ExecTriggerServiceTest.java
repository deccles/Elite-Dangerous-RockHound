package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExecTriggerServiceTest {

    @Test
    void buildFleetCooldownLaunchContext_includesDestinationWhenSupplierReturnsName() {
        ExecTriggerService service = new ExecTriggerService();
        service.setCarrierSystemSupplier(() -> "Magellan");
        service.setFleetCooldownClipboardPrepSupplier(
                () -> FleetCooldownClipboardPrep.copied("Eol Prou LH-K c9-96"));

        ExecLaunchContext context = service.buildFleetCooldownLaunchContext();
        Map<String, String> env = context.toEnvironment();

        assertEquals("fleet_cooldown_complete", env.get("EDO_TRIGGER"));
        assertEquals("Magellan", env.get("EDO_CARRIER_SYSTEM"));
        assertEquals("Eol Prou LH-K c9-96", env.get("EDO_DESTINATION"));
        assertEquals("Eol Prou LH-K c9-96", env.get("EDO_CLIPBOARD"));
        assertNull(env.get("EDO_CLIPBOARD_CLEARED"));
    }

    @Test
    void buildFleetCooldownLaunchContext_signalsClipboardClearedAtEndOfRoute() {
        ExecTriggerService service = new ExecTriggerService();
        service.setCarrierSystemSupplier(() -> "Magellan");
        service.setFleetCooldownClipboardPrepSupplier(FleetCooldownClipboardPrep::cleared);

        ExecLaunchContext context = service.buildFleetCooldownLaunchContext();
        Map<String, String> env = context.toEnvironment();

        assertEquals("Magellan", env.get("EDO_CARRIER_SYSTEM"));
        assertNull(env.get("EDO_DESTINATION"));
        assertNull(env.get("EDO_CLIPBOARD"));
        assertEquals("1", env.get("EDO_CLIPBOARD_CLEARED"));
    }

    @Test
    void buildFleetCooldownLaunchContext_omitsDestinationWhenSupplierUnavailable() {
        ExecTriggerService service = new ExecTriggerService();
        service.setCarrierSystemSupplier(() -> "Magellan");
        service.setFleetCooldownClipboardPrepSupplier(FleetCooldownClipboardPrep::unavailable);

        ExecLaunchContext context = service.buildFleetCooldownLaunchContext();
        Map<String, String> env = context.toEnvironment();

        assertEquals("Magellan", env.get("EDO_CARRIER_SYSTEM"));
        assertNull(env.get("EDO_DESTINATION"));
        assertNull(env.get("EDO_CLIPBOARD"));
        assertNull(env.get("EDO_CLIPBOARD_CLEARED"));
    }
}
