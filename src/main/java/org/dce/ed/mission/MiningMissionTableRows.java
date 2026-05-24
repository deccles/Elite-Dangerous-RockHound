package org.dce.ed.mission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.dce.ed.util.ValuableBodyExplorationEstimate;

/**
 * Rows for the Mining tab missions table: one line per commodity and turn-in destination.
 */
public final class MiningMissionTableRows {

    /** Cargo/percent shown for a row after in-hold is split across rows of the same material. */
    public static final class Display {
        private final int inHoldTons;
        private final int remainingTons;
        private final double percentComplete;

        Display(int inHoldTons, int remainingTons, double percentComplete) {
            this.inHoldTons = inHoldTons;
            this.remainingTons = remainingTons;
            this.percentComplete = percentComplete;
        }

        public int getInHoldTons() {
            return inHoldTons;
        }

        public double getPercentComplete() {
            return percentComplete;
        }

        public String getQuantityDisplay() {
            return inHoldTons + "/" + Math.max(0, remainingTons) + " t";
        }

        public String getPercentDisplay() {
            if (remainingTons <= 0 && percentComplete >= 99.95) {
                return "100%";
            }
            return String.format("%.0f%%", Math.min(100.0, Math.max(0.0, percentComplete)));
        }
    }

    public static final class Row {
        private final String material;
        private final int totalDelivered;
        private final int remainingTons;
        private final int totalRequiredTons;
        private final long rewardCredits;
        private final String rewardDisplay;
        private final MissionDestination turnIn;
        private final String turnInDisplay;
        private final String turnInCopy;

        Row(String material,
                int totalDelivered,
                int remainingTons,
                int totalRequiredTons,
                long rewardCredits,
                MissionDestination turnIn) {
            this.material = material;
            this.totalDelivered = totalDelivered;
            this.remainingTons = remainingTons;
            this.totalRequiredTons = totalRequiredTons;
            this.rewardCredits = rewardCredits;
            this.rewardDisplay = rewardCredits > 0
                    ? ValuableBodyExplorationEstimate.formatCredits(rewardCredits)
                    : "—";
            this.turnIn = turnIn;
            this.turnInDisplay = turnIn != null ? turnIn.displayLine() : "—";
            this.turnInCopy = turnIn != null ? turnIn.copyLine() : "";
        }

        public String getMaterial() {
            return material;
        }

        public int getTotalDelivered() {
            return totalDelivered;
        }

        public int getRemainingTons() {
            return remainingTons;
        }

        public int getTotalRequiredTons() {
            return totalRequiredTons;
        }

        public String getRewardDisplay() {
            return rewardDisplay;
        }

        public long getRewardCredits() {
            return rewardCredits;
        }

        public String getTurnInDisplay() {
            return turnInDisplay;
        }

        public String getTurnInCopy() {
            return turnInCopy;
        }
    }

    static final class PendingRow {
        final String commodity;
        final int totalRequired;
        final int totalDelivered;
        final int remaining;
        final long totalReward;
        final MissionDestination turnIn;
        final String turnInDisplay;

        PendingRow(String commodity,
                int totalRequired,
                int totalDelivered,
                int remaining,
                long totalReward,
                MissionDestination turnIn) {
            this.commodity = commodity;
            this.totalRequired = totalRequired;
            this.totalDelivered = totalDelivered;
            this.remaining = remaining;
            this.totalReward = totalReward;
            this.turnIn = turnIn;
            this.turnInDisplay = turnIn != null ? turnIn.displayLine() : "—";
        }
    }

    private MiningMissionTableRows() {
    }

    public static List<Row> build(MissionTracker tracker) {
        if (tracker == null) {
            return List.of();
        }
        Map<String, Aggregator> groups = new LinkedHashMap<>();
        for (MissionRecord r : tracker.getActive()) {
            if (!isMiningCommodityMission(r)) {
                continue;
            }
            String commodity = r.getCommodityLocalised();
            if (commodity == null || commodity.isBlank()) {
                continue;
            }
            MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
            String key = normalizeCommodity(commodity) + "|" + turnInKey(turnIn);
            Aggregator agg = groups.computeIfAbsent(key, k -> new Aggregator(commodity, turnIn));
            int req = r.getCountRequired() > 0 ? r.getCountRequired() : r.getTotalItemsToDeliver();
            if (req > 0) {
                agg.totalRequired += req;
            }
            agg.totalDelivered += Math.max(0, r.getItemsDelivered());
            if (r.getReward() > 0) {
                agg.totalReward += r.getReward();
            }
        }
        List<Row> rows = new ArrayList<>();
        List<PendingRow> pending = new ArrayList<>();
        for (Aggregator agg : groups.values()) {
            if (agg.totalRequired <= 0 && agg.totalDelivered <= 0) {
                continue;
            }
            int remaining = Math.max(0, agg.totalRequired - agg.totalDelivered);
            pending.add(new PendingRow(
                    agg.commodity,
                    agg.totalRequired,
                    agg.totalDelivered,
                    remaining,
                    agg.totalReward,
                    agg.turnIn));
        }
        pending.sort(Comparator.comparing((PendingRow p) -> p.commodity, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> p.turnInDisplay, String.CASE_INSENSITIVE_ORDER));
        for (PendingRow p : pending) {
            rows.add(new Row(
                    p.commodity,
                    p.totalDelivered,
                    p.remaining,
                    p.totalRequired,
                    p.totalReward,
                    p.turnIn));
        }
        return rows;
    }

