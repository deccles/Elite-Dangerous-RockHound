package org.dce.ed.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.dce.ed.systemmap.SystemMapFixture;
import org.dce.ed.systemmap.SystemMapFixtureLoader;
import org.dce.ed.util.SystemOrbitGeometry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@code BCD 1} must not be parsed as branch {@code D 1} by trailing designation logic. */
class ScanParentsDesignationTest {

    private static Map<Integer, BodyInfo> eorBodies;

    @BeforeAll
    static void loadEor() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        eorBodies = fixture.toBodies();
    }

    @Test
    void bcd1_resolveParent_usesJournalNull_notStarD() {
        BodyInfo bcd1 = eorBodies.get(Integer.valueOf(36));
        assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                SystemOrbitGeometry.resolveOrbitParentBodyId(bcd1, eorBodies, 36));
    }

    @Test
    void bcd1_misParentedToStarA_recoversNullFromBcdSibling() {
        Map<Integer, BodyInfo> bodies = new HashMap<>(eorBodies);
        BodyInfo bcd1 = bodies.get(Integer.valueOf(36));
        bcd1.setImmediateParentBodyId(1);
        assertEquals(SystemOrbitGeometry.planetBinaryBarycentreMapKey(2),
                SystemOrbitGeometry.resolveOrbitParentBodyId(bcd1, bodies, 36));
    }
}
