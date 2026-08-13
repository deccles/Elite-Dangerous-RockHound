package org.dce.ed.mission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Accumulates radius searches without duplicate markets. */
public final class CommoditySourceResults {
    public static final int ARDENT_RESULT_CAP = 1000;
    private final Map<String, CommoditySourceChoice> byMarket = new LinkedHashMap<>();

    public void clear() { byMarket.clear(); }

    public void merge(List<CommoditySourceChoice> choices) {
        if (choices == null) return;
        for (CommoditySourceChoice choice : choices) {
            if (choice == null) continue;
            String key = (choice.system() + "\n" + choice.station()).toLowerCase(Locale.ROOT);
            byMarket.put(key, choice);
        }
    }

    public List<CommoditySourceChoice> rows() {
        List<CommoditySourceChoice> rows = new ArrayList<>(byMarket.values());
        rows.sort(Comparator.comparing(CommoditySourceChoice::systemDistanceLy,
                Comparator.nullsLast(Double::compareTo)).thenComparing(CommoditySourceChoice::arrivalDistanceLs,
                        Comparator.nullsLast(Double::compareTo)));
        return List.copyOf(rows);
    }

    public static int radiusAfterCappedResponse(int radius) { return Math.max(1, radius / 2); }
    public static int nextRadius(int radius) { return Math.min(500, radius + 25); }
}