    /**
     * For one material, rows in {@code modelIndicesInPriorityOrder} (e.g. current table view) are filled in order.
     * Cargo in hold and percent credit apply only while prior rows for that material are incomplete; later rows stay
     * at 0% until the first row reaches 100%.
     */
    public static Map<Integer, Display> allocateDisplayForModelOrder(
            List<Row> rows,
            List<Integer> modelIndicesInPriorityOrder,
            ToIntFunction<String> inHoldForCommodity) {
        if (rows == null || rows.isEmpty() || modelIndicesInPriorityOrder == null || modelIndicesInPriorityOrder.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Integer>> indicesByMaterial = new LinkedHashMap<>();
        for (int modelIndex : modelIndicesInPriorityOrder) {
            if (modelIndex < 0 || modelIndex >= rows.size()) {
                continue;
            }
            Row row = rows.get(modelIndex);
            indicesByMaterial
                    .computeIfAbsent(normalizeCommodity(row.getMaterial()), k -> new ArrayList<>())
                    .add(modelIndex);
        }
        Map<Integer, Display> displayByModelRow = new HashMap<>();
        for (List<Integer> modelIndices : indicesByMaterial.values()) {
            if (modelIndices.isEmpty()) {
                continue;
            }
            Row first = rows.get(modelIndices.get(0));
            int holdLeft = Math.max(0, inHoldForCommodity.applyAsInt(first.getMaterial()));
            boolean priorRowsIncomplete = false;
            for (int modelIndex : modelIndices) {
                Row row = rows.get(modelIndex);
                if (priorRowsIncomplete) {
                    displayByModelRow.put(modelIndex, new Display(0, row.getRemainingTons(), 0.0));
                    continue;
                }
                int fillFromHold = Math.min(holdLeft, Math.max(0, row.getRemainingTons()));
                holdLeft -= fillFromHold;
                double pct = row.getTotalRequiredTons() > 0
                        ? 100.0 * (row.getTotalDelivered() + fillFromHold) / (double) row.getTotalRequiredTons()
                        : 0.0;
                displayByModelRow.put(modelIndex, new Display(fillFromHold, row.getRemainingTons(), pct));
                boolean rowComplete = row.getRemainingTons() <= 0
                        || row.getTotalDelivered() + fillFromHold >= row.getTotalRequiredTons();
                if (!rowComplete) {
                    priorRowsIncomplete = true;
                }
            }
        }
        return displayByModelRow;
    }

    private static boolean isMiningCommodityMission(MissionRecord r) {
        if (r == null || !r.isCommodityMission()) {
            return false;
        }
        String name = r.getName();
        if (name != null) {
            if (name.startsWith("Mission_Mining_")) {
                return true;
            }
            if (name.endsWith("_name")) {
                name = name.substring(0, name.length() - 5);
            }
            if (name.startsWith("Mission_Mining_")) {
                return true;
            }
        }
        String localised = r.getLocalisedName();
        return localised != null && localised.toLowerCase().contains("mining");
    }

    private static String normalizeCommodity(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static String turnInKey(MissionDestination d) {
        if (d == null || d.isEmpty()) {
            return "";
        }
        return (d.getSystem() != null ? d.getSystem() : "")
                + "|" + (d.getStation() != null ? d.getStation() : "")
                + "|" + (d.getSettlement() != null ? d.getSettlement() : "");
    }

    private static final class Aggregator {
        final String commodity;
        final MissionDestination turnIn;
        int totalRequired;
        int totalDelivered;
        long totalReward;

        Aggregator(String commodity, MissionDestination turnIn) {
            this.commodity = commodity;
            this.turnIn = turnIn;
        }
    }
}
