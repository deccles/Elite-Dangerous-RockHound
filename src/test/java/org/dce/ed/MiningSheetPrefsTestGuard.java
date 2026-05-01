package org.dce.ed;

/**
 * Saves mining log / Google Sheets URL preferences around tests that need a local CSV backend and no sheet URL.
 * <p>
 * {@code mvn test} uses the same {@link java.util.prefs.Preferences} user store as the desktop app (Surefire only
 * isolates the SQLite path). Without restore, tests would clobber a developer's real Google Sheets settings.
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
