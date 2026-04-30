package org.dce.ed.mining;

import org.dce.ed.OverlayPreferences;

/**
 * Creates the prospector log backend from preferences (local CSV or Google Sheets).
 */
public final class ProspectorLogBackendFactory {

    private ProspectorLogBackendFactory() {
    }

    /**
     * Returns the backend selected by preferences.
     * <p>
     * Important: when backend is "google", we do not silently fall back to local CSV if setup is incomplete.
     * The Google backend reports setup/auth/connectivity errors so the UI can keep Google selected and prompt
     * the user to reconnect.
     * </p>
     */
    public static ProspectorLogBackend create() {
        String backend = OverlayPreferences.getMiningLogBackend();
        String url = OverlayPreferences.getMiningGoogleSheetsUrl();

        if ("google".equals(backend)) {
            return new GoogleSheetsBackend(url);
        }
        return new LocalCsvBackend();
    }
}
