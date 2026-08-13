package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.dce.ed.mission.MissionRecord;
import org.junit.jupiter.api.Test;

class MultiCommoditySourceDialogModelTest {
    @Test
    void needsIncludeOnlyOutstandingUnassignedSelfSourcedMissions() {
        MissionRecord eligible = mission(1, "Gold", 50, 10);
        MissionRecord assigned = mission(2, "Silver", 30, 0);
        assigned.setSourcedFromSystem("Sol");
        assigned.setSourcedFromStation("Galileo");
        MissionRecord complete = mission(3, "Gold", 20, 20);

        var needs = MultiCommoditySourceDialog.buildNeeds(List.of(eligible, assigned, complete));

        assertEquals(1, needs.size());
        assertEquals(1L, needs.get(0).missionId());
        assertEquals(40, needs.get(0).tons());
    }

    private static MissionRecord mission(long id, String commodity, int required, int delivered) {
        MissionRecord r = new MissionRecord(id);
        r.setName("Mission_Collect_Industrial");
        r.setCommodityLocalised(commodity);
        r.setCountRequired(required);
        r.setItemsDelivered(delivered);
        return r;
    }
}
