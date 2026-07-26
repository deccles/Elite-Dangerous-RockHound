package org.dce.ed;

/**
 * Saves mining log / Google Sheets URL preferences around tests that need a local CSV backend and no sheet URL.
 * <p>
 * {@code mvn test} uses the same {@link java.util.prefs.Preferences} user store as the desktop app (Surefire only
 * isolates the SQLite path and UI layout writes via {@code edo.test.isolateUi}). Without restore, tests would
 * clobber a developer's real Google Sheets settings.
 * <p>
 * Pattern for any new prefs-mutating test: snapshot → mutate → assert → restore in {@code close()}/{@code finally}.
 * Never leave cleared or fake values in the live store. See {@code .cursor/rules/junit-live-preferences.mdc}.
 */
final class MiningSheetPrefsTestGuard implements AutoCloseable {

    private final String savedBackend;
    private final String savedUrl;

    MiningSheetPrefsTestGuard() {
        savedBackend = OverlayPreferences.getMiningLogBackend();
        savedUrl = OverlayPreferences.getMiningGoogleSheetsUrl();
        OverlayPreferences.setMiningLogBackend("local");
        OverlayPreferences.clearMiningGoogleSheetsUrl();
    }

    @Override
    public void close() {
        OverlayPreferences.setMiningLogBackend(savedBackend);
        if (savedUrl == null || savedUrl.isBlank()) {
            OverlayPreferences.clearMiningGoogleSheetsUrl();
        } else {
            OverlayPreferences.setMiningGoogleSheetsUrl(savedUrl);
        }
        OverlayPreferences.flushBackingStore();
    }
}
