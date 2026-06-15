package org.dce.ed.exec;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.logreader.CarrierJumpCooldown;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;

import com.google.gson.JsonObject;

/**
 * Dispatches configured JAR launches for {@link ExecTriggerId} events.
 */
public final class ExecTriggerService {

    private final ExecBindingsStore store;
    private final CarrierFuelTracker fuelTracker = new CarrierFuelTracker();
    private volatile Supplier<ExecBindingsConfig> configSupplier;
    private volatile Consumer<String> statusListener;
    private volatile Supplier<String> carrierSystemSupplier;

    public ExecTriggerService() {
        this(new ExecBindingsStore());
    }

    ExecTriggerService(ExecBindingsStore store) {
        this.store = store;
    }

    public void setConfigSupplier(Supplier<ExecBindingsConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void setCarrierSystemSupplier(Supplier<String> carrierSystemSupplier) {
        this.carrierSystemSupplier = carrierSystemSupplier;
    }

    public CarrierFuelTracker fuelTracker() {
        return fuelTracker;
    }

    public ExecBindingsStore store() {
        return store;
    }

    public void onFleetCooldownComplete() {
        int delayMs = CarrierJumpCooldown.EXEC_TRIGGER_DELAY_AFTER_COOLDOWN_SECONDS * 1000;
        publishStatus("Cooldown ended — running macro in "
                + CarrierJumpCooldown.EXEC_TRIGGER_DELAY_AFTER_COOLDOWN_SECONDS + "s…");
        Timer timer = new Timer(delayMs, e -> fireTrigger(
                ExecTriggerId.FLEET_COOLDOWN_COMPLETE,
                ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                        .carrierSystemName(carrierSystemName())
                        .build()));
        timer.setRepeats(false);
        timer.start();
    }

    public void onJournalEvent(EliteLogEvent event, OwnedFleetCarrierTracker ownedTracker) {
        if (event == null || event.getType() != EliteEventType.CARRIER_STATS) {
            return;
        }
        JsonObject raw = event.getRawJson();
        if (raw == null) {
            return;
        }
        ExecBindingsConfig config = currentConfig();
        long ownedId = ownedTracker != null ? ownedTracker.getOwnedCarrierId() : 0L;
        boolean crossedLow = fuelTracker.updateFromCarrierStats(
                raw,
                ownedId,
                config.getFleetTritiumLowThreshold(),
                config.getFleetTritiumLowHysteresis());
        if (!crossedLow) {
            return;
        }
        fireTrigger(ExecTriggerId.FLEET_TRITIUM_LOW, ExecLaunchContext.builder(ExecTriggerId.FLEET_TRITIUM_LOW)
                .carrierSystemName(carrierSystemName())
                .carrierFuelLevel(fuelTracker.getLastKnownFuelLevel())
                .carrierFuelThreshold(config.getFleetTritiumLowThreshold())
                .carrierCallsign(CarrierFuelTracker.callsignFromJson(raw))
                .carrierName(CarrierFuelTracker.carrierNameFromJson(raw))
                .build());
    }

    public void runBindingNow(ExecBinding binding) {
        if (binding == null) {
            publishStatus("Select a row to run.");
            return;
        }
        ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.MANUAL)
                .carrierSystemName(carrierSystemName())
                .carrierFuelLevel(fuelTracker.getLastKnownFuelLevel() >= 0
                        ? Integer.valueOf(fuelTracker.getLastKnownFuelLevel()) : null)
                .build();
        scheduleAndRun(binding, context);
    }

    private void fireTrigger(ExecTriggerId triggerId, ExecLaunchContext baseContext) {
        ExecBindingsConfig config = currentConfig();
        for (ExecBinding binding : config.getBindings()) {
            if (binding == null || !binding.isEnabled() || binding.getTrigger() != triggerId) {
                continue;
            }
            ExecLaunchContext context = mergeContext(baseContext, binding);
            scheduleAndRun(binding, context);
        }
    }

    private ExecLaunchContext mergeContext(ExecLaunchContext base, ExecBinding binding) {
        return ExecLaunchContext.builder(base.getTrigger())
                .delayMs(binding.getDelayMs())
                .firedAt(base.getFiredAt())
                .carrierSystemName(base.getCarrierSystemName())
                .carrierFuelLevel(base.getCarrierFuelLevel())
                .carrierFuelThreshold(base.getCarrierFuelThreshold())
                .carrierCallsign(base.getCarrierCallsign())
                .carrierName(base.getCarrierName())
                .build();
    }

    private void scheduleAndRun(ExecBinding binding, ExecLaunchContext context) {
        int delay = binding.getDelayMs();
        if (delay <= 0) {
            launch(binding, context);
            return;
        }
        publishStatus("Scheduled " + binding.getTrigger().getLabel() + " in " + (delay / 1000) + "s…");
        Timer timer = new Timer(delay, e -> launch(binding, context));
        timer.setRepeats(false);
        timer.start();
    }

    private void launch(ExecBinding binding, ExecLaunchContext context) {
        publishStatus("Running " + shortJarName(binding.getJarPath()) + "…");
        JarExecRunner.runAsync(binding, context, result -> SwingUtilities.invokeLater(() -> {
            if (result.exitCode() == 0) {
                publishStatus("OK: " + result.detail());
            } else {
                publishStatus("Failed: " + result.detail());
            }
        }));
    }

    private ExecBindingsConfig currentConfig() {
        Supplier<ExecBindingsConfig> supplier = configSupplier;
        if (supplier != null) {
            ExecBindingsConfig config = supplier.get();
            if (config != null) {
                return config;
            }
        }
        return store.load();
    }

    private String carrierSystemName() {
        Supplier<String> supplier = carrierSystemSupplier;
        if (supplier == null) {
            return null;
        }
        String name = supplier.get();
        return name != null && !name.isBlank() ? name.trim() : null;
    }

    private void publishStatus(String message) {
        Consumer<String> listener = statusListener;
        if (listener != null && message != null) {
            listener.accept(message);
        }
    }

    private static String shortJarName(String jarPath) {
        if (jarPath == null || jarPath.isBlank()) {
            return "(no JAR)";
        }
        int slash = Math.max(jarPath.lastIndexOf('/'), jarPath.lastIndexOf('\\'));
        return slash >= 0 ? jarPath.substring(slash + 1) : jarPath;
    }
}
