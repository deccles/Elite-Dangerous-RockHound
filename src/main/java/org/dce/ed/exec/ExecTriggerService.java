package org.dce.ed.exec;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.logreader.CarrierJumpCooldown;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.OwnedFleetCarrierTracker;

import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;

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
    /** Fleet Carrier tab: copy next route hop (or clear clipboard at end of route) before cooldown exec. */
    private volatile Supplier<FleetCooldownClipboardPrep> fleetCooldownClipboardPrepSupplier;
    private volatile ExecPlaceholderContext placeholderContext;

    private final CopyOnWriteArrayList<Timer> scheduledExecTimers = new CopyOnWriteArrayList<>();
    private volatile Runnable activityListener;

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

    /** Fired on EDT when a script starts, stops, or a delayed launch is scheduled/cancelled. */
    public void setActivityListener(Runnable activityListener) {
        this.activityListener = activityListener;
    }

    /** True when an exec child process is running or a delayed launch timer is active. */
    public boolean hasActiveScripts() {
        if (JarExecRunner.runningProcessCount() > 0) {
            return true;
        }
        for (Timer timer : scheduledExecTimers) {
            if (timer != null && timer.isRunning()) {
                return true;
            }
        }
        return false;
    }

    public void setCarrierSystemSupplier(Supplier<String> carrierSystemSupplier) {
        this.carrierSystemSupplier = carrierSystemSupplier;
    }

    public void setFleetCooldownClipboardPrepSupplier(Supplier<FleetCooldownClipboardPrep> fleetCooldownClipboardPrepSupplier) {
        this.fleetCooldownClipboardPrepSupplier = fleetCooldownClipboardPrepSupplier;
    }

    public void setPlaceholderContext(ExecPlaceholderContext placeholderContext) {
        this.placeholderContext = placeholderContext;
    }

    public ExecPlaceholderContext placeholderContext() {
        return placeholderContext;
    }

    /** Live placeholder values for UI tooltips (no launch context). */
    public Map<String, String> resolvePlaceholdersForUi() {
        return ExecPlaceholderResolver.resolveAll(placeholderContext, null);
    }

    public CarrierFuelTracker fuelTracker() {
        return fuelTracker;
    }

    /** Load last {@code CarrierStats.FuelLevel} from recent journals (owned carrier when known). */
    public void bootstrapFuelFromJournal(OwnedFleetCarrierTracker ownedTracker) {
        CarrierFuelJournalBootstrap.replayInto(fuelTracker, ownedTracker);
    }

    public ExecBindingsStore store() {
        return store;
    }

    public void onCopyNextDestination(ExecTriggerId triggerId, String destination) {
        if (triggerId == null || destination == null || destination.isBlank()) {
            return;
        }
        if (triggerId != ExecTriggerId.ROUTE_COPY_NEXT_DESTINATION
                && triggerId != ExecTriggerId.FLEET_CARRIER_COPY_NEXT_DESTINATION) {
            return;
        }
        String trimmed = destination.trim();
        fireTrigger(triggerId, ExecLaunchContext.builder(triggerId)
                .destination(trimmed)
                .clipboard(trimmed)
                .build());
    }

    /** Fires enabled bindings whose shortcut key matches (global F-key hook). */
    public void onShortcutKeyPressed(int keyCode) {
        if (!ExecShortcutKeys.isSupported(keyCode)) {
            return;
        }
        ExecBindingsConfig config = currentConfig();
        for (ExecBinding binding : config.getBindings()) {
            if (binding == null || !binding.isEnabled() || binding.getTrigger() != ExecTriggerId.SHORTCUT_KEY) {
                continue;
            }
            if (binding.getShortcutKeyCode() != keyCode) {
                continue;
            }
            ExecLaunchContext context = ExecLaunchContext.builder(ExecTriggerId.SHORTCUT_KEY)
                    .carrierSystemName(carrierSystemName())
                    .carrierFuelLevel(fuelTracker.getLastKnownFuelLevel() >= 0
                            ? Integer.valueOf(fuelTracker.getLastKnownFuelLevel()) : null)
                    .carrierFuelThreshold(config.getFleetTritiumLowThreshold())
                    .carrierCallsign(fuelTracker.getLastKnownCallsign())
                    .carrierName(fuelTracker.getLastKnownCarrierName())
                    .build();
            scheduleAndRun(binding, mergeContext(context, binding));
        }
    }

    public void onFleetCooldownComplete() {
        int delayMs = CarrierJumpCooldown.EXEC_TRIGGER_DELAY_AFTER_COOLDOWN_SECONDS * 1000;
        publishStatus("Cooldown ended — running macro in "
                + CarrierJumpCooldown.EXEC_TRIGGER_DELAY_AFTER_COOLDOWN_SECONDS + "s…");
        Timer timer = new Timer(delayMs, e -> fireTrigger(
                ExecTriggerId.FLEET_COOLDOWN_COMPLETE,
                buildFleetCooldownLaunchContext()));
        timer.setRepeats(false);
        trackExecTimer(timer);
        timer.start();
        notifyActivityChanged();
    }

    ExecLaunchContext buildFleetCooldownLaunchContext() {
        FleetCooldownClipboardPrep prep = resolveFleetCooldownClipboardPrep();
        ExecLaunchContext.Builder builder = ExecLaunchContext.builder(ExecTriggerId.FLEET_COOLDOWN_COMPLETE)
                .carrierSystemName(carrierSystemName())
                .carrierCallsign(fuelTracker.getLastKnownCallsign())
                .carrierName(fuelTracker.getLastKnownCarrierName());
        if (prep != null && prep.destination() != null && !prep.destination().isBlank()) {
            String destination = prep.destination().trim();
            builder.destination(destination).clipboard(destination);
        } else if (prep != null && prep.clipboardCleared()) {
            builder.clipboardCleared(true);
        }
        return builder.build();
    }

    private FleetCooldownClipboardPrep resolveFleetCooldownClipboardPrep() {
        Supplier<FleetCooldownClipboardPrep> supplier = fleetCooldownClipboardPrepSupplier;
        if (supplier == null) {
            return null;
        }
        try {
            FleetCooldownClipboardPrep prep = supplier.get();
            return prep != null ? prep : FleetCooldownClipboardPrep.unavailable();
        } catch (Exception ignored) {
            return FleetCooldownClipboardPrep.unavailable();
        }
    }

    public void onJournalEvent(EliteLogEvent event, OwnedFleetCarrierTracker ownedTracker) {
        if (event == null) {
            return;
        }
        if (event.getType() == EliteEventType.CARRIER_STATS) {
            handleCarrierStatsTritium(event, ownedTracker);
        }
        dispatchJournalEventBindings(event);
    }

    private void handleCarrierStatsTritium(EliteLogEvent event, OwnedFleetCarrierTracker ownedTracker) {
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

    private void dispatchJournalEventBindings(EliteLogEvent event) {
        EliteEventType type = event.getType();
        if (type == null || type == EliteEventType.UNKNOWN) {
            return;
        }
        ExecBindingsConfig config = currentConfig();
        for (ExecBinding binding : config.getBindings()) {
            if (binding == null || !binding.isEnabled() || binding.getTrigger() != ExecTriggerId.JOURNAL_EVENT) {
                continue;
            }
            if (!binding.matchesJournalEvent(type)) {
                continue;
            }
            JsonObject raw = event.getRawJson();
            ExecLaunchContext launchContext = buildJournalEventLaunchContext(event);
            Map<String, String> placeholders = ExecPlaceholderResolver.resolveAll(placeholderContext, launchContext);
            if (raw != null && !binding.matchesJournalAttributes(raw, placeholders)) {
                continue;
            }
            ExecLaunchContext context = mergeContext(launchContext, binding);
            scheduleAndRun(binding, context);
        }
    }

    private ExecLaunchContext buildJournalEventLaunchContext(EliteLogEvent event) {
        ExecLaunchContext.Builder builder = ExecLaunchContext.builder(ExecTriggerId.JOURNAL_EVENT)
                .carrierSystemName(carrierSystemName())
                .journalEventType(event.getType())
                .carrierCallsign(fuelTracker.getLastKnownCallsign())
                .carrierName(fuelTracker.getLastKnownCarrierName());
        if (event.getType() == EliteEventType.CARRIER_STATS) {
            JsonObject raw = event.getRawJson();
            int fuel = CarrierFuelTracker.fuelLevelFromJson(raw);
            if (fuel >= 0) {
                builder.carrierFuelLevel(fuel);
            }
            String callsign = CarrierFuelTracker.callsignFromJson(raw);
            if (callsign != null && !callsign.isBlank()) {
                builder.carrierCallsign(callsign);
            }
            String name = CarrierFuelTracker.carrierNameFromJson(raw);
            if (name != null && !name.isBlank()) {
                builder.carrierName(name);
            }
        }
        return builder.build();
    }

    public void runBindingNow(ExecBinding binding) {
        if (binding == null) {
            publishStatus("Select a row to run.");
            return;
        }
        runBindingNowInternal(binding);
    }

    /** Run a configured binding by persisted id (Control Panel buttons). */
    public void runBindingNowById(String bindingId) {
        if (bindingId == null || bindingId.isBlank()) {
            publishStatus("Action not configured.");
            return;
        }
        ExecBindingsConfig config = currentConfig();
        for (ExecBinding binding : config.getBindings()) {
            if (binding != null && bindingId.equals(binding.getId())) {
                runBindingNowInternal(binding);
                return;
            }
        }
        publishStatus("Action no longer configured.");
    }

    /**
     * Stops rogue exec programs: cancels pending delayed launches and kills running child processes.
     *
     * @return user-facing status for the overlay bar
     */
    public String killRunningScripts() {
        int cancelledTimers = cancelPendingExecTimers();
        int killedProcesses = JarExecRunner.killRunningProcesses();
        if (cancelledTimers == 0 && killedProcesses == 0) {
            return "No scripts running.";
        }
        StringBuilder sb = new StringBuilder("Stopped ");
        if (killedProcesses > 0) {
            sb.append(killedProcesses).append(killedProcesses == 1 ? " script" : " scripts");
        }
        if (cancelledTimers > 0) {
            if (killedProcesses > 0) {
                sb.append(" and cancelled ");
            } else {
                sb.append("cancelled ");
            }
            sb.append(cancelledTimers).append(cancelledTimers == 1 ? " scheduled launch" : " scheduled launches");
        }
        sb.append('.');
        String message = sb.toString();
        publishStatus(message);
        notifyActivityChanged();
        return message;
    }

    private void runBindingNowInternal(ExecBinding binding) {
        ExecBindingsConfig config = currentConfig();
        FleetCooldownClipboardPrep prep = resolveFleetCooldownClipboardPrep();
        ExecLaunchContext.Builder builder = ExecLaunchContext.builder(ExecTriggerId.MANUAL)
                .carrierSystemName(carrierSystemName())
                .carrierFuelLevel(fuelTracker.getLastKnownFuelLevel() >= 0
                        ? Integer.valueOf(fuelTracker.getLastKnownFuelLevel()) : null)
                .carrierFuelThreshold(config.getFleetTritiumLowThreshold())
                .carrierCallsign(fuelTracker.getLastKnownCallsign())
                .carrierName(fuelTracker.getLastKnownCarrierName());
        if (prep != null && prep.destination() != null && !prep.destination().isBlank()) {
            String destination = prep.destination().trim();
            builder.destination(destination).clipboard(destination);
        } else if (prep != null && prep.clipboardCleared()) {
            builder.clipboardCleared(true);
        }
        scheduleAndRun(binding, builder.build());
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
                .destination(base.getDestination())
                .clipboard(base.getClipboard())
                .clipboardCleared(base.isClipboardCleared())
                .journalEventType(base.getJournalEventType())
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
        trackExecTimer(timer);
        timer.start();
        notifyActivityChanged();
    }

    private void trackExecTimer(Timer timer) {
        scheduledExecTimers.add(timer);
        timer.addActionListener(e -> {
            scheduledExecTimers.remove(timer);
            notifyActivityChanged();
        });
        notifyActivityChanged();
    }

    private int cancelPendingExecTimers() {
        int cancelled = 0;
        for (Timer timer : scheduledExecTimers) {
            if (timer != null && timer.isRunning()) {
                timer.stop();
                cancelled++;
            }
        }
        scheduledExecTimers.clear();
        return cancelled;
    }

    private void launch(ExecBinding binding, ExecLaunchContext context) {
        notifyActivityChanged();
        publishStatus("Running " + shortProgramName(binding.getJarPath()) + "…");
        JarExecRunner.runAsync(binding, context, placeholderContext, result -> SwingUtilities.invokeLater(() -> {
            if (result.exitCode() == 0) {
                publishStatus("OK");
            } else if (JarExecRunner.isUserCancelled(result)) {
                publishStatus("");
            } else {
                publishStatus("Failed: " + JarExecRunner.formatConciseStatus(result));
            }
            notifyActivityChanged();
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

    private void notifyActivityChanged() {
        Runnable listener = activityListener;
        if (listener == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            listener.run();
        } else {
            SwingUtilities.invokeLater(listener);
        }
    }

    private static String shortProgramName(String programPath) {
        if (programPath == null || programPath.isBlank()) {
            return "(no program)";
        }
        int slash = Math.max(programPath.lastIndexOf('/'), programPath.lastIndexOf('\\'));
        return slash >= 0 ? programPath.substring(slash + 1) : programPath;
    }
}
