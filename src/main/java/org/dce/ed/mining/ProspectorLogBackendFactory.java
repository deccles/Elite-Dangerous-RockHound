package org.dce.ed.mining;

import org.dce.ed.OverlayPreferences;

/**
 * Creates the prospector log backend from preferences ({@code "local"}, {@code "google"}, or {@code "both"}).
 *
 * <p>For {@code "both"} we instantiate both concrete backends and wrap them in a
 * {@link CompositeProspectorLogBackend}. The user's chosen Both-mode primary
 * ({@link OverlayPreferences#getMiningLogBothPrimary()}) becomes the composite's primary, so the Mining tab table
 * and run-resolution always read from that side.</p>
 *
 * <p>Important: when backend is {@code "google"} or {@code "both"} we do not silently fall back to local CSV if
 * setup is incomplete. The Google backend reports setup/auth/connectivity errors so the UI can keep Google
 * selected and prompt the user to reconnect.</p>
 */
public final class ProspectorLogBackendFactory {

    private ProspectorLogBackendFactory() {
    }

    public static ProspectorLogBackend create() {
        String backend = OverlayPreferences.getMiningLogBackend();
        String url = OverlayPreferences.getMiningGoogleSheetsUrl();

        switch (backend) {
            case "google":
                return new GoogleSheetsBackend(url);
            case "both": {
                GoogleSheetsBackend sheets = new GoogleSheetsBackend(url);
                LocalCsvBackend csv = new LocalCsvBackend();
                String primaryChoice = OverlayPreferences.getMiningLogBothPrimary();
                if ("local".equals(primaryChoice)) {
                    return new CompositeProspectorLogBackend(csv, sheets);
                }
                return new CompositeProspectorLogBackend(sheets, csv);
            }
            case "local":
            default:
                return new LocalCsvBackend();
        }
    }
}
