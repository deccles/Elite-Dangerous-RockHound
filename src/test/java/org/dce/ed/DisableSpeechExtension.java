package org.dce.ed;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps prospector / Polly announcements silent during tests (Maven, IDE, and single-class runs).
 * Registered via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}.
 */
public final class DisableSpeechExtension implements BeforeEachCallback {

    /** When true, Polly playback is suppressed (always during tests). */
    static final String PROPERTY = "edo.test.disableSpeech";

    /**
     * When true, {@link OverlayPreferences#isSpeechEnabled()} reads user prefs so speech-gating tests can run
     * without re-enabling audio ({@link #PROPERTY} stays true).
     */
    static final String ALLOW_SPEECH_GATING_PROPERTY = "edo.test.allowSpeechGating";

    static {
        TestEnvironment.ensureTestIsolation();
        System.setProperty(PROPERTY, "true");
        System.setProperty(ALLOW_SPEECH_GATING_PROPERTY, "false");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        boolean allowGating = context.getTestMethod()
                .map(m -> m.isAnnotationPresent(AllowSpeechForTest.class))
                .orElse(false);
        System.setProperty(PROPERTY, "true");
        System.setProperty(ALLOW_SPEECH_GATING_PROPERTY, allowGating ? "true" : "false");
    }
}
