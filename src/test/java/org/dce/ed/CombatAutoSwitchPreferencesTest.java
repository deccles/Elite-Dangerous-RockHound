package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;

class CombatAutoSwitchPreferencesTest {
    private static final String OLD_ATTACKED_KEY = "overlay.autoswitch.combat.whenAttacked";
    private static final String OLD_REWARD_KEY = "overlay.autoswitch.combat.onReward";
    private static final String NEW_ATTACKED_KEY = "overlay.autoswitch.combat.whenAttacked.v2";
    private static final String NEW_REWARD_KEY = "overlay.autoswitch.combat.onReward.v2";

    @Test
    void replacementKeysDefaultOnAndIgnoreOldDisabledValues() throws Exception {
        Preferences prefs = Preferences.userNodeForPackage(OverlayFrame.class);
        String oldAttacked = prefs.get(OLD_ATTACKED_KEY, null);
        String oldReward = prefs.get(OLD_REWARD_KEY, null);
        String newAttacked = prefs.get(NEW_ATTACKED_KEY, null);
        String newReward = prefs.get(NEW_REWARD_KEY, null);
        try {
            prefs.putBoolean(OLD_ATTACKED_KEY, false);
            prefs.putBoolean(OLD_REWARD_KEY, false);
            prefs.remove(NEW_ATTACKED_KEY);
            prefs.remove(NEW_REWARD_KEY);

            assertTrue(OverlayPreferences.isAutoSwitchCombatWhenAttacked());
            assertTrue(OverlayPreferences.isAutoSwitchCombatOnReward());
        } finally {
            restore(prefs, OLD_ATTACKED_KEY, oldAttacked);
            restore(prefs, OLD_REWARD_KEY, oldReward);
            restore(prefs, NEW_ATTACKED_KEY, newAttacked);
            restore(prefs, NEW_REWARD_KEY, newReward);
            prefs.flush();
        }
    }

    private static void restore(Preferences prefs, String key, String value) {
        if (value == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, value);
        }
    }
}
