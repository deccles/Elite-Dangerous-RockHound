package org.dce.ed.mission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges per-commodity market searches into station-level sourcing assessments. */
public final class MultiCommoditySourcePlanner {
    private MultiCommoditySourcePlanner() { }

    public static List<MultiCommodityStationAssessment> assess(List<MultiCommodityMissionNeed> needs,
            Map<String, Integer> inHold, Map<String, List<CommoditySourceChoice>> offersByCommodity) {
        Map<String, Builder> stations = new LinkedHashMap<>();
        if (offersByCommodity != null) for (Map.Entry<String, List<CommoditySourceChoice>> entry : offersByCommodity.entrySet()) {
            String commodity = MultiCommoditySourceAllocator.key(entry.getKey());
            if (entry.getValue() == null) continue;
            for (CommoditySourceChoice offer : entry.getValue()) {
                if (offer == null) continue;
                String marketKey = (offer.system() + "\n" + offer.station()).toLowerCase();
                Builder b = stations.computeIfAbsent(marketKey, k -> new Builder(offer));
                b.stock.put(commodity, Math.max(b.stock.getOrDefault(commodity, 0),
                        offer.supply() == null ? 0 : offer.supply()));
                b.oldest = older(b.oldest, offer.updatedAt());
            }
        }
        List<MultiCommodityStationAssessment> out = new ArrayList<>();
        for (Builder b : stations.values()) {
            MultiCommodityAllocation allocation = MultiCommoditySourceAllocator.allocate(needs, inHold, b.stock);
            Map<String, Integer> required = requiredByCommodity(needs);
            Map<String, Integer> held = normalizedQuantities(inHold);
            String text = required.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> displayCommodity(needs, e.getKey()) + " "
                            + Math.min(e.getValue(), held.getOrDefault(e.getKey(), 0)
                                    + b.stock.getOrDefault(e.getKey(), 0))
                            + "/" + e.getValue())
                    .reduce((a, c) -> a + "; " + c).orElse("");
            out.add(new MultiCommodityStationAssessment(b.station, allocation, Map.copyOf(b.stock), text, b.oldest));
        }
        out.sort(Comparator.comparingInt((MultiCommodityStationAssessment a) -> a.allocation().missionIds().size()).reversed()
                .thenComparingInt(a -> a.allocation().purchaseTons())
                .thenComparing(a -> a.station().systemDistanceLy(), Comparator.nullsLast(Double::compareTo)));
        return List.copyOf(out);
    }

    private static String displayCommodity(List<MultiCommodityMissionNeed> needs, String key) {
        if (needs != null) for (MultiCommodityMissionNeed need : needs)
            if (MultiCommoditySourceAllocator.key(need.commodity()).equals(key)) return need.commodity();
        return key;
    }

    private static Map<String, Integer> requiredByCommodity(List<MultiCommodityMissionNeed> needs) {
        Map<String, Integer> required = new LinkedHashMap<>();
        if (needs != null) for (MultiCommodityMissionNeed need : needs)
            required.merge(MultiCommoditySourceAllocator.key(need.commodity()), need.tons(), Integer::sum);
        return required;
    }

    private static Map<String, Integer> normalizedQuantities(Map<String, Integer> quantities) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        if (quantities != null) quantities.forEach((commodity, tons) ->
                normalized.merge(MultiCommoditySourceAllocator.key(commodity), Math.max(0, tons), Integer::sum));
        return normalized;
    }

    private static String older(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        try { return Instant.parse(a).isBefore(Instant.parse(b)) ? a : b; }
        catch (Exception ex) { return a; }
    }

    private static final class Builder {
        final CommoditySourceChoice station;
        final Map<String, Integer> stock = new LinkedHashMap<>();
        String oldest;
        Builder(CommoditySourceChoice station) { this.station = station; }
    }
}
