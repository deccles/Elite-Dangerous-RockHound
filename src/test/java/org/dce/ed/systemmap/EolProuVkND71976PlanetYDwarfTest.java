package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.testutil.OrbitGeometryTestSupport;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou VK-N d7-1976: Sudarsky IV gas giant 8 and Y dwarf 9 share {@code Null:35} — both must sit on the
 * mutual-orbit ring, not only the planet.
 */
class EolProuVkND71976PlanetYDwarfTest {

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static int baryKey35;

    @BeforeAll
    static void loadFixture() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eol-prou-vk-n-d7-1976-planet-y-dwarf.json");
        baryKey35 = SystemOrbitGeometry.planetBinaryBarycentreMapKey(35);
    }

    @BeforeEach
    void freshBodies() {
        bodies = fixture.toBodies();
    }

    @Test
    @DisplayName("Null:35 is shared planet+stellar hub, not co-orbit planet pair alone")
    void null35_mixedPlanetStellarHub() {
        assertTrue(SystemOrbitGeometry.isSharedNullBarycentreId(35, bodies));
        assertTrue(SystemOrbitGeometry.isPlanetBinaryNullParentId(35, bodies));
        assertTrue(!SystemOrbitGeometry.isCoOrbitMajorSharedNullHub(35, bodies),
                "Y dwarf 9 is stellar — only gas giant 8 counts as co-orbit major");
    }

    @Test
    @DisplayName("Bodies 8 and 9 align on Null:35 mutual ring")
    void bodies8and9_onMutualRing() {
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);

        assertNotNull(model.positionsMetres().get(Integer.valueOf(baryKey35)),
                "Null:35 barycentre positioned");
        assertNotNull(OrbitGeometryTestSupport.findPlanetBinaryMutualRing(model, 35),
                "mutual orbit ring at Null:35");

        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "8", 35, 2.0);
        OrbitGeometryTestSupport.assertBodyOnMutualOrbitRing(model, bodies, "9", 35, 2.0);
    }
}
