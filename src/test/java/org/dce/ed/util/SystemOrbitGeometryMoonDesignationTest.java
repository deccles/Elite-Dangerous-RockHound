package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

/** Moon vs planet-binary-major designation heuristics (no per-system rules). */
class SystemOrbitGeometryMoonDesignationTest {

    @Test
    void hasEliteMoonDesignation_branchMajorMoon() {
        assertTrue(SystemOrbitGeometry.hasEliteMoonDesignationInName("Eol Prou RN-I c10-276 A 3 e"));
        assertTrue(SystemOrbitGeometry.hasEliteMoonDesignationInName("A 3 a"));
    }

    @Test
    void hasEliteMoonDesignation_planetBinaryMajorOnly() {
        assertTrue(SystemOrbitGeometry.hasEliteMoonDesignationInName("1 b"));
        assertFalse(SystemOrbitGeometry.hasEliteMoonDesignationInName("BCD 2"));
    }

    @Test
    void isMoonSatelliteBody_coOrbitNullParent_stillMoonForA3e() {
        BodyInfo moon = new BodyInfo();
        moon.setBodyShortName("A 3 e");
        moon.setImmediateParentBodyId(15);
        java.util.Map<Integer, BodyInfo> bodies = new java.util.HashMap<>();
        BodyInfo scanRow = new BodyInfo();
        scanRow.setScanBarycentreRow(true);
        bodies.put(Integer.valueOf(15), scanRow);
        assertTrue(SystemOrbitGeometry.isMoonSatelliteBody(moon, bodies));
    }

    @Test
    void isMoonSatelliteBody_coOrbitNullParent_notMoonFor1b() {
        BodyInfo major = new BodyInfo();
        major.setBodyShortName("1 b");
        major.setImmediateParentBodyId(12);
        java.util.Map<Integer, BodyInfo> bodies = new java.util.HashMap<>();
        BodyInfo scanRow = new BodyInfo();
        scanRow.setScanBarycentreRow(true);
        bodies.put(Integer.valueOf(12), scanRow);
        assertFalse(SystemOrbitGeometry.isMoonSatelliteBody(major, bodies));
    }
}
