package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void killRunningScripts_cancelsScheduledLaunchWithoutStartingProcess() throws Exception {
        ExecTriggerService service = new ExecTriggerService();
        AtomicReference<String> status = new AtomicReference<>();
        service.setStatusListener(status::set);

        ExecBinding binding = new ExecBinding();
        binding.setJarPath("");
        binding.setDelayMs(60_000);
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION);

        ExecBindingsConfig config = new ExecBindingsConfig();
        config.getBindings().add(binding);
        service.setConfigSupplier(() -> config);

        service.onCopyNextDestination(ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION, "Sol");

        String message = service.killRunningScripts();
        assertTrue(message.contains("scheduled"), message);
        assertEquals(0, JarExecRunner.runningProcessCount());
    }

    @Test
    void runBindingNow_skipsDelayAndDoesNotSchedule() {
        ExecTriggerService service = new ExecTriggerService();

        ExecBinding binding = new ExecBinding();
        binding.setJarPath("");
        binding.setDelayMs(60_000);
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.NONE);

        service.runBindingNow(binding);

        assertFalse(service.hasActiveScripts());
        assertEquals(0, JarExecRunner.runningProcessCount());
    }

    @Test
    void runningLabel_prefersBindingNameOverJarFile() {
        ExecBinding binding = new ExecBinding();
        binding.setName("Supercruise Autoland");
        binding.setProgramName("RoboHound");
        binding.setJarPath("C:\\apps\\RoboHound.jar");
        binding.setProgramArgs("--play supercruise-to-landing-v2");
        assertEquals("Supercruise Autoland", ExecTriggerService.runningLabel(binding));
    }

    @Test
    void runningLabel_fallsBackToPlayScriptWhenBindingNameBlank() {
        ExecBinding binding = new ExecBinding();
        binding.setName("");
        binding.setProgramName("RoboHound");
        binding.setJarPath("C:\\apps\\RoboHound.jar");
        binding.setProgramArgs("--play fleet-carrier-arrival $CARRIER_NAME");
        assertEquals("fleet-carrier-arrival", ExecTriggerService.runningLabel(binding));
        assertEquals("fleet-carrier-arrival", ExecTriggerService.playScriptName(binding.getProgramArgs()));
    }
}
