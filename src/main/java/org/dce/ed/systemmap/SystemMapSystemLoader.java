package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;

/**
 * Loads scanned bodies for one system from RockHound cache and/or Elite journal logs.
 */
public final class SystemMapSystemLoader {

    public enum Source {
        /** SQLite cache by canonical system name. */
        CACHE,
        /** Replay Scan + ScanBaryCentre from journal directory. */
        JOURNAL,
        /** Cache first, then journal if cache has no bodies. */
        AUTO
    }

    public static final class Loaded {
        public final String systemName;
        public final Map<Integer, BodyInfo> bodies;
        public final String loadedFrom;
        /** Bodies in cache before journal union; {@code -1} when not applicable. */
        public final int cacheBodyCount;
        /** Bodies newly added from journal during union; {@code 0} when not enriched. */
        public final int journalBodiesAdded;

        public Loaded(String systemName, Map<Integer, BodyInfo> bodies, String loadedFrom) {
            this(systemName, bodies, loadedFrom, -1, 0);
        }

        public Loaded(String systemName, Map<Integer, BodyInfo> bodies, String loadedFrom,
                int cacheBodyCount, int journalBodiesAdded) {
            this.systemName = systemName;
            this.bodies = bodies;
            this.loadedFrom = loadedFrom;
            this.cacheBodyCount = cacheBodyCount;
            this.journalBodiesAdded = journalBodiesAdded;
        }
    }

    private SystemMapSystemLoader() {
    }

    public static Loaded load(String systemName, Source source) throws IOException {
        return load(systemName, source, null);
    }

    /**
     * @param journalDirectory optional override for journal enrichment / {@link Source#JOURNAL} loads (tests)
     */
    public static Loaded load(String systemName, Source source, Path journalDirectory) throws IOException {
        if (systemName == null || systemName.isBlank()) {
            throw new IOException("System name is required");
        }
        Path prevJournalOverride = null;
        if (journalDirectory != null) {
            prevJournalOverride = SystemMapJournalEnricher.journalDirectoryOverride;
            SystemMapJournalEnricher.setJournalDirectoryOverrideForTests(journalDirectory);
        }
        try {
            return loadInternal(systemName, source);
        } finally {
            if (journalDirectory != null) {
                if (prevJournalOverride != null) {
                    SystemMapJournalEnricher.setJournalDirectoryOverrideForTests(prevJournalOverride);
                } else {
                    SystemMapJournalEnricher.clearJournalDirectoryOverrideForTests();
                }
            }
        }
    }

    private static Loaded loadInternal(String systemName, Source source) throws IOException {
        String trimmed = systemName.trim();
        if (source == Source.JOURNAL) {
            Path dir = SystemMapJournalEnricher.resolveJournalDirectory();
            return loadFromJournal(trimmed, dir);
        }
        if (source == Source.CACHE) {
            Loaded cached = loadFromCache(trimmed);
            if (cached == null) {
                throw new IOException("No cached bodies for: " + trimmed);
            }
            Loaded enriched = enrichFromJournalIfSparse(cached);
            persistRepairedCacheIfEnriched(enriched, source);
            return enriched;
        }
        Loaded cached = loadFromCache(trimmed);
        if (cached != null && !cached.bodies.isEmpty()) {
            Loaded enriched = enrichFromJournalIfSparse(cached);
            persistRepairedCacheIfEnriched(enriched, source);
            return enriched;
        }
        Path dir = SystemMapJournalEnricher.resolveJournalDirectory();
        return loadFromJournal(trimmed, dir);
    }

    public static Loaded loadFromCache(String systemName) {
        long addr = 0L;
        CachedSystem cs = SystemCache.getInstance().get(0L, systemName);
        if (cs == null || cs.bodies == null || cs.bodies.isEmpty()) {
            return null;
        }
        if (cs.systemAddress != 0L) {
            addr = cs.systemAddress;
        }
        CachedSystem richest = SystemCache.getInstance().get(addr, systemName);
        if (richest != null && richest.bodies != null && !richest.bodies.isEmpty()) {
            cs = richest;
        }
        SystemState state = new SystemState();
        SystemCache.getInstance().loadInto(state, cs);
        if (state.getBodies().isEmpty()) {
            return null;
        }
        String name = cs.systemName != null ? cs.systemName : systemName;
        return new Loaded(name, state.getBodies(), "cache");
    }

    /**
     * Union journal {@code Scan}/{@code ScanBaryCentre} when the cache row is sparser than journal history
     * (truncated partial saves, incremental rescan skipping older lines).
     */
    static Loaded enrichFromJournalIfSparse(Loaded cached) {
        if (cached == null || cached.bodies == null || cached.bodies.isEmpty()) {
            return cached;
        }
        Map<Integer, BodyInfo> merged = new java.util.HashMap<>(cached.bodies);
        int cacheBodies = cached.bodies.size();
        int added = SystemMapJournalEnricher.mergeMissingBodiesFromJournal(merged, cached.systemName);
        if (added <= 0) {
            return cached;
        }
        return new Loaded(cached.systemName, merged, "cache+journal", cacheBodies, added);
    }

    /**
     * After CACHE load unioned journal scans, write the repaired body map back to SQLite so the next
     * CACHE-only load is complete.
     */
    static void persistRepairedCacheIfEnriched(Loaded loaded, Source source) {
        if (source != Source.CACHE || loaded == null || loaded.journalBodiesAdded <= 0) {
            return;
        }
        CachedSystem cs = SystemCache.getInstance().get(0L, loaded.systemName);
        long addr = cs != null ? cs.systemAddress : 0L;
        if (addr == 0L) {
            return;
        }
        SystemState state = new SystemState();
        state.setSystemName(loaded.systemName);
        state.setSystemAddress(addr);
        for (Map.Entry<Integer, BodyInfo> e : loaded.bodies.entrySet()) {
            BodyInfo b = e.getValue();
            if (b == null) {
                continue;
            }
            int id = e.getKey().intValue();
            b.setBodyId(id);
            state.getBodies().put(Integer.valueOf(id), b);
        }
        SystemCache.getInstance().storeSystem(state);
    }

    public static Loaded loadFromJournal(String systemName) throws IOException {
        return loadFromJournal(systemName, journalDirectory());
    }

    static Loaded loadFromJournal(String systemName, Path journalDir) throws IOException {
        if (!Files.isDirectory(journalDir)) {
            throw new IOException("Journal directory not found: " + journalDir);
        }
        SystemState state = JournalSystemMapLoader.loadFromJournal(journalDir, systemName);
        return new Loaded(state.getSystemName(), state.getBodies(), "journal");
    }

    public static Path journalDirectory() {
        String env = System.getenv("EDO_JOURNAL_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        return JournalSystemMapLoader.defaultJournalDirectory();
    }
}
