package org.dce.ed.mission;

import java.util.Map;

/** Combined commodity coverage for one market. */
public record MultiCommodityStationAssessment(CommoditySourceChoice station,
        MultiCommodityAllocation allocation, Map<String, Integer> stockByCommodity,
        String commoditiesText, String oldestUpdatedAt) { }
