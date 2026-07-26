package org.dce.ed;

import java.nio.file.Files;
import java.nio.file.Path;

import org.dce.ed.cache.SystemCache;

/**
 * Test isolation: redirect cache / file output away from user data, and enable UI prefs isolation.
 * <p>
 * <b>Preferences are not sandboxed.</b> {@code Preferences.userNodeForPackage} is the same store the running
 * app uses. Never write window bounds, floating-tab layout ({@code overlay.tabLayout.json}), mining/Google
 * sheet URLs, or other live prefs from tests without snapshot/restore. Prefer in-memory fixtures.
 * <ul>
 *   <li>{@link EdoTestFlags#ISOLATE_UI_PROPERTY} — skip floating-tab restore/persist and overlay bounds writes
 *       (set here for IDE runners; Surefire also sets it in {@code pom.xml}).</li>
 *   <li>{@link #ensureTestIsolation()} — redirect {@link SystemCache} SQLite away from {@code ~/.edo}.</li>
 *   <li>{@link MiningSheetPrefsTestGuard} — snapshot/restore mining backend + Google Sheets URL when a test
 *       must temporarily change those keys.</li>
 * </ul>
 * Historical foot-gun: {@code TabLayoutStateTest} used to {@code save} then {@code clear} live tab layout prefs.
 * Round-trip JSON in memory only ({@code TabLayoutPreferences.toJson}/{@code parse}).
 * See {@code .cursor/rules/junit-live-preferences.mdc}.
 */
public final class TestEnvironment {

    static {
        // Surefire sets these in pom.xml; IDE runners often omit them.
        if (System.getProperty("edo.test.disableSpeech") == null) {
            System.setProperty("edo.test.disableSpeech", "true");
        }
        if (System.getProperty("edo.test.allowSpeechGating") == null) {
            System.setProperty("edo.test.allowSpeechGating", "false");
        }
        // CRITICAL: without this, OverlayContentPanel / TabDockingController / TabLayoutPreferences can
        // rewrite or clear the developer's floating-tab layout and window bounds.
        if (System.getProperty(EdoTestFlags.ISOLATE_UI_PROPERTY) == null) {
            System.setProperty(EdoTestFlags.ISOLATE_UI_PROPERTY, "true");
        }
    }

    private static volatile boolean initialized;

    /**
     * Call from a test class (e.g. in a {@code static { ... }} block or {@code @BeforeAll}) so that
     * SystemCache, if ever used during tests, writes to a temp directory instead of user home.
     * Does not isolate Java Preferences — those remain the live OS store.
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
