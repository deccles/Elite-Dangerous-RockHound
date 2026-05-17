package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.ExplorationBodyCredits.SystemMapDotKind;
import org.junit.jupiter.api.Test;

class ExplorationBodyCreditsMapDotTest {

    @Test
    void earthlikeBody_fromPlanetClass() {
        BodyInfo b = new BodyInfo();
        b.setPlanetClass("Earthlike body");
        assertEquals(SystemMapDotKind.EARTH_LIKE, ExplorationBodyCredits.systemMapDotKind(b, false));
    }

    @Test
    void waterWorld_fromPlanetClass() {
        BodyInfo b = new BodyInfo();
        b.setPlanetClass("Water world");
        assertEquals(SystemMapDotKind.WATER_LIKE, ExplorationBodyCredits.systemMapDotKind(b, false));
    }

    @Test
    void waterWorld_fromAtmoOrTypeWhenPlanetClassMissing() {
        BodyInfo b = new BodyInfo();
        b.setAtmoOrType("Water world");
        assertEquals(SystemMapDotKind.WATER_LIKE, ExplorationBodyCredits.systemMapDotKind(b, false));
    }

    @Test
    void starsAreNeverColored() {
        BodyInfo b = new BodyInfo();
        b.setPlanetClass("Water world");
        assertEquals(SystemMapDotKind.DEFAULT, ExplorationBodyCredits.systemMapDotKind(b, true));
    }
}
