package org.dce.ed.mission;

import java.util.Map;
import java.util.List;

/** Combined commodity coverage for one market. */
public record MultiCommodityStationAssessment(CommoditySourceChoice station,
        MultiCommodityAllocation allocation, Map<String, Integer> stockByCommodity,
        List<MultiCommodityCoverage> coverages, String oldestUpdatedAt) {
    public int completeCommodityCount() {
        return count(MultiCommodityCoverage.Status.COMPLETE);
    }

    public int partialCommodityCount() {
        return count(MultiCommodityCoverage.Status.PARTIAL);
    }

    public int missingCommodityCount() {
        return count(MultiCommodityCoverage.Status.MISSING);
    }

    private int count(MultiCommodityCoverage.Status status) {
        return (int) coverages.stream().filter(c -> c.status() == status).count();
    }
}
