package org.dce.ed.util;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dce.ed.cache.SystemCache;

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
    private final SpanshRequestTracker requestTracker =
            new SpanshRequestTracker(Clock.systemUTC(), Duration.ofMinutes(5));
    private final ExecutorService fetchExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SpanshLandmarkFetch");
        thread.setDaemon(true);
        return thread;
    });

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
     * <p>
     * During bulk journal rescan, never opens a second SQLite connection or hits the network
     * (the cache writer holds a long transaction).
     */
    public SpanshBodyExobiologyInfo getOrFetch(String systemName, String bodyName) {
        if (SystemCache.isBulkSystemWrite()) {
            return memoryOnly(systemName, bodyName);
        }
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
        if (!requestTracker.tryStart(k)) {
            return null;
        }
        return fetchAndStore(k, systemName, bodyName);
    }

    /**
     * Starts a background network fetch on a cache miss. Duplicate requests are coalesced and
     * failures are held for five minutes before another attempt is allowed.
     */
    public boolean requestFetch(String systemName, String bodyName, Runnable onComplete) {
        if (SystemCache.isBulkSystemWrite()) {
            return false;
        }
        String k = key(systemName, bodyName);
        if (cache.containsKey(k) || !requestTracker.tryStart(k)) {
            return false;
        }
        fetchExecutor.execute(() -> {
            try {
                fetchAndStore(k, systemName, bodyName);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        return true;
    }

    private SpanshBodyExobiologyInfo fetchAndStore(String k, String systemName, String bodyName) {
        try {
            SpanshBodyExobiologyInfo result = client.getBodyExobiologyInfo(systemName, bodyName);
            if (result == null) {
                requestTracker.failed(k);
                return null;
            }
            cache.put(k, result);
            SpanshBodyExobiologySqliteStore.save(systemName, bodyName, result);
            requestTracker.succeeded(k);
            return result;
        } catch (RuntimeException failure) {
            requestTracker.failed(k);
            System.err.println("[EDO][Spansh] landmark lookup failed for " + systemName + " / "
                    + bodyName + ": " + failure.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Returns exobiology info from memory or SQLite; never performs network I/O.
     * Populates the in-memory cache when loaded from disk.
     * <p>
     * During bulk journal rescan, memory-only (avoids {@code SQLITE_BUSY} against the write transaction).
     */
    public SpanshBodyExobiologyInfo getIfPresent(String systemName, String bodyName) {
        if (SystemCache.isBulkSystemWrite()) {
            return memoryOnly(systemName, bodyName);
        }
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

    private SpanshBodyExobiologyInfo memoryOnly(String systemName, String bodyName) {
        return cache.get(key(systemName, bodyName));
    }
}
