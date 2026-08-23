package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class FirstBonusHelperTest {

    @Test
    void explicitNoPriorFootfallAppliesBonusEvenWhenSpanshHasLandmarks() {
        List<SpanshLandmark> landmarks = List.of(
                new SpanshLandmark("Biological", "Stratum", 1.0, 2.0));

        assertTrue(FirstBonusHelper.firstBonusApplies(false, landmarks));
    }

    @Test
    void explicitPriorFootfallSuppressesBonus() {
        assertFalse(FirstBonusHelper.firstBonusApplies(true, Collections.emptyList()));
    }

    @Test
    void unknownFootfallAppliesOptimisticBonusWhenSpanshIsUnavailable() {
        assertTrue(FirstBonusHelper.firstBonusApplies(null, null));
    }

    @Test
    void unknownFootfallAppliesBonusWhenSpanshHasNoLandmarks() {
        assertTrue(FirstBonusHelper.firstBonusApplies(null, Collections.emptyList()));
    }

    @Test
    void unknownFootfallSuppressesBonusWhenSpanshHasLandmarks() {
        List<SpanshLandmark> landmarks = List.of(
                new SpanshLandmark("Biological", "Stratum", 1.0, 2.0));

        assertFalse(FirstBonusHelper.firstBonusApplies(null, landmarks));
    }
}
