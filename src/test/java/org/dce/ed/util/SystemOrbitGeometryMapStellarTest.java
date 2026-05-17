package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

/** Regression: FSS / journal rows can populate Sudarsky gas giants via {@link BodyInfo#getStarType()} before planet class. */
class SystemOrbitGeometryMapStellarTest {

    @Test
    void isMapStellarBody_falseWhenOnlySudarskyStarType() {
        BodyInfo giant = new BodyInfo();
        giant.setStarSystem("Byua Aim TT-X c15-44");
        giant.setBodyName("Byua Aim TT-X c15-44 4");
        giant.setBodyShortName("4");
        giant.setStarType("Sudarsky class I gas giant");
        giant.setDistanceLs(3941);
        assertFalse(SystemOrbitGeometry.isMapStellarBody(giant));
    }

    @Test
    void isMapStellarBody_falseWhenGasGiantPhraseInStarType() {
        BodyInfo giant = new BodyInfo();
        giant.setStarSystem("Test");
        giant.setStarType("Gas giant"); // abbreviated / edge journal text
        assertFalse(SystemOrbitGeometry.isMapStellarBody(giant));
    }

    @Test
    void isMapStellarBody_trueForPrimaryNamedLikeSystem_withoutStarType() {
        BodyInfo primary = new BodyInfo();
        primary.setStarSystem("Byua Aim TT-X c15-44");
        primary.setBodyShortName("Byua Aim TT-X c15-44");
        assertTrue(SystemOrbitGeometry.isMapStellarBody(primary));
    }

    @Test
    void isMoonSatelliteBody_trueForCompactAndSpacedDesignations() {
        BodyInfo a = new BodyInfo();
        a.setBodyShortName("2a");
        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(a));
        BodyInfo b = new BodyInfo();
        b.setBodyShortName("2 a");
        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(b));
    }

    @Test
    void isMoonSatelliteBody_falseForMajorIndexOnly() {
        BodyInfo g = new BodyInfo();
        g.setBodyShortName("2");
        assertFalse(SystemOrbitGeometry.isMoonSatelliteBody(g));
    }
}
