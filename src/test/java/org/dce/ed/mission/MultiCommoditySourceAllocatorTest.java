package org.dce.ed.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MultiCommoditySourceAllocatorTest {
    @Test
    void maximizesFullySatisfiedMissionCountWithSharedCargoAndStock() {
        var needs = List.of(need(1, "Gold", 60, "2026-08-15T00:00:00Z"),
                need(2, "Gold", 40, "2026-08-16T00:00:00Z"),
                need(3, "Gold", 30, "2026-08-17T00:00:00Z"));

        var result = MultiCommoditySourceAllocator.allocate(needs, Map.of("gold", 20), Map.of("gold", 50));

        assertEquals(List.of(2L, 3L), result.missionIds());
        assertEquals(50, result.purchaseTons());
    }

    @Test
    void equalMissionCountPrefersEarliestExpiryThenLeastPurchase() {
        var needs = List.of(need(1, "Gold", 50, "2026-08-14T00:00:00Z"),
                need(2, "Gold", 20, "2026-08-15T00:00:00Z"));

        var earliest = MultiCommoditySourceAllocator.allocate(needs, Map.of(), Map.of("gold", 50));
        assertEquals(List.of(1L), earliest.missionIds());

        var leastPurchase = MultiCommoditySourceAllocator.allocate(
                List.of(need(3, "Gold", 40, "2026-08-14T00:00:00Z"),
                        need(4, "Gold", 20, "2026-08-14T00:00:00Z")),
                Map.of("gold", 10), Map.of("gold", 40));
        assertEquals(List.of(4L), leastPurchase.missionIds());
        assertEquals(10, leastPurchase.purchaseTons());
    }

    @Test
    void allocatesDifferentCommoditiesIndependently() {
        var result = MultiCommoditySourceAllocator.allocate(
                List.of(need(1, "Gold", 20, null), need(2, "Silver", 30, null)),
                Map.of("gold", 5), Map.of("gold", 15, "silver", 30));

        assertEquals(List.of(1L, 2L), result.missionIds());
        assertEquals(45, result.purchaseTons());
    }

    private static MultiCommodityMissionNeed need(long id, String commodity, int tons, String expiry) {
        return new MultiCommodityMissionNeed(id, commodity, tons,
                expiry == null ? null : Instant.parse(expiry));
    }
}
