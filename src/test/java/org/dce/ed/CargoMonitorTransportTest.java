package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class CargoMonitorTransportTest {
    @Test
    void reportsTotalAndMissionSpecificCargoForTransportPlanning() {
        var cargo = JsonParser.parseString("""
                {"Inventory":[
                  {"Name":"gold","Name_Localised":"Gold","Count":20,"MissionID":7},
                  {"Name":"silver","Name_Localised":"Silver","Count":5},
                  {"Name":"drones","Count":3}
                ]}
                """).getAsJsonObject();

        assertEquals(28, CargoMonitor.totalCargoTons(cargo));
        assertEquals(20, CargoMonitor.countMissionCargoTons(cargo, 7L));
        assertEquals(0, CargoMonitor.countMissionCargoTons(cargo, 8L));
    }
}
