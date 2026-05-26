package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.Test;

class SystemTabTableFilteringTest {

    @Test
    void systemTableBodiesExcludeScanBarycentresButDoNotMutateStateMap() {
        BodyInfo star = body(0, "Test System");
        BodyInfo barycentre = body(2, "Null:2");
        barycentre.setScanBarycentreRow(true);
        BodyInfo planet = body(3, "Test System 1");

        Map<Integer, BodyInfo> bodies = new LinkedHashMap<>();
        bodies.put(Integer.valueOf(0), star);
        bodies.put(Integer.valueOf(2), barycentre);
        bodies.put(Integer.valueOf(3), planet);

        Map<Integer, BodyInfo> tableBodies = SystemTabPanel.systemTableBodiesExcludingBarycentres(bodies);

        assertEquals(2, tableBodies.size());
        assertSame(star, tableBodies.get(Integer.valueOf(0)));
        assertSame(planet, tableBodies.get(Integer.valueOf(3)));
        assertFalse(tableBodies.containsKey(Integer.valueOf(2)));

        assertTrue(bodies.containsKey(Integer.valueOf(2)), "Map geometry should keep barycentre rows in system state");
    }

    private static BodyInfo body(int bodyId, String name) {
        BodyInfo body = new BodyInfo();
        body.setBodyId(bodyId);
        body.setBodyName(name);
        body.setBodyShortName(name);
        return body;
    }
}
