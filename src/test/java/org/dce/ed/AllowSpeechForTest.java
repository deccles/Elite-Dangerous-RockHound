package org.dce.ed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in for tests that assert speech-gating logic with a recording {@link org.dce.ed.tts.TtsSprintf}
 * stub (no real audio). Sets {@code edo.test.allowSpeechGating=true} so {@link OverlayPreferences#isSpeechEnabled()}
 * reads prefs; {@code edo.test.disableSpeech} stays true so Polly never plays sound.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AllowSpeechForTest {
}
