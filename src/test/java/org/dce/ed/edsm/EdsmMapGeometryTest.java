package org.dce.ed.edsm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EdsmMapGeometryTest {

    @Test
    void acceptsCompleteOrbitalGeometry() {
        assertTrue(EdsmMapGeometry.isComplete(system(orbit(0.12))));
    }

    @Test
    void rejectsBodyWithMissingEllipseGeometry() {
        assertFalse(EdsmMapGeometry.isComplete(system(orbit(null))));
    }

    @Test
    void rejectsBodyWithNoParent() {
        BodiesResponse.Body body = orbit(0.12);
        body.parents = List.of();
        assertFalse(EdsmMapGeometry.isComplete(system(body)));
    }

    private static BodiesResponse system(BodiesResponse.Body orbit) {
        BodiesResponse response = new BodiesResponse();
        BodiesResponse.Body star = new BodiesResponse.Body();
        star.id = 0;
        star.name = "Test";
        star.type = "Star";
        star.isMainStar = true;
        response.bodies = List.of(star, orbit);
        return response;
    }

    private static BodiesResponse.Body orbit(Double eccentricity) {
        BodiesResponse.Body body = new BodiesResponse.Body();
        body.id = 1;
        body.name = "Test 1";
        body.type = "Planet";
        body.semiMajorAxis = 1.0;
        body.orbitalEccentricity = eccentricity;
        body.orbitalInclination = 2.0;
        body.argOfPeriapsis = 30.0;
        BodiesResponse.ParentRef parent = new BodiesResponse.ParentRef();
        parent.Star = 0;
        body.parents = List.of(parent);
        return body;
    }
}
