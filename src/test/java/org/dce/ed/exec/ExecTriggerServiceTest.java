package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ExecTriggerServiceTest {

    @Test
    void onFleetCooldownComplete_dispatchesImmediatelyWithoutScheduling() {
        ExecTriggerService service = new ExecTriggerService();
        ExecBinding binding = new ExecBinding();
        binding.setJarPath("");
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.FLEET_COOLDOWN_COMPLETE);
        ExecBindingsConfig config = new ExecBindingsConfig();
        config.getBindings().add(binding);
        service.setConfigSupplier(() -> config);

        service.onFleetCooldownComplete();

        assertFalse(service.hasActiveScripts());
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
    void onShipJumpComplete_schedulesEnabledBinding() {
        ExecTriggerService service = new ExecTriggerService();

        ExecBinding binding = new ExecBinding();
        binding.setJarPath("");
        binding.setDelayMs(60_000);
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.SHIP_JUMP_COMPLETE);

        ExecBindingsConfig config = new ExecBindingsConfig();
        config.getBindings().add(binding);
        service.setConfigSupplier(() -> config);

        service.onShipJumpComplete("Alpha Centauri");
        assertTrue(service.hasActiveScripts());
        service.killRunningScripts();
        assertFalse(service.hasActiveScripts());
    }

    @Test
    void onCustomRouteJumpComplete_schedulesEnabledBinding() {
        ExecTriggerService service = new ExecTriggerService();

        ExecBinding binding = new ExecBinding();
        binding.setJarPath("");
        binding.setDelayMs(60_000);
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.CUSTOM_ROUTE_JUMP_COMPLETE);

        ExecBindingsConfig config = new ExecBindingsConfig();
        config.getBindings().add(binding);
        service.setConfigSupplier(() -> config);

        service.onCustomRouteJumpComplete("Alpha Centauri");
        assertTrue(service.hasActiveScripts());
        service.killRunningScripts();
        assertFalse(service.hasActiveScripts());
    }

    @Test
    void configurableTriggers_includeShipJumpAndCustomRouteJumpComplete() {
        boolean ship = false;
        boolean custom = false;
        for (ExecTriggerId id : ExecTriggerId.configurableValues()) {
            if (id == ExecTriggerId.SHIP_JUMP_COMPLETE) {
                ship = true;
            }
            if (id == ExecTriggerId.CUSTOM_ROUTE_JUMP_COMPLETE) {
                custom = true;
            }
        }
        assertTrue(ship);
        assertTrue(custom);
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
