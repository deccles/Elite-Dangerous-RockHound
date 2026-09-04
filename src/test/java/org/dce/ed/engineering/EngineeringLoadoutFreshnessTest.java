package org.dce.ed.engineering;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.ModuleRetrieveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EngineeringLoadoutFreshnessTest {

    @BeforeEach
    @AfterEach
    void reset() {
        EngineeringLoadoutFreshness.resetForTests();
    }

    @Test
    void warningStaysDisabled() {
        assertFalse(EngineeringLoadoutFreshness.isAwaitingLoadout());
        EngineeringLoadoutFreshness.markAwaitingLoadout();
        assertFalse(EngineeringLoadoutFreshness.isAwaitingLoadout());
        ModuleRetrieveEvent stock = (ModuleRetrieveEvent) new EliteLogParser().parseRecord("""
                {"timestamp":"2026-09-04T06:04:08Z","event":"ModuleRetrieve","Slot":"LifeSupport",
                 "Ship":"federation_corvette","ShipID":23,
                 "RetrievedItem":"$int_lifesupport_size5_class2_name;"}
                """);
        EngineeringLoadoutFreshness.onModuleRetrieve(stock);
        EngineeringLoadoutFreshness.onModuleStore();
        assertFalse(EngineeringLoadoutFreshness.isAwaitingLoadout());
        EngineeringLoadoutFreshness.clear();
        assertFalse(EngineeringLoadoutFreshness.isAwaitingLoadout());
    }
}
