package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.StartJumpEvent;
import org.dce.ed.logreader.event.StatusEvent;
import org.junit.jupiter.api.Test;

class AutoTabJumpLogicTest {

    private static final String ISO_TS = "2026-02-15T22:47:39Z";
    private final EliteLogParser parser = new EliteLogParser();

    @Test
    void noActivity_returnsNone() {
        assertEquals(AutoTabJumpLogic.JumpKind.NONE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, false, false, null, null));
    }

    private static final long FSD_CHARGING = 0x00020000L;
    private static final long DOCKED = 0x00000001L;

    @Test
    void ownedCarrierPendingWithFsdChargingAboard_returnsFleetCarrier() {
        StatusEvent charging = parseStatusWithFlags(FSD_CHARGING);
        assertEquals(AutoTabJumpLogic.JumpKind.FLEET_CARRIER,
                AutoTabJumpLogic.classifyForAutoTabSwitch(true, false, true, charging, null));
    }

    @Test
    void ownedCarrierPendingWithFsdChargingOffCarrier_returnsShipHyperspace() {
        StatusEvent charging = parseStatusWithFlags(FSD_CHARGING);
        assertEquals(AutoTabJumpLogic.JumpKind.SHIP_HYPERSPACE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(true, false, false, charging, null));
    }

    @Test
    void countdownActiveWithFsdChargingAboard_returnsFleetCarrier() {
        StatusEvent charging = parseStatusWithFlags(FSD_CHARGING);
        assertEquals(AutoTabJumpLogic.JumpKind.FLEET_CARRIER,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, true, true, charging, null));
    }

    @Test
    void countdownActiveWithFsdChargingOffCarrier_returnsShipHyperspace() {
        StatusEvent charging = parseStatusWithFlags(FSD_CHARGING);
        assertEquals(AutoTabJumpLogic.JumpKind.SHIP_HYPERSPACE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, true, false, charging, null));
    }

    @Test
    void undockedFsdChargingWithoutCarrierContext_returnsShipHyperspace() {
        StatusEvent charging = parseStatusWithFlags(FSD_CHARGING);
        assertEquals(AutoTabJumpLogic.JumpKind.SHIP_HYPERSPACE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, false, false, charging, null));
    }

    @Test
    void dockedFsdChargingWithoutCarrierPending_returnsFleetCarrier() {
        StatusEvent dockedCharging = parseStatusWithFlags(FSD_CHARGING | DOCKED);
        assertEquals(AutoTabJumpLogic.JumpKind.FLEET_CARRIER,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, false, false, dockedCharging, null));
    }

    @Test
    void startJumpHyperspaceUndocked_returnsShipHyperspace() {
        StartJumpEvent sj = parseStartJump("Hyperspace");
        assertEquals(AutoTabJumpLogic.JumpKind.SHIP_HYPERSPACE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(false, false, false, null, sj));
    }

    @Test
    void startJumpHyperspaceWithCarrierPendingOffCarrier_returnsShipHyperspace() {
        StartJumpEvent sj = parseStartJump("Hyperspace");
        assertEquals(AutoTabJumpLogic.JumpKind.SHIP_HYPERSPACE,
                AutoTabJumpLogic.classifyForAutoTabSwitch(true, false, false, null, sj));
    }

    @Test
    void isHyperspaceJumpActivity_fsdJumpFlag() {
        StatusEvent inJump = parseStatusWithFlags(0x40000000L);
        assertTrue(AutoTabJumpLogic.isHyperspaceJumpActivity(inJump, null));
    }

    @Test
    void isHyperspaceJumpActivity_supercruiseStartJump_false() {
        StartJumpEvent sj = parseStartJump("Supercruise");
        assertFalse(AutoTabJumpLogic.isHyperspaceJumpActivity(null, sj));
    }

    private StatusEvent parseStatusWithFlags(long flags) {
        String json = "{\"event\":\"Status\",\"timestamp\":\"" + ISO_TS + "\",\"Flags\":" + flags + ",\"Flags2\":0}";
        return (StatusEvent) parser.parseRecord(json);
    }

    private StartJumpEvent parseStartJump(String jumpType) {
        String json = "{\"event\":\"StartJump\",\"timestamp\":\"" + ISO_TS + "\",\"JumpType\":\"" + jumpType
                + "\",\"StarSystem\":\"Test\",\"SystemAddress\":1}";
        return (StartJumpEvent) parser.parseRecord(json);
    }
}
