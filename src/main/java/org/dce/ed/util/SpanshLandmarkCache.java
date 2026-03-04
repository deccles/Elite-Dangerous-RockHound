package org.dce.ed.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for Spansh landmark data keyed by (systemName, bodyName).
 * Used to avoid re-querying Spansh for the same body in one session.
 */
public final class SpanshLandmarkCache {

    private static final String KEY_SEP = "\t";

    private static final SpanshLandmarkCache INSTANCE = new SpanshLandmarkCache();

    private final Map<String, List<SpanshLandmark>> cache = new ConcurrentHashMap<>();
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
     * Returns cached landmarks, or fetches from Spansh and caches on success.
     * Returns null on API/search failure (not cached). Returns empty list when body has no landmarks.
     */
    public List<SpanshLandmark> getOrFetch(String systemName, String bodyName) {
        String k = key(systemName, bodyName);
        List<SpanshLandmark> cached = cache.get(k);
        if (cached != null) {
            return cached;
        }
        List<SpanshLandmark> result = client.getBodyLandmarks(systemName, bodyName);
        if (result != null) {
            cache.put(k, result);
        }
        return result;
    }
}
