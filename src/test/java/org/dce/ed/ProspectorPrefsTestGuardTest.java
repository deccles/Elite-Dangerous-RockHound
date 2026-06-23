package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProspectorPrefsTestGuardTest {

    @Test
    void closeRestoresPersistedSpeechEnabled_notTestGatedOff() {
        OverlayPreferences.setSpeechEnabled(true);
        OverlayPreferences.flushBackingStore();
        ProspectorPrefsTestGuard guard = new ProspectorPrefsTestGuard();
        try {
            assertFalse(OverlayPreferences.isSpeechEnabled(), "tests gate speech off by default");
            OverlayPreferences.setSpeechEnabled(false);
        } finally {
            guard.close();
        }
        assertTrue(
                OverlayPreferences.isSpeechEnabledPersisted(),
                "guard must restore persisted speech.enabled, not the test-gated false from isSpeechEnabled()");
    }
}
