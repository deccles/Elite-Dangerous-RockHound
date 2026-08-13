package org.dce.ed.mission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chooses the largest fully satisfiable mission set for a station. */
public final class MultiCommoditySourceAllocator {
    private MultiCommoditySourceAllocator() { }

    public static MultiCommodityAllocation allocate(List<MultiCommodityMissionNeed> needs,
            Map<String, Integer> inHold, Map<String, Integer> stationStock) {
        Map<String, List<MultiCommodityMissionNeed>> groups = new LinkedHashMap<>();
        if (needs != null) for (MultiCommodityMissionNeed need : needs) {
            if (need != null && need.tons() > 0) groups.computeIfAbsent(key(need.commodity()), k -> new ArrayList<>()).add(need);
        }
        List<Long> ids = new ArrayList<>();
        Map<String, Integer> purchases = new LinkedHashMap<>();
        int totalPurchase = 0;
        for (Map.Entry<String, List<MultiCommodityMissionNeed>> entry : groups.entrySet()) {
            String commodity = entry.getKey();
            int hold = value(inHold, commodity);
            int stock = value(stationStock, commodity);
            Selection best = bestSelection(entry.getValue(), hold + stock, hold);
            for (MultiCommodityMissionNeed need : best.needs) ids.add(need.missionId());
            int purchase = Math.max(0, best.tons - hold);
            if (purchase > 0) purchases.put(commodity, purchase);
            totalPurchase += purchase;
        }
        return new MultiCommodityAllocation(List.copyOf(ids), totalPurchase, Map.copyOf(purchases));
    }

    private static Selection bestSelection(List<MultiCommodityMissionNeed> needs, int capacity, int hold) {
        Map<Integer, Selection> byTons = new LinkedHashMap<>();
        byTons.put(0, new Selection(List.of(), 0));
        for (MultiCommodityMissionNeed need : needs) {
            Map<Integer, Selection> next = new LinkedHashMap<>(byTons);
            for (Selection selected : byTons.values()) {
                int tons = selected.tons + need.tons();
                if (tons > capacity) continue;
                List<MultiCommodityMissionNeed> chosen = new ArrayList<>(selected.needs);
                chosen.add(need);
                Selection candidate = new Selection(List.copyOf(chosen), tons);
                Selection prior = next.get(tons);
                if (prior == null || better(candidate, prior, hold)) next.put(tons, candidate);
            }
            byTons = next;
        }
        Selection best = byTons.get(0);
        for (Selection candidate : byTons.values()) if (better(candidate, best, hold)) best = candidate;
        return best;
    }

    private static boolean better(Selection a, Selection b, int hold) {
        if (a.needs.size() != b.needs.size()) return a.needs.size() > b.needs.size();
        List<Instant> ae = expiries(a.needs);
        List<Instant> be = expiries(b.needs);
        for (int i = 0; i < ae.size(); i++) {
            int compared = Comparator.nullsLast(Instant::compareTo).compare(ae.get(i), be.get(i));
            if (compared != 0) return compared < 0;
        }
        int ap = Math.max(0, a.tons - hold);
        int bp = Math.max(0, b.tons - hold);
        if (ap != bp) return ap < bp;
        return missionIds(a.needs).compareTo(missionIds(b.needs)) < 0;
    }

    private static List<Instant> expiries(List<MultiCommodityMissionNeed> needs) {
        List<Instant> out = needs.stream().map(MultiCommodityMissionNeed::expiry).sorted(Comparator.nullsLast(Instant::compareTo)).toList();
        return out;
    }

    private static String missionIds(List<MultiCommodityMissionNeed> needs) {
        return needs.stream().map(n -> Long.toString(n.missionId())).sorted().reduce("", (a, b) -> a + "," + b);
    }

    private static int value(Map<String, Integer> values, String key) {
        if (values == null) return 0;
        Integer exact = values.get(key);
        if (exact != null) return Math.max(0, exact);
        for (Map.Entry<String, Integer> e : values.entrySet()) if (key(e.getKey()).equals(key)) return Math.max(0, e.getValue());
        return 0;
    }

    public static String key(String commodity) {
        return commodity == null ? "" : commodity.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private record Selection(List<MultiCommodityMissionNeed> needs, int tons) { }
}
