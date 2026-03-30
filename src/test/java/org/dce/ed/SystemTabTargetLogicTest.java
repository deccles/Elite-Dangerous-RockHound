package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.StatusEvent;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SystemTabTargetLogic}: destination-is-other-system and effective dest name/body.
 */
class SystemTabTargetLogicTest {

    private static final String ISO_TS = "2026-02-15T22:47:39Z";
    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void isDestinationOtherSystem_nullEvent_returnsFalse() {
        assertFalse(SystemTabTargetLogic.isDestinationOtherSystem(null, 100L));
    }

    @Test
    void isDestinationOtherSystem_sameSystem_returnsFalse() {
        StatusEvent e = parseStatusWithDestination(100L, 7, "Sol");
        assertFalse(SystemTabTargetLogic.isDestinationOtherSystem(e, 100L));
    }

    @Test
    void isDestinationOtherSystem_differentSystem_returnsTrue() {
        StatusEvent e = parseStatusWithDestination(200L, 0, "Alpha Centauri");
        assertTrue(SystemTabTargetLogic.isDestinationOtherSystem(e, 100L));
    }

    @Test
    void isDestinationOtherSystem_currentAddressZero_returnsFalse() {
        StatusEvent e = parseStatusWithDestination(200L, 0, "Other");
        assertFalse(SystemTabTargetLogic.isDestinationOtherSystem(e, 0L));
    }

    @Test
    void effectiveDestBody_otherSystem_returnsNull() {
        StatusEvent e = parseStatusWithDestination(200L, 7, "Other");
        assertNull(SystemTabTargetLogic.effectiveDestBody(e, 100L));
    }

    @Test
    void effectiveDestBody_sameSystem_returnsBodyId() {
        StatusEvent e = parseStatusWithDestination(100L, 7, "Earth");
        assertEquals(Integer.valueOf(7), SystemTabTargetLogic.effectiveDestBody(e, 100L));
    }

    @Test
    void effectiveDestBody_bodyZero_returnsNull() {
        StatusEvent e = parseStatusWithDestination(100L, 0, "Sol");
        assertNull(SystemTabTargetLogic.effectiveDestBody(e, 100L));
    }

    @Test
    void effectiveDestName_otherSystem_returnsNull() {
        StatusEvent e = parseStatusWithDestination(200L, 0, "Alpha Centauri");
        assertNull(SystemTabTargetLogic.effectiveDestName(e, 100L));
    }

    @Test
    void effectiveDestName_sameSystem_returnsTrimmedName() {
        StatusEvent e = parseStatusWithDestination(100L, 7, " Earth ");
        assertEquals("Earth", SystemTabTargetLogic.effectiveDestName(e, 100L));
    }

    @Test
    void preferSystemTab_jumpTargetMatchesWithBody_true() {
        StatusEvent e = parseStatusWithDestination(100L, 7, "Moon");
        assertTrue(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, 100L, 50L));
    }

    @Test
    void preferSystemTab_jumpTargetMismatchedWithBody_false() {
        StatusEvent e = parseStatusWithDestination(200L, 7, "Moon");
        assertFalse(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, 100L, 50L));
    }

    @Test
    void preferSystemTab_noJumpTarget_hyperspaceBody_true() {
        StatusEvent e = parseStatusWithDestination(100L, 7, "Moon");
        assertTrue(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, null, 50L));
    }

    @Test
    void preferSystemTab_noJumpTarget_currentUnknown_false() {
        StatusEvent e = parseStatusWithDestination(100L, 7, "Moon");
        assertFalse(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, null, 0L));
    }

    @Test
    void preferSystemTab_sameSystemBody_true() {
        StatusEvent e = parseStatusWithDestination(50L, 3, "Body");
        assertTrue(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, null, 50L));
    }

    @Test
    void preferSystemTab_systemOnlyBodyZero_false() {
        StatusEvent e = parseStatusWithDestination(100L, 0, "System");
        assertFalse(SystemTabTargetLogic.preferSystemTabForFsdTarget(e, 100L, 50L));
    }

    private StatusEvent parseStatusWithDestination(Long destSystem, int destBody, String destName) {
        StringBuilder json = new StringBuilder("{\"event\":\"Status\",\"timestamp\":\"" + ISO_TS + "\",\"Flags\":0,\"Flags2\":0");
        if (destSystem != null || destBody != 0 || destName != null) {
            json.append(",\"Destination\":{");
            boolean first = true;
            if (destSystem != null) {
                json.append("\"System\":").append(destSystem);
                first = false;
            }
            if (destBody != 0 || !first) {
                if (!first) json.append(",");
                json.append("\"Body\":").append(destBody);
                first = false;
            }
            if (destName != null) {
                if (!first) json.append(",");
                json.append("\"Name\":\"").append(destName.replace("\"", "\\\"")).append("\"");
            }
            json.append("}");
        }
        json.append("}");
        return (StatusEvent) parser.parseRecord(json.toString());
    }
}
