package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.dce.ed.TestEnvironment;
import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.systemmap.SystemMapSystemLoader.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coeus wide-binary + planet-binary: partial {@link SystemCache#storeSystem} must not truncate the
 * cached body list (regression for hierarchy graph showing only recently scanned bodies).
 */
class CoeusCacheIntegrationTest {

    private static final String SYSTEM = "Coeus";
    /** Matches {@code coeus-extra-body.log} and real Coeus journals. */
    private static final long SYSTEM_ADDRESS = 77508721788073L;

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @BeforeEach
    void resetCache() {
        System.setProperty(SystemMapJournalEnricher.SKIP_PROPERTY, "true");
        SystemMapJournalEnricher.clearJournalDirectoryOverrideForTests();
        SystemCache.getInstance().clearAndDeleteOnDisk();
    }

    @Test
    void partialStore_preservesAllCachedBodiesAndHierarchyNodes() throws IOException {
        SystemMapFixture full = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        SystemCache cache = SystemCache.getInstance();
        cache.storeSystem(systemStateFromBodies(full.toBodies(), full.name));

        Map<Integer, BodyInfo> partial = new HashMap<>();
        put(partial, full, "A");
        put(partial, full, "Null:14");
        put(partial, full, "A 2");
        put(partial, full, "A 3");
        cache.storeSystem(systemStateFromBodies(partial, full.name));

        CachedSystem cs = cache.get(SYSTEM_ADDRESS, SYSTEM);
        assertNotNull(cs);
        assertNotNull(cs.bodies);
        assertEquals(full.toBodies().size(), cs.bodies.size(),
                "partial store must not drop previously cached Coeus bodies (B, A 1, A 4, moons)");

        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);
        assertEquals(full.toBodies().size(), loaded.getBodies().size());

        SystemSession session = SystemSessionFactory.open(new Loaded(SYSTEM, loaded.getBodies(), "cache"));
        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);

        assertTrue(graph.nodeByKey.containsKey(Integer.valueOf(full.bodyIdByLabel("A"))));
        assertTrue(graph.nodeByKey.containsKey(Integer.valueOf(full.bodyIdByLabel("B"))));
        assertTrue(graph.nodeByKey.containsKey(Integer.valueOf(full.bodyIdByLabel("A 2"))));
        assertTrue(graph.nodeByKey.size() >= 4);
    }

    @Test
    void sparseCache_loaderMergesJournalRingBodies(@TempDir Path journalDir) throws IOException {
        System.clearProperty(SystemMapJournalEnricher.SKIP_PROPERTY);
        SystemMapFixture full = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        SystemCache cache = SystemCache.getInstance();
        Map<Integer, BodyInfo> partial = sparseCoeusFiveBodies(full);
        copyTestJournalSnippet(journalDir, "coeus-a2-rings.log");
        System.setProperty(SystemMapJournalEnricher.SKIP_PROPERTY, "true");
        cache.storeSystem(systemStateFromBodies(partial, full.name));
        System.clearProperty(SystemMapJournalEnricher.SKIP_PROPERTY);
        Loaded loaded = SystemMapSystemLoader.load(SYSTEM, Source.CACHE, journalDir);
        assertEquals("cache+journal", loaded.loadedFrom);
        assertEquals(partial.size(), loaded.cacheBodyCount);
        assertTrue(loaded.journalBodiesAdded >= 2,
                "journal should add A 2 ring belts missing from sparse cache");
        assertNotNull(findBodyByShortName(loaded.bodies, "A 2 A Ring"));
        assertNotNull(findBodyByShortName(loaded.bodies, "A 2 B Ring"));
        assertTrue(loaded.bodies.size() >= partial.size() + 2);

        SystemSession session = SystemSessionFactory.open(loaded);
        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        assertTrue(graph.nodeByKey.size() >= 4);
    }

    @Test
    void sparseCache_loaderMergesJournalBodies(@TempDir Path journalDir) throws IOException {
        System.clearProperty(SystemMapJournalEnricher.SKIP_PROPERTY);
        SystemMapFixture full = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        SystemCache cache = SystemCache.getInstance();
        Map<Integer, BodyInfo> partial = sparseCoeusFiveBodies(full);
        System.setProperty(SystemMapJournalEnricher.SKIP_PROPERTY, "true");
        cache.storeSystem(systemStateFromBodies(partial, full.name));
        System.clearProperty(SystemMapJournalEnricher.SKIP_PROPERTY);
        copyTestJournalSnippet(journalDir, "coeus-extra-body.log");

        Loaded loaded = SystemMapSystemLoader.load(SYSTEM, Source.CACHE, journalDir);
        assertEquals("cache+journal", loaded.loadedFrom);
        assertNotNull(findBodyByShortName(loaded.bodies, "B"), "journal merge should add Coeus B");
        assertNotNull(findBodyByShortName(loaded.bodies, "A 1"), "journal merge should add Coeus A 1");
    }

    @Test
    void sparseCache_storeSystemRepairsFromJournal(@TempDir Path journalDir) throws IOException {
        SystemMapFixture full = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        SystemCache cache = SystemCache.getInstance();
        Map<Integer, BodyInfo> partial = sparseCoeusFiveBodies(full);
        copyTestJournalSnippet(journalDir, "coeus-extra-body.log");

        // Seed sparse cache without journal replay (real Saved Games dir must not affect CI).
        System.setProperty(SystemMapJournalEnricher.SKIP_PROPERTY, "true");
        cache.storeSystem(systemStateFromBodies(partial, full.name));

        SystemState saveAgain = systemStateFromBodies(partial, full.name);
        SystemMapJournalEnricher.setJournalDirectoryOverrideForTests(journalDir);
        System.clearProperty(SystemMapJournalEnricher.SKIP_PROPERTY);
        try {
            cache.storeSystem(saveAgain);
        } finally {
            SystemMapJournalEnricher.clearJournalDirectoryOverrideForTests();
            System.setProperty(SystemMapJournalEnricher.SKIP_PROPERTY, "true");
        }

        CachedSystem cs = cache.get(SYSTEM_ADDRESS, SYSTEM);
        assertNotNull(cs);
        int expectedMin = partial.size() + 2;
        assertTrue(cs.bodies.size() >= expectedMin,
                "storeSystem should union journal scans into sparse Coeus cache (was "
                        + partial.size() + ", +B +A 1, need >= " + expectedMin + ", got " + cs.bodies.size() + ")");
        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);
        assertNotNull(findBodyByShortName(loaded.getBodies(), "B"));
        assertNotNull(findBodyByShortName(loaded.getBodies(), "A 1"));
    }

    private static Map<Integer, BodyInfo> sparseCoeusFiveBodies(SystemMapFixture full) {
        Map<Integer, BodyInfo> partial = new HashMap<>();
        put(partial, full, "A");
        put(partial, full, "Null:14");
        put(partial, full, "A 2");
        put(partial, full, "A 3");
        return partial;
    }

    private static void copyTestJournalSnippet(Path journalDir, String resourceName) throws IOException {
        Path journalFile = journalDir.resolve("Journal.2026-01-01T000000.01.log");
        try (InputStream in = CoeusCacheIntegrationTest.class.getResourceAsStream("/journals/" + resourceName)) {
            assertNotNull(in, resourceName);
            Files.copy(in, journalFile);
        }
    }

    @Test
    void fullFixture_hierarchyIncludesWideBinaryB() throws IOException {
        SystemMapFixture coeus = SystemMapFixtureLoader.loadClasspath("coeus-a-branch-planet-binary.json");
        Map<Integer, BodyInfo> bodies = coeus.toBodies();
        SystemSession session = SystemSessionFactory.open(new Loaded(coeus.name, bodies, "cache"));
        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        assertNotNull(findNode(graph.root, "B"), "wide-binary companion B must appear under Null:0");
        assertTrue(graph.nodeByKey.size() >= 7);
    }

    private static void put(Map<Integer, BodyInfo> map, SystemMapFixture fixture, String label) {
        int id = fixture.bodyIdByLabel(label);
        BodyInfo b = fixture.toBodies().get(Integer.valueOf(id));
        assertNotNull(b, label);
        map.put(Integer.valueOf(id), b);
    }

    private static SystemState systemStateFromBodies(Map<Integer, BodyInfo> bodies, String systemName) {
        SystemState state = new SystemState();
        state.setSystemName(systemName);
        state.setSystemAddress(SYSTEM_ADDRESS);
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            BodyInfo b = e.getValue();
            if (b == null) {
                continue;
            }
            int id = e.getKey().intValue();
            b.setBodyId(id);
            state.getBodies().put(Integer.valueOf(id), b);
        }
        return state;
    }

    private static BodyInfo findBodyByShortName(Map<Integer, BodyInfo> bodies, String shortName) {
        for (BodyInfo b : bodies.values()) {
            if (b != null && shortName.equals(b.getShortName())) {
                return b;
            }
        }
        return null;
    }

    private static SystemMapHierarchyBuilder.Node findNode(SystemMapHierarchyBuilder.Node node, String label) {
        if (label.equals(node.label)) {
            return node;
        }
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            SystemMapHierarchyBuilder.Node hit = findNode(child, label);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }
}
