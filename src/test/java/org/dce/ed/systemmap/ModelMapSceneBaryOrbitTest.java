package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.systemmodel.model.HierarchyKeys;
import org.junit.jupiter.api.Test;

class ModelMapSceneBaryOrbitTest {

    @Test
    void baryHubOrbit_isAssociated() {
        int hub = HierarchyKeys.baryMapKey(32);
        assertTrue(ModelMapScene.isBarycentreAssociatedOrbit(hub, 0));
    }

    @Test
    void bodyOrbitingBary_isAssociated() {
        int hub = HierarchyKeys.baryMapKey(20);
        assertTrue(ModelMapScene.isBarycentreAssociatedOrbit(21, hub));
    }

    @Test
    void regularPlanetOrbit_isNotAssociated() {
        assertFalse(ModelMapScene.isBarycentreAssociatedOrbit(5, 0));
    }
}
