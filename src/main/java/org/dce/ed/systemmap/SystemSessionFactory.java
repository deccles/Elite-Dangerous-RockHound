package org.dce.ed.systemmap;

import java.util.List;
import java.util.Map;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.ed.systemmodel.SystemModelService.ModelHandle;
import org.dce.ed.systemmodel.SystemModelService.ModelState;
import org.dce.systemmodel.journal.JournalEventLogUtil;
import org.dce.systemmodel.journal.JournalRecord;

/**
 * Single entry point for merging journal rows and building {@link SystemModelService.ModelHandle}.
 */
public final class SystemSessionFactory {

    private SystemSessionFactory() {
    }

    /**
     * Builds from live {@link SystemState} after cache / EDSM / journal hydrate. Augments the event log with
     * {@link CachedBodyJournalBridge} rows from {@code state.getBodies()} when scans are missing.
     */
    public static SystemSession open(SystemState state) {
        if (state == null) {
            return SystemSession.empty(null);
        }
        String name = state.getSystemName() != null ? state.getSystemName().trim() : "";
        List<JournalRecord> merged = mergeJournalForBuild(
                name,
                state.getJournalEventLog(),
                state.getBodies(),
                null);
        if (!merged.isEmpty()) {
            state.setJournalEventLog(merged);
        }
        ModelHandle handle = merged.isEmpty()
                ? new ModelHandle(ModelState.ERROR, null, "No journal scans for model", List.of())
                : SystemModelService.rebuild(state, false);
        SystemSession session = SystemSession.of(state, handle);
        SystemSessionRegistry.publish(session);
        return session;
    }

    /** Builds from loader snapshot (hierarchy graph tool, cold cache). */
    public static SystemSession open(Loaded loaded) {
        if (loaded == null || loaded.bodies == null || loaded.bodies.isEmpty()) {
            return SystemSession.empty(null);
        }
        String name = loaded.systemName != null ? loaded.systemName.trim() : "";
        if (name.isEmpty()) {
            return SystemSession.empty(null);
        }
        SystemState state = new SystemState();
        state.setSystemName(name);
        CachedSystem cs = SystemHierarchyAvailability.resolveRichestCachedSystem(name);
        if (cs != null && cs.systemAddress != 0L) {
            state.setSystemAddress(cs.systemAddress);
        }
        List<JournalRecord> normalized = List.of();
        if (cs != null) {
            SystemState fromCache = SystemModelHierarchyBuilder.stateWithJournalLogFromCache(name, cs);
            if (fromCache != null && !fromCache.getJournalEventLog().isEmpty()) {
                normalized = JournalEventLogUtil.normalizeForSystemBuild(name, fromCache.getJournalEventLog());
                if (fromCache.getSystemAddress() != 0L) {
                    state.setSystemAddress(fromCache.getSystemAddress());
                }
            }
        }
        List<JournalRecord> merged = mergeJournalForBuild(name, normalized, loaded.bodies, cs);
        if (merged.isEmpty()) {
            return SystemSession.empty(state);
        }
        state.setJournalEventLog(merged);
        for (Map.Entry<Integer, BodyInfo> e : loaded.bodies.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                state.getBodies().put(e.getKey(), e.getValue());
            }
        }
        ModelHandle handle = SystemModelService.rebuild(state, false);
        SystemSession session = SystemSession.of(state, handle);
        SystemSessionRegistry.publish(session);
        return session;
    }

    static List<JournalRecord> mergeJournalForBuild(
            String systemName,
            List<JournalRecord> normalized,
            Map<Integer, BodyInfo> bodies,
            CachedSystem cs) {
        List<JournalRecord> merged = normalized != null ? normalized : List.of();
        if (cs != null) {
            merged = CachedBodyJournalBridge.mergeMissingFromCache(systemName, merged, cs);
        }
        /*
         * Always reconcile from live {@link BodyInfo} when available — fixes stale journal rows that still carry
         * mistaken {@code Null:N} parents while the map table already has {@code Star:N}/{@code Planet:N}.
         */
        if (bodies != null && !bodies.isEmpty()) {
            merged = CachedBodyJournalBridge.mergeMissingFromBodyInfo(systemName, merged, bodies);
        }
        return JournalEventLogUtil.dedupeScansByDesignation(systemName, merged);
    }
}
