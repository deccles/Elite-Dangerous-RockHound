package org.dce.ed.mission;

/** One nearby market that exports (sells) a mission commodity. */
public record CommoditySourceChoice(
        String system,
        String station,
        Double systemDistanceLy,
        Double arrivalDistanceLs,
        Integer price,
        Integer supply,
        String updatedAt) {
}
