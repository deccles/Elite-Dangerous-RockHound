package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EngineeringRecommendationFooterTest {

    @Test
    void footerPointsAgentsToPublishedFormat() {
        String footer = EngineeringBuildProgressDialog.engineeringRecommendationFooter();

        assertTrue(footer.contains("Engineering recommendations:"), footer);
        assertTrue(footer.contains(
                "https://github.com/deccles/Elite-Dangerous-RockHound/blob/main/docs/engineering-recommendations.md"),
                footer);
    }
}
