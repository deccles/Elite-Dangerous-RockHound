package org.dce.ed;

import java.nio.file.Files;
import java.nio.file.Path;

import org.dce.ed.cache.SystemCache;

/**
 * Test isolation: redirect any cache or file output away from user data.
 * Call {@link #ensureTestIsolation()} from a test class so it runs before tests;
 * if any code path touches {@link SystemCache}, it will use a temp SQLite DB instead of the real cache.
 * <p>
 * Current unit tests do not trigger cache writes, Preferences, or other persistence:
 * <ul>
 *   <li>{@link org.dce.ed.logreader.EliteLogParserTest} – parser only, no I/O</li>
 *   <li>{@link org.dce.ed.NavRouteParserTest} – static parse method, no cache</li>
 *   <li>{@link org.dce.ed.RouteTargetStateTest} – in-memory state only</li>
 *   <li>{@link org.dce.ed.RouteTabPanelHelperTest} – static helpers, no cache</li>
 *   <li>{@link org.dce.ed.MiningTabPanelTest} – static buildInventoryTonsFromCargo / csvEscape, no files</li>
 *   <li>{@link org.dce.ed.SystemTabTargetLogicTest} – pure logic, no I/O</li>
 * </ul>
 * Also sets {@link EdoTestFlags#ISOLATE_UI_PROPERTY} so overlay UI tests do not restore floating tabs
 * or write window bounds into the live Preferences store.
 */
public final class TestEnvironment {

    static {
        // Surefire sets this in pom.xml; IDE runners often omit it — keep tests quiet (no TTS / console).
        if (System.getProperty("edo.test.disableSpeech") == null) {
            System.setProperty("edo.test.disableSpeech", "true");
        }
        if (System.getProperty("edo.test.allowSpeechGating") == null) {
            System.setProperty("edo.test.allowSpeechGating", "false");
        }
        if (System.getProperty(EdoTestFlags.ISOLATE_UI_PROPERTY) == null) {
            System.setProperty(EdoTestFlags.ISOLATE_UI_PROPERTY, "true");
        }
    }

    private static volatile boolean initialized;

    /**
     * Call from a test class (e.g. in a {@code static { ... }} block or {@code @BeforeAll}) so that
     * SystemCache, if ever used during tests, writes to a temp directory instead of user home.
     */
    public static void ensureTestIsolation() {
        if (initialized) {
            return;
        }
        synchronized (TestEnvironment.class) {
            if (initialized) {
                return;
            }
            try {
                Path tempDir = Files.createTempDirectory("edo-test-cache");
                tempDir.toFile().deleteOnExit();
                Path cacheDb = tempDir.resolve("test-cache.db");
                System.setProperty(SystemCache.CACHE_DB_PATH_PROPERTY, cacheDb.toAbsolutePath().toString());
            } catch (Exception e) {
                throw new RuntimeException("Could not set test cache path", e);
            }
            initialized = true;
        }
    }

    private TestEnvironment() {}
}
