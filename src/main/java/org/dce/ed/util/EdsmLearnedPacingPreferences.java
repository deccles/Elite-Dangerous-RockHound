package org.dce.ed.util;

import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;

/** Preference-backed learned EDSM pacing that stays isolated during tests. */
final class EdsmLearnedPacingPreferences implements EdsmRequestGate.LearnedPacingStore {
    private final Preferences preferences;
    private final String key;
    private final BooleanSupplier isolated;

    EdsmLearnedPacingPreferences(Preferences preferences, String key, BooleanSupplier isolated) {
        this.preferences = preferences;
        this.key = key;
        this.isolated = isolated;
    }

    @Override
    public long loadIntervalMillis() {
        return isolated.getAsBoolean() ? 0L : preferences.getLong(key, 0L);
    }

    @Override
    public void saveIntervalMillis(long intervalMillis) {
        if (!isolated.getAsBoolean()) {
            preferences.putLong(key, intervalMillis);
        }
    }
}
