package org.dce.ed.systemmap;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;

/** Whether the hierarchy graph can load data for a system from cache and/or journal. */
public final class SystemHierarchyAvailability {

    private SystemHierarchyAvailability() {
    }

    public static boolean hasCachedHierarchyData(String systemName) {
        return resolveRichestCachedSystem(systemName) != null;
    }

    /** Richest SQLite row for a system name (same lookup pattern as {@link SystemMapSystemLoader#loadFromCache}). */
    public static CachedSystem resolveRichestCachedSystem(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }
        String trimmed = systemName.trim();
        CachedSystem cs = SystemCache.getInstance().get(0L, trimmed);
        if (cs == null) {
            return null;
        }
        if (cs.systemAddress != 0L) {
            CachedSystem richer = SystemCache.getInstance().get(cs.systemAddress, trimmed);
            if (richer != null) {
                cs = richer;
            }
        }
        if (hasJournalLogJson(cs)) {
            return cs;
        }
        if (cs.bodies != null && !cs.bodies.isEmpty()) {
            return cs;
        }
        return null;
    }

    private static boolean hasJournalLogJson(CachedSystem cs) {
        if (cs == null) {
            return false;
        }
        String json = cs.journalEventLogJson;
        if (json == null || json.isBlank()) {
            json = cs.modelSnapshotJson;
        }
        return json != null && !json.isBlank();
    }
}
