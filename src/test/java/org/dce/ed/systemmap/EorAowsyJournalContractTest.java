package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Machine-readable journal contract for Eor Aowsy RI-K c8-3670 ({@code eor-aowsy-ri-k-c8-3670-expected-tree.json}).
 */
class EorAowsyJournalContractTest {

    private static final String SYSTEM = "Eor Aowsy RI-K c8-3670";
    private static final String EXPECTED_TREE_RESOURCE = "eor-aowsy-ri-k-c8-3670-expected-tree.json";
    private static final String FIXTURE_RESOURCE = "eor-aowsy-ri-k-c8-3670.json";

    private static SystemMapExpectedTree expectedTree;
    private static SystemMapFixture fixture;

    @BeforeAll
    static void loadArtifacts() throws IOException {
        expectedTree = SystemMapExpectedTreeLoader.loadClasspath(EXPECTED_TREE_RESOURCE);
        fixture = SystemMapFixtureLoader.loadClasspath(FIXTURE_RESOURCE);
    }

    @Test
    void committedFixture_matchesExpectedTreeParents() {
        assertEquals(SYSTEM, expectedTree.systemName);
        assertEquals(Long.valueOf(1008877717987402L), expectedTree.systemAddress);
        assertNotNull(expectedTree.bodies);
        assertEquals(34, expectedTree.bodies.size(), "map body contract count");

        Set<Integer> baryIds = new HashSet<>();
        for (Integer id : expectedTree.scanBarycentreIds) {
            baryIds.add(id);
        }
        for (SystemMapExpectedTree.BodyEntry entry : expectedTree.bodies) {
            BodyInfo fromFixture = fixture.toBodies().get(Integer.valueOf(entry.id));
            assertNotNull(fromFixture, "fixture missing body id " + entry.id + " (" + entry.shortName + ")");
            assertEquals(entry.shortName, fromFixture.getShortName());
            if (entry.parentIsBarycentre != null && entry.parentIsBarycentre.booleanValue()) {
                assertEquals(0, fromFixture.getImmediateParentBodyId());
            } else if (entry.immediateParentBodyId != null) {
                assertEquals(entry.immediateParentBodyId.intValue(), fromFixture.getImmediateParentBodyId(),
                        entry.shortName);
            }
        }
        for (Integer baryId : baryIds) {
            BodyInfo bary = fixture.toBodies().get(baryId);
            assertNotNull(bary, "ScanBaryCentre row " + baryId);
            assertTrue(bary.isScanBarycentreRow());
        }
    }
}
