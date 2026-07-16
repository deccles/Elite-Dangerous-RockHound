package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.BountyScanTracker;
import org.junit.jupiter.api.Test;

class VoiceCacheWarmerParallelismTest {

    @Test
    void warmParallelismRespectsSystemProperty() {
        String key = VoiceCacheWarmer.VOICE_WARM_PARALLELISM_PROPERTY;
        String prior = System.getProperty(key);
        try {
            System.setProperty(key, "3");
            assertEquals(3, VoiceCacheWarmer.warmParallelism());
        } finally {
            if (prior == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, prior);
            }
        }
    }

    @Test
    void warmParallelismDefaultIsBounded() {
        String key = VoiceCacheWarmer.VOICE_WARM_PARALLELISM_PROPERTY;
        String prior = System.getProperty(key);
        try {
            System.clearProperty(key);
            int p = VoiceCacheWarmer.warmParallelism();
            assertTrue(p >= 1 && p <= 3, "default parallelism=" + p);
        } finally {
            if (prior != null) {
                System.setProperty(key, prior);
            }
        }
    }

    @Test
    void requiredWarmupTemplatesIncludeFirstDiscoveredSystem() {
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests().contains("First Discovered System"));
    }

    @Test
    void requiredWarmupTemplatesIncludeFighterPilotReminder() {
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains("Did you forget your fighter pilot again, commander?"));
    }

    @Test
    void requiredWarmupTemplatesIncludeBountyScanPhrases() {
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains(BountyScanTracker.FIRST_BOUNTY_SPEECH));
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains(BountyScanTracker.ADDITIONAL_BOUNTY_SPEECH));
    }

    @Test
    void requiredWarmupTemplatesIncludeMissionProgressPhrases() {
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains(org.dce.ed.mission.MissionSpeechTracker.TARGET_DESTROYED_SPEECH));
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains(org.dce.ed.mission.MissionSpeechTracker.COMBAT_COMPLETE_SPEECH));
        assertTrue(VoiceCacheWarmer.requiredWarmupTemplatesForTests()
                .contains(org.dce.ed.mission.MissionSpeechTracker.DELIVERED_SPEECH));
    }

    @Test
    void isPollyRateLimitDetectsMessage() {
        assertTrue(VoiceCacheWarmer.isPollyRateLimit(
                new RuntimeException("Rate exceeded (Service: Polly, Status Code: 400)")));
        assertTrue(VoiceCacheWarmer.isPollyRateLimit(
                new Exception(new RuntimeException("Throttling: Rate exceeded"))));
    }
}
