package org.dce.ed.engineering;

import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engineer access rank over time, from {@code EngineerProgress}. Journal replay needs the rank at
 * each {@code EngineerCraft}: the number of rolls a grade takes depends on the rank when rolling,
 * and rank rises while crafting.
 */
public final class EngineerRankHistory {

    private static volatile EngineerRankHistory shared = new EngineerRankHistory();

    private final Map<String, NavigableMap<Instant, Integer>> byEngineer = new ConcurrentHashMap<>();

    /** Timeline used by goal progress replay. The Engineering tab installs its tracker's history. */
    public static EngineerRankHistory shared() {
        return shared;
    }

    public static void useShared(EngineerRankHistory history) {
        shared = history != null ? history : new EngineerRankHistory();
    }

    public void record(String engineer, Instant at, int rank) {
        String key = EngineerReputationTracker.normalize(engineer);
        if (key.isEmpty() || at == null || rank <= 0) {
            return;
        }
        NavigableMap<Instant, Integer> timeline =
                byEngineer.computeIfAbsent(key, k -> new TreeMap<>());
        synchronized (timeline) {
            timeline.put(at, Integer.valueOf(rank));
        }
    }

    /**
     * Rank in effect at {@code at}, 0 when unknown. A rank-up logged in the same second as a craft
     * counts for that craft; Elite writes the {@code EngineerProgress} line just after it.
     */
    public int rankAt(String engineer, Instant at) {
        String key = EngineerReputationTracker.normalize(engineer);
        NavigableMap<Instant, Integer> timeline = byEngineer.get(key);
        if (timeline == null || at == null) {
            return 0;
        }
        synchronized (timeline) {
            Map.Entry<Instant, Integer> entry = timeline.floorEntry(at);
            return entry != null ? entry.getValue() : 0;
        }
    }

    public void clear() {
        byEngineer.clear();
    }
}
