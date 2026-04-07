package org.dce.ed.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spansh body exobiology info (landmarks + exclude-from-exobiology) keyed by (systemName, bodyName).
 * Layered: in-memory → SQLite ({@link SpanshBodyExobiologySqliteStore}, same DB as {@link org.dce.ed.cache.SystemCache})
 * → Spansh HTTP on cache miss. Successful network responses are persisted for the next session.
 */
public final class SpanshLandmarkCache {

    private static final String KEY_SEP = "\t";

    private static final SpanshLandmarkCache INSTANCE = new SpanshLandmarkCache();

    private final Map<String, SpanshBodyExobiologyInfo> cache = new ConcurrentHashMap<>();
    private final SpanshClient client = new SpanshClient();

    private SpanshLandmarkCache() {
    }

    public static SpanshLandmarkCache getInstance() {
        return INSTANCE;
    }

    private static String key(String systemName, String bodyName) {
        if (systemName == null) systemName = "";
        if (bodyName == null) bodyName = "";
        return systemName + KEY_SEP + bodyName;
    }

    /**
     * Returns exobiology info from memory, else SQLite, else Spansh; persists successful HTTP results to SQLite.
     * Returns null on API/search failure (not stored).
     */
    public SpanshBodyExobiologyInfo getOrFetch(String systemName, String bodyName) {
        String k = key(systemName, bodyName);
        SpanshBodyExobiologyInfo mem = cache.get(k);
        if (mem != null) {
            return mem;
        }
        SpanshBodyExobiologyInfo disk = SpanshBodyExobiologySqliteStore.load(systemName, bodyName);
        if (disk != null) {
            cache.put(k, disk);
            return disk;
        }
        SpanshBodyExobiologyInfo result = client.getBodyExobiologyInfo(systemName, bodyName);
        if (result != null) {
            cache.put(k, result);
            SpanshBodyExobiologySqliteStore.save(systemName, bodyName, result);
        }
        return result;
    }

    /**
     * Returns exobiology info from memory or SQLite; never performs network I/O.
     * Populates the in-memory cache when loaded from disk.
     */
    public SpanshBodyExobiologyInfo getIfPresent(String systemName, String bodyName) {
        String k = key(systemName, bodyName);
        SpanshBodyExobiologyInfo mem = cache.get(k);
        if (mem != null) {
            return mem;
        }
        SpanshBodyExobiologyInfo disk = SpanshBodyExobiologySqliteStore.load(systemName, bodyName);
        if (disk != null) {
            cache.put(k, disk);
        }
        return disk;
    }
}
