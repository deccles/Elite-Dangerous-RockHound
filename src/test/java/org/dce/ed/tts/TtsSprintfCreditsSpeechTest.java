package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TtsSprintfCreditsSpeechTest {

    @Test
    void roundCreditsForSpeech_usesTenthOfMillion() {
        assertEquals(5_300_000L, TtsSprintf.roundCreditsForSpeech(5_349_000L));
        assertEquals(5_300_000L, TtsSprintf.roundCreditsForSpeech(5_250_000L));
        assertEquals(5_400_000L, TtsSprintf.roundCreditsForSpeech(5_350_000L));
        assertEquals(5_000_000L, TtsSprintf.roundCreditsForSpeech(5_049_999L));
        assertEquals(1_500_000_000L, TtsSprintf.roundCreditsForSpeech(1_520_000_000L));
    }

    @Test
    void roundCreditsForSpeech_scalesByMagnitude() {
        // Tens/hundreds of thousands → nearest 10k
        assertEquals(240_000L, TtsSprintf.roundCreditsForSpeech(242_475L));
        assertEquals(310_000L, TtsSprintf.roundCreditsForSpeech(305_335L));
        assertEquals(60_000L, TtsSprintf.roundCreditsForSpeech(62_860L));
        assertEquals(70_000L, TtsSprintf.roundCreditsForSpeech(67_984L));
        assertEquals(990_000L, TtsSprintf.roundCreditsForSpeech(994_999L));
        // Thousands → nearest 1k (KWS deltas must not become zero)
        assertEquals(3_000L, TtsSprintf.roundCreditsForSpeech(3_200L));
        assertEquals(5_000L, TtsSprintf.roundCreditsForSpeech(4_999L));
        assertEquals(1_000L, TtsSprintf.roundCreditsForSpeech(1_499L));
        // Below 1k → exact
        assertEquals(750L, TtsSprintf.roundCreditsForSpeech(750L));
        assertEquals(1L, TtsSprintf.roundCreditsForSpeech(1L));
    }

    @Test
    void creditsPlaceholder_speaksFractionalMillions() {
        TtsSprintf sp = new TtsSprintf(new PollyTtsCached());
        assertEquals(
                List.of("Bounty of", "five", "point", "three", "million", "credits found"),
                sp.formatToUtteranceChunks("Bounty of {credits} credits found", 5_300_000L));
        assertEquals(
                List.of("Bounty of", "two", "million", "credits found"),
                sp.formatToUtteranceChunks("Bounty of {credits} credits found", 2_000_000L));
        assertEquals(
                List.of("Bounty of", "twelve", "point", "seven", "million", "credits found"),
                sp.formatToUtteranceChunks(
                        "Bounty of {credits} credits found",
                        TtsSprintf.roundCreditsForSpeech(12_749_000L)));
    }
}
