package org.dce.ed.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.junit.jupiter.api.Test;

class SystemCacheEdsmStandaloneMergeTest {

    @Test
    void mergeBodiesFromEdsm_standaloneCreatesVisibleRowsForCompleteFss() {
        SystemState state = new SystemState();
        state.setSystemName("Laksak");
        state.setFssProgress(1.0);
        state.setTotalBodies(2);

        BodiesResponse edsm = new BodiesResponse();
        edsm.name = "Laksak";

        BodiesResponse.Body star = new BodiesResponse.Body();
        star.id = 1;
        star.name = "Laksak";
        star.type = "Star";
        star.subType = "F (White) Star";
        star.distanceToArrival = 0.0;

        BodiesResponse.Body planet = new BodiesResponse.Body();
        planet.id = 2;
        planet.name = "Laksak A 1";
        planet.type = "Planet";
        planet.subType = "High metal content world";
        planet.atmosphereType = "Carbon dioxide-rich";
        planet.distanceToArrival = 199.0;
        planet.parents = List.of(new BodiesResponse.ParentRef());
        planet.parents.get(0).Star = 1;

        edsm.bodies = List.of(star, planet);

        SystemCache.getInstance().mergeBodiesFromEdsm(state, edsm, true);

        assertEquals(2, state.getBodies().size());
        BodyInfo arrivalStar = state.getBodies().get(0);
        assertTrue(arrivalStar.isEdsmFssBackfill());
        assertTrue(arrivalStar.isJournalScanned());
        BodyInfo firstPlanet = state.getBodies().values().stream()
                .filter(b -> "Laksak A 1".equals(b.getBodyName()))
                .findFirst()
                .orElseThrow();
        assertTrue(firstPlanet.isEdsmFssBackfill());
        assertTrue(firstPlanet.isJournalScanned());
    }

    @Test
    void mergeBodiesFromEdsm_supplementOnlyDoesNotCreateStandaloneRows() {
        SystemState state = new SystemState();
        state.setSystemName("Laksak");
        state.setFssProgress(1.0);

        BodiesResponse edsm = new BodiesResponse();
        edsm.name = "Laksak";
        BodiesResponse.Body star = new BodiesResponse.Body();
        star.id = 1;
        star.name = "Laksak";
        star.type = "Star";
        star.subType = "F (White) Star";
        edsm.bodies = List.of(star);

        SystemCache.getInstance().mergeBodiesFromEdsm(state, edsm, false);

        assertTrue(state.getBodies().isEmpty());
    }
}
