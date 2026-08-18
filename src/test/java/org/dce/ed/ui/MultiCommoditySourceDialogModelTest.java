package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.List;
import java.util.stream.IntStream;

import org.dce.ed.mission.CommoditySourceChoice;
import org.dce.ed.mission.MultiCommodityAllocation;
import org.dce.ed.mission.MultiCommodityCoverage;
import org.dce.ed.mission.MultiCommodityStationAssessment;
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
        MissionRecord mining = mission(4, "Bromellite", 12, 0);
        mining.setName("Mission_Mining_Boom");

        var needs = MultiCommoditySourceDialog.buildNeeds(List.of(eligible, assigned, complete, mining));

        assertEquals(2, needs.size());
        assertEquals(1L, needs.get(0).missionId());
        assertEquals(40, needs.get(0).tons());
        assertEquals(4L, needs.get(1).missionId());
    }

    @Test
    void sourceResultsUseSeparateCoverageAvailableAndMissingColumns() {
        var model = MultiCommoditySourceDialog.createResultsModel();
        assertEquals(List.of("Station", "System", "Type", "Ly", "Arrival Ls", "Coverage",
                "Available", "Missing", "Buy", "Updated"),
                IntStream.range(0, model.getColumnCount()).mapToObj(model::getColumnName).toList());
    }

    @Test
    void commodityLinesClearlyDistinguishReadyPartialAndMissing() {
        MultiCommodityStationAssessment row = assessment(List.of(
                new MultiCommodityCoverage("Gold", 40, 60, 100, MultiCommodityCoverage.Status.COMPLETE),
                new MultiCommodityCoverage("Silver", 10, 20, 50, MultiCommodityCoverage.Status.PARTIAL),
                new MultiCommodityCoverage("Water", 0, 0, 25, MultiCommodityCoverage.Status.MISSING)));

        List<String> available = MultiCommoditySourceDialog.availableLines(row);
        List<String> missing = MultiCommoditySourceDialog.missingLines(row);

        assertEquals(2, available.size());
        assertTrue(available.get(0).contains("Gold"));
        assertTrue(available.get(0).contains("ready"));
        assertTrue(available.get(1).contains("20 here + 10 aboard"));
        assertTrue(available.get(1).contains("20 short"));
        assertEquals(List.of("Water · 25 short"), missing);
    }

    private static MultiCommodityStationAssessment assessment(List<MultiCommodityCoverage> coverages) {
        CommoditySourceChoice station = new CommoditySourceChoice("Sol", "Galileo", 0.0, 100.0,
                3, 100, "2026-01-01T00:00:00Z", "Coriolis", 3, null);
        return new MultiCommodityStationAssessment(station,
                new MultiCommodityAllocation(List.of(), 0, Map.of()), Map.of(), coverages, null);
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
