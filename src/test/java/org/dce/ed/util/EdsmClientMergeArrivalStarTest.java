package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.junit.jupiter.api.Test;

class EdsmClientMergeArrivalStarTest {

    @Test
    void mergeBodiesFromEdsm_enrichesJournalStarInsteadOfCreatingEdsmId() {
        SystemState state = new SystemState();
        state.setSystemName("Eol Prou LW-L c8-75");
        BodyInfo journalStar = new BodyInfo();
        journalStar.setBodyId(0);
        journalStar.setBodyName("Eol Prou LW-L c8-75");
        state.getBodies().put(0, journalStar);

        BodiesResponse edsm = new BodiesResponse();
        edsm.name = "Eol Prou LW-L c8-75";
        BodiesResponse.Body star = new BodiesResponse.Body();
        star.id = 9781;
        star.name = "Eol Prou LW-L c8-75";
        star.type = "Star";
        star.subType = "M (Red dwarf) Star";
        edsm.bodies = List.of(star);

        new EdsmClient().mergeBodiesFromEdsm(state, edsm);

        assertNull(state.getBodies().get(9781));
        BodyInfo merged = state.getBodies().get(0);
        assertEquals(0, merged.getBodyId());
        assertEquals("M", merged.getStarType());
    }

    @Test
    void mergeBodiesFromEdsm_planetParentStarRefsUseJournalZero() {
        SystemState state = new SystemState();
        state.setSystemName("Eol Prou LW-L c8-75");
        BodyInfo journalStar = new BodyInfo();
        journalStar.setBodyId(0);
        journalStar.setBodyName("Eol Prou LW-L c8-75");
        state.getBodies().put(0, journalStar);

        BodiesResponse edsm = new BodiesResponse();
        edsm.name = "Eol Prou LW-L c8-75";
        BodiesResponse.Body star = new BodiesResponse.Body();
        star.id = 9781;
        star.name = "Eol Prou LW-L c8-75";
        star.type = "Star";
        star.subType = "M (Red dwarf) Star";

        BodiesResponse.Body planet = new BodiesResponse.Body();
        planet.id = 14767;
        planet.name = "Eol Prou LW-L c8-75 5";
        planet.type = "Planet";
        planet.subType = "High metal content world";
        BodiesResponse.ParentRef parent = new BodiesResponse.ParentRef();
        parent.Star = 9781;
        planet.parents = List.of(parent);

        edsm.bodies = List.of(star, planet);

        new EdsmClient().mergeBodiesFromEdsm(state, edsm);

        BodyInfo body5 = state.getBodies().get(14767);
        assertEquals(List.of("Star:0"), body5.getJournalParentRefs());
        assertEquals(0, body5.getParentStarBodyId());
    }
}
