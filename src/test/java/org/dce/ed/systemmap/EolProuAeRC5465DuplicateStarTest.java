package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapHierarchyBuilder.Graph;
import org.dce.ed.util.SystemOrbitGeometry;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.journal.ScanBaryCentreRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.model.SystemModel;
import org.junit.jupiter.api.Test;

/**
 * Eol Prou AE-R c5-465 style triple: inner A+B binary at Null:1, outer C at Null:0. Cache/journal id
 * mismatches must not produce duplicate B/C stars or a spurious system-name arrival alongside {@code A}.
 */
class EolProuAeRC5465DuplicateStarTest {

    private static final String SYS = "Eol Prou AE-R c5-465";
    private static final Instant T = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void dedupedJournal_buildsThreeStarsNoDuplicateHierarchyNodes() {
        SystemState state = stateWithDuplicateJournal();
        SystemSession session = SystemSessionFactory.open(state);
        assertTrue(session.hasModel(), "model from deduped journal");

        SystemModel model = session.model();
        long starCount = model.bodies().values().stream()
                .filter(b -> b.kind() == org.dce.systemmodel.model.BodyKind.STAR)
                .count();
        assertEquals(3, starCount, "exactly three stars after dedupe");

        Graph graph = SystemModelHierarchyBuilder.buildForSession(session);
        assertTrue(SystemModelHierarchyBuilder.isUsableHierarchy(graph));

        long graphStars = graph.nodeByKey.values().stream()
                .filter(n -> n.kind == SystemMapHierarchyBuilder.NodeKind.STAR)
                .count();
        assertEquals(3, graphStars, "hierarchy graph lists one node per star");

        BodyInfo starA = bodyInfoFromModel(model, "A");
        assertFalse(SystemOrbitGeometry.isPrimaryStarBodyByName(starA),
                "letter-branch A is not the arrival * alias");
    }

    private static BodyInfo bodyInfoFromModel(SystemModel model, String shortName) {
        return model.bodies().values().stream()
                .filter(b -> b.bodyName() != null && b.bodyName().trim().endsWith(" " + shortName))
                .findFirst()
                .map(b -> {
                    BodyInfo bi = new BodyInfo();
                    bi.setBodyId(b.bodyId());
                    bi.setBodyName(b.bodyName());
                    bi.setBodyShortName(shortName);
                    bi.setStarSystem(SYS);
                    bi.setDistanceLs(b.distanceFromArrivalLs());
                    return bi;
                })
                .orElseThrow();
    }

    private static SystemState stateWithDuplicateJournal() {
        SystemState state = new SystemState();
        state.setSystemName(SYS);
        state.setSystemAddress(424242L);
        state.setJournalEventLog(buildDuplicateJournalLog());
        return state;
    }

    private static List<JournalRecord> buildDuplicateJournalLog() {
        List<JournalRecord> log = new ArrayList<>();
        log.add(new ScanBaryCentreRecord(
                T, 1, SYS + " barycentre 1", List.of(new ParentRef(ParentRef.ParentType.NULL, 0)),
                List.of(), null));
        log.add(star(T, 9781, SYS, "K", 0, ParentRef.ParentType.NULL, 0));
        log.add(star(T, 2, SYS + " A", "K5 VA", 0, ParentRef.ParentType.NULL, 1));
        log.add(star(T, 3, SYS + " B", "K", 2236, ParentRef.ParentType.NULL, 1));
        log.add(star(T, 9, SYS + " B", "K5 VA", 2236, ParentRef.ParentType.NULL, 1));
        log.add(star(T, 4, SYS + " C", "M", 158267, ParentRef.ParentType.NULL, 0));
        log.add(star(T, 10, SYS + " C", "M0 VA", 158267, ParentRef.ParentType.NULL, 0));
        return log;
    }

    private static ScanRecord star(Instant t, int id, String name, String subType, double distLs,
            ParentRef.ParentType parentType, int parentId) {
        return new ScanRecord(
                t, id, name, "Star", subType, distLs,
                0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new ParentRef(parentType, parentId)), null, true, false);
    }
}
