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

        public Loaded(String systemName, Map<Integer, BodyInfo> bodies, String loadedFrom) {
            this.systemName = systemName;
            this.bodies = bodies;
            this.loadedFrom = loadedFrom;
        }
    }

    private SystemMapSystemLoader() {
    }

    public static Loaded load(String systemName, Source source) throws IOException {
        if (systemName == null || systemName.isBlank()) {
            throw new IOException("System name is required");
        }
        String trimmed = systemName.trim();
        if (source == Source.JOURNAL) {
            return loadFromJournal(trimmed);
        }
        if (source == Source.CACHE) {
            Loaded cached = loadFromCache(trimmed);
            if (cached == null) {
                throw new IOException("No cached bodies for: " + trimmed);
            }
            return cached;
        }
        Loaded cached = loadFromCache(trimmed);
        if (cached != null && !cached.bodies.isEmpty()) {
            return cached;
        }
        return loadFromJournal(trimmed);
    }

    public static Loaded loadFromCache(String systemName) {
        CachedSystem cs = SystemCache.getInstance().get(0L, systemName);
        if (cs == null || cs.bodies == null || cs.bodies.isEmpty()) {
            return null;
        }
        SystemState state = new SystemState();
        SystemCache.getInstance().loadInto(state, cs);
        if (state.getBodies().isEmpty()) {
            return null;
        }
        String name = cs.systemName != null ? cs.systemName : systemName;
        return new Loaded(name, state.getBodies(), "cache");
    }

    public static Loaded loadFromJournal(String systemName) throws IOException {
        Path journalDir = journalDirectory();
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
