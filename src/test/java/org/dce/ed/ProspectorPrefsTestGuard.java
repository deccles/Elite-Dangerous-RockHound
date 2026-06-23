package org.dce.ed;

/**
 * Saves prospector filter preferences around tests that mutate them via {@link OverlayPreferences}.
 * <p>
 * {@code mvn test} uses the same {@link java.util.prefs.Preferences} user store as the desktop app.
 * Without restore, tests would clobber a developer's real prospector materials / threshold settings.
 */
final class ProspectorPrefsTestGuard implements AutoCloseable {

    private final String savedMaterialsCsv;
    private final double savedMinProportionPercent;
    private final int savedMinAvgValueCrPerTon;
    private final boolean savedSpeechEnabled;

    ProspectorPrefsTestGuard() {
        savedMaterialsCsv = OverlayPreferences.getProspectorMaterialsCsv();
        savedMinProportionPercent = OverlayPreferences.getProspectorMinProportionPercent();
        savedMinAvgValueCrPerTon = OverlayPreferences.getProspectorMinAvgValueCrPerTon();
        savedSpeechEnabled = OverlayPreferences.isSpeechEnabledPersisted();
    }

    @Override
    public void close() {
        OverlayPreferences.setProspectorMaterialsCsv(savedMaterialsCsv);
        OverlayPreferences.setProspectorMinProportionPercent(savedMinProportionPercent);
        OverlayPreferences.setProspectorMinAvgValueCrPerTon(savedMinAvgValueCrPerTon);
        OverlayPreferences.setSpeechEnabled(savedSpeechEnabled);
        OverlayPreferences.flushBackingStore();
    }
}
