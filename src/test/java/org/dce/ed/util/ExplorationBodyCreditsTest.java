package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class ExplorationBodyCreditsTest {

    @Test
    void achievableTotal_matches_community_tool_breakdown_order() {
        // Base scan value from tooltip; mapping ×10/3, +30% Odyssey mapping rule, ×1.25 efficiency (no first discoverer).
        double raw = 296_016.0;
        long total = ExplorationBodyCredits.achievableTotalCredits(raw, false, false);
        assertTrue(total >= 1_603_400L && total <= 1_603_450L, "total=" + total);
    }

    @Test
    void bodyInfo_elw_with_mass_uses_formula() {
        BodyInfo b = new BodyInfo();
        b.setHighValue(true);
        b.setPlanetClass("Earth-like world");
        b.setTerraformState("Terraformed");
        b.setMassEm(Double.valueOf(1.0));
        b.setWasDiscovered(Boolean.TRUE);
        b.setWasMapped(Boolean.FALSE);
        long cr = ExplorationBodyCredits.achievableExplorationTotalCredits(b);
        assertTrue(cr >= 1_000_000L, "ELW achievable should exceed 1M Cr, got " + cr);
    }

    @Test
    void explorationK_earthLike() {
        assertEquals(64831 + 116295, ExplorationBodyCredits.explorationK("Earth-like world", ""));
    }

    @Test
    void explorationK_metalRich_notTerraformableStringIsNotTerraformTier() {
        assertEquals(21790, ExplorationBodyCredits.explorationK("Metal rich body", "Not terraformable"));
    }

    @Test
    void explorationK_metalRich_candidateForTerraformingUsesTerraformTier() {
        assertEquals(21790 + 105678,
                ExplorationBodyCredits.explorationK("Metal rich body", "Candidate for terraforming"));
    }

    @Test
    void explorationTypeHint_usesAtmoWhenPlanetClassMissing() {
        BodyInfo b = new BodyInfo();
        b.setAtmoOrType("Earth-like world");
        assertEquals("Earth-like world", ExplorationBodyCredits.explorationTypeHint(b));
        assertTrue(ExplorationBodyCredits.explorationK(
                ExplorationBodyCredits.explorationTypeHint(b), "") > 0);
    }

    @Test
    void journalValuableTarget_trueWhenOnlyAtmoOrTypeHasElw() {
        BodyInfo b = new BodyInfo();
        b.setHighValue(false);
        b.setAtmoOrType("Earth-like world");
        assertTrue(ExplorationBodyCredits.isJournalValuableExplorationTarget(b));
        ExplorationBodyCredits.syncHighValueExplorationFromClassifiers(b);
        assertTrue(b.isHighValue());
    }

    @Test
    void journalValuableTarget_notTerraformableIsFalse() {
        assertFalse(ExplorationBodyCredits.isJournalValuableExplorationTarget(
                "High metal content body", "Not terraformable"));
    }
}
