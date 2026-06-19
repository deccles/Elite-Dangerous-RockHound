package org.dce.ed.tts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertTrue(p >= 2 && p <= 8, "default parallelism=" + p);
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
}
