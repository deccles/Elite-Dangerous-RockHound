package org.dce.ed.mission;

import java.util.List;
import java.util.Map;

/** Missions fully satisfiable at one station after shared hold cargo is applied once. */
public record MultiCommodityAllocation(List<Long> missionIds, int purchaseTons,
        Map<String, Integer> purchaseByCommodity) { }
