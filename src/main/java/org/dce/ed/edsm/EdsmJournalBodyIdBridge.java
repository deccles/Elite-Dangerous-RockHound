package org.dce.ed.edsm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps EDSM internal body ids to Elite journal {@code BodyID} values. EDSM uses its database primary key per body;
 * the journal always uses {@code 0} for the arrival star (system name with no numeric suffix).
 */
public final class EdsmJournalBodyIdBridge {

    private EdsmJournalBodyIdBridge() {
    }

    public static boolean isArrivalStar(BodiesResponse.Body remote, String systemName) {
        if (remote == null || systemName == null || remote.type == null || remote.name == null) {
            return false;
        }
        if (!"Star".equalsIgnoreCase(remote.type)) {
            return false;
        }
        return remote.name.trim().equalsIgnoreCase(systemName.trim());
    }

    /**
     * Journal {@code BodyID} to use when merging this EDSM row into {@link org.dce.ed.state.SystemState}.
     */
    public static Integer resolveJournalBodyId(BodiesResponse.Body remote, String systemName) {
        if (remote == null) {
            return null;
        }
        Integer edsmId = safeToInt(remote.id);
        if (edsmId == null) {
            return null;
        }
        if (isArrivalStar(remote, systemName)) {
            return 0;
        }
        return edsmId;
    }

    /**
     * EDSM star parent ids that refer to the arrival star should use journal {@code BodyID 0} in parent refs.
     */
    public static Map<Integer, Integer> buildEdsmToJournalIdMap(String systemName, List<BodiesResponse.Body> bodies) {
        Map<Integer, Integer> map = new HashMap<>();
        if (systemName == null || systemName.isBlank() || bodies == null) {
            return map;
        }
        for (BodiesResponse.Body b : bodies) {
            if (!isArrivalStar(b, systemName)) {
                continue;
            }
            Integer edsmId = safeToInt(b.id);
            if (edsmId != null && edsmId.intValue() != 0) {
                map.put(edsmId, 0);
            }
        }
        return map;
    }

    public static int remapStarParentId(int edsmStarId, Map<Integer, Integer> edsmToJournal) {
        if (edsmToJournal != null) {
            Integer mapped = edsmToJournal.get(edsmStarId);
            if (mapped != null) {
                return mapped.intValue();
            }
        }
        return edsmStarId;
    }

    static Integer safeToInt(long v) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            return null;
        }
        return (int) v;
    }
}
