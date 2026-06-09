package org.dce.ed.logreader;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.swing.SwingUtilities;

import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.SystemTabPanel;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.state.SystemEventProcessor;
import org.dce.ed.state.SystemState;

/**
 * Replays journal history for one system through {@link SystemEventProcessor} and replaces that
 * system's SQLite cache row. Uses the same parser and event handlers as live play and bulk rescan.
 */
public final class RescanCurrentSystemFromJournal {

    public record TargetSystem(String systemName, long systemAddress) {
    }

    public record RescanResult(TargetSystem target, int eventsReplayed, int bodiesStored) {
    }

    private RescanCurrentSystemFromJournal() {
    }

    /**
     * Prefer the system shown on an open {@link SystemTabPanel}; fall back to the cache session header.
     */
    public static TargetSystem resolveTargetSystem() {
        for (Window window : Window.getWindows()) {
            if (window == null || !window.isDisplayable()) {
                continue;
            }
            if (!(window instanceof Container root)) {
                continue;
            }
            TargetSystem fromTab = targetFromContainer(root);
            if (fromTab != null) {
                return fromTab;
            }
        }
        try {
            CachedSystem last = SystemCache.load();
            if (last != null && last.systemName != null && !last.systemName.isBlank() && last.systemAddress != 0L) {
                return new TargetSystem(last.systemName, last.systemAddress);
            }
        } catch (IOException ignored) {
            // fall through
        }
        return null;
    }

    public static RescanResult rescanDisplayedSystem(RescanJournalsMain.RescanProgressListener progress)
            throws IOException {
        TargetSystem target = resolveTargetSystem();
        if (target == null) {
            throw new IOException("No current system is loaded in the overlay.");
        }
        return rescanSystem(target.systemName(), target.systemAddress(), progress);
    }

    public static RescanResult rescanSystem(String systemName, long systemAddress,
            RescanJournalsMain.RescanProgressListener progress) throws IOException {
        if (systemName == null || systemName.isBlank()) {
            throw new IOException("System name is required.");
        }
        if (systemAddress == 0L) {
            throw new IOException("System address is required for a targeted journal rescan.");
        }

        Path journalDirectory = OverlayPreferences.resolveJournalDirectory(EliteDangerousOverlay.clientKey);
        if (journalDirectory == null || !java.nio.file.Files.isDirectory(journalDirectory)) {
            throw new IOException("Journal directory not found.");
        }

        reportProgress(progress, "Reading journals", -1, null);
        EliteJournalReader reader = new EliteJournalReader(journalDirectory);
        List<EliteLogEvent> allEvents = reader.readAllEvents();
        List<EliteLogEvent> replayEvents = JournalSystemRescanFilter.filterForSystemRescan(
                allEvents, systemName, systemAddress);
        if (replayEvents.isEmpty()) {
            throw new IOException("No journal events found for system: " + systemName);
        }

        reportProgress(progress, "Replaying journal", 0,
                replayEvents.size() + " event" + (replayEvents.size() == 1 ? "" : "s"));

        SystemCache cache = SystemCache.getInstance();
        CachedSystem existing = cache.get(systemAddress, systemName);

        SystemState state = new SystemState();
        state.setSystemName(systemName);
        state.setSystemAddress(systemAddress);
        if (existing != null && existing.starPos != null) {
            state.setStarPos(existing.starPos);
        }
        seedStarPosFromTransitions(replayEvents, state);

        SystemEventProcessor processor = new SystemEventProcessor(EliteDangerousOverlay.clientKey, state, null);

        String prevBulk = System.getProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY);
        String prevReplace = System.getProperty(SystemCache.CACHE_JOURNAL_REPLACE_SYSTEM_WRITE_PROPERTY);
        try {
            System.setProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY, "true");
            System.setProperty(SystemCache.CACHE_JOURNAL_REPLACE_SYSTEM_WRITE_PROPERTY, "true");
            int index = 0;
            for (EliteLogEvent event : replayEvents) {
                processor.handleEvent(event);
                index++;
                if (index == replayEvents.size() || index % 500 == 0) {
                    int pct = (int) Math.round(index * 100.0 / replayEvents.size());
                    reportProgress(progress, "Replaying journal", pct,
                            index + " / " + replayEvents.size());
                }
            }
            cache.storeSystem(state);
        } finally {
            restoreProperty(SystemCache.CACHE_BULK_SYSTEM_WRITE_PROPERTY, prevBulk);
            restoreProperty(SystemCache.CACHE_JOURNAL_REPLACE_SYSTEM_WRITE_PROPERTY, prevReplace);
        }

        int bodies = state.getBodies() != null ? state.getBodies().size() : 0;
        reportProgress(progress, "Done", 100, bodies + " bod" + (bodies == 1 ? "y" : "ies"));
        System.out.printf(Locale.US,
                "[EDO] RescanCurrentSystemFromJournal: %s — replayed %d events, stored %d bodies%n",
                systemName, replayEvents.size(), bodies);
        return new RescanResult(new TargetSystem(systemName, systemAddress), replayEvents.size(), bodies);
    }

    private static void seedStarPosFromTransitions(List<EliteLogEvent> replayEvents, SystemState state) {
        if (state.getStarPos() != null) {
            return;
        }
        for (EliteLogEvent event : replayEvents) {
            double[] pos = null;
            if (event instanceof LocationEvent e) {
                pos = e.getStarPos();
            } else if (event instanceof FsdJumpEvent e) {
                pos = e.getStarPos();
            }
            if (pos != null) {
                state.setStarPos(pos);
                return;
            }
        }
    }

    private static TargetSystem targetFromContainer(Container root) {
        if (root instanceof SystemTabPanel panel) {
            TargetSystem t = targetFromPanel(panel);
            if (t != null) {
                return t;
            }
        }
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                TargetSystem t = targetFromContainer(nested);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static TargetSystem targetFromPanel(SystemTabPanel panel) {
        SystemState state = panel.getState();
        if (state == null) {
            return null;
        }
        String name = state.getSystemName();
        long address = state.getSystemAddress();
        if (name == null || name.isBlank() || address == 0L) {
            return null;
        }
        return new TargetSystem(name, address);
    }

    private static void restoreProperty(String key, String previous) {
        if (previous != null) {
            System.setProperty(key, previous);
        } else {
            System.clearProperty(key);
        }
    }

    private static void reportProgress(RescanJournalsMain.RescanProgressListener progress,
            String phase, int percent, String detail) {
        if (progress != null) {
            progress.onProgress(phase, percent, detail);
        }
    }

    /** EDT-safe reload after a background rescan completes. */
    public static void reloadDisplayedSystemFromCacheOnEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            SystemTabPanel.notifyAllInstancesReloadDisplayedSystemFromCache();
        } else {
            SwingUtilities.invokeLater(SystemTabPanel::notifyAllInstancesReloadDisplayedSystemFromCache);
        }
    }
}
