package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

class EdsmLearnedPacingPreferencesTest {

    @Test
    void isolatedTestsNeitherReadNorWriteLiveLearning() throws Exception {
        Preferences prefs = Preferences.userRoot().node("edo-test/" + UUID.randomUUID());
        try {
            prefs.putLong("interval", 4_000L);
            EdsmLearnedPacingPreferences store = new EdsmLearnedPacingPreferences(
                    prefs, "interval", () -> true);

            assertEquals(0L, store.loadIntervalMillis());
            store.saveIntervalMillis(8_000L);
            assertEquals(4_000L, prefs.getLong("interval", 0L));
        } finally {
            prefs.removeNode();
        }
    }

    @Test
    void normalRuntimeRoundTripsLearnedInterval() throws Exception {
        Preferences prefs = Preferences.userRoot().node("edo-test/" + UUID.randomUUID());
        try {
            EdsmLearnedPacingPreferences store = new EdsmLearnedPacingPreferences(
                    prefs, "interval", () -> false);

            store.saveIntervalMillis(2_000L);

            assertEquals(2_000L, store.loadIntervalMillis());
        } finally {
            prefs.removeNode();
        }
    }
}
