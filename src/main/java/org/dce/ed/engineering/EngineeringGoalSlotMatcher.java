package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;

/**
 * Assigns engineering goals to loadout rows using optional {@link EngineeringGoal#getTargetSlot()}
 * pins, then greedy matching for unscoped goals.
 *
 * <p>An unscoped (or multi-quantity) goal may claim up to {@link EngineeringGoal#getQuantity()}
 * compatible rows so a qty-2 plan lights up two fitted modules of that type.
 */
public final class EngineeringGoalSlotMatcher {

    private EngineeringGoalSlotMatcher() {
    }

    /** Stable key for a fitted module row (ship + journal slot). */
    public static String rowKey(Row row) {
        if (row == null) {
            return "";
        }
        String slot = row.slotKey() != null ? row.slotKey() : "";
        return row.shipId() + "\0" + slot;
    }

    /**
     * Maps {@link #rowKey(Row)} → matched goal. Exact slot pins win; remaining capacity is filled
     * greedily in stable slot order (up to each goal's quantity).
     */
    public static Map<String, EngineeringGoal> assign(List<Row> rows, List<EngineeringGoal> goals) {
        Map<String, EngineeringGoal> out = new HashMap<>();
        if (rows == null || rows.isEmpty() || goals == null || goals.isEmpty()) {
            return out;
        }
        Map<EngineeringGoal, Integer> remaining = new IdentityHashMap<>();
        for (EngineeringGoal goal : goals) {
            if (goal != null) {
                remaining.put(goal, Math.max(1, goal.getQuantity()));
            }
        }

        // Pass 1: exact targetSlot match (consumes one unit of capacity).
        for (Row row : rows) {
            if (row == null) {
                continue;
            }
            String key = rowKey(row);
            if (key.isBlank() || out.containsKey(key)) {
                continue;
            }
            String slot = row.slotKey() != null ? row.slotKey().trim() : "";
            if (slot.isEmpty()) {
                continue;
            }
            for (EngineeringGoal goal : goals) {
                if (goal == null || remainingCapacity(remaining, goal) <= 0 || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (!goal.hasTargetSlot() || !slot.equalsIgnoreCase(goal.getTargetSlot().trim())) {
                    continue;
                }
                if (!blueprintCompatible(row, goal)) {
                    continue;
                }
                claim(out, remaining, key, goal);
                break;
            }
        }

        // Pass 2: fill remaining capacity by stable slot order.
        // Qty-1 pins stay exclusive to their slot; multi-qty goals may claim additional free rows
        // even if a preferred pin was already consumed.
        List<Row> ordered = new ArrayList<>();
        for (Row row : rows) {
            if (row != null) {
                ordered.add(row);
            }
        }
        ordered.sort(Comparator
                .comparingLong(Row::shipId)
                .thenComparing(r -> r.slotKey() != null ? r.slotKey() : "", String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.moduleType() != null ? r.moduleType() : "", String.CASE_INSENSITIVE_ORDER));

        for (Row row : ordered) {
            String key = rowKey(row);
            if (key.isBlank() || out.containsKey(key)) {
                continue;
            }
            for (EngineeringGoal goal : goals) {
                if (goal == null || remainingCapacity(remaining, goal) <= 0 || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (goal.hasTargetSlot() && goal.getQuantity() <= 1) {
                    // Pinned qty-1: never steal for another row.
                    continue;
                }
                if (!blueprintCompatible(row, goal)) {
                    continue;
                }
                claim(out, remaining, key, goal);
                break;
            }
        }
        return out;
    }

    public static EngineeringGoal forRow(Row row, List<Row> allRows, List<EngineeringGoal> goals) {
        if (row == null) {
            return null;
        }
        return assign(allRows, goals).get(rowKey(row));
    }

    private static int remainingCapacity(Map<EngineeringGoal, Integer> remaining, EngineeringGoal goal) {
        Integer left = remaining.get(goal);
        return left != null ? left : 0;
    }

    private static void claim(Map<String, EngineeringGoal> out,
            Map<EngineeringGoal, Integer> remaining,
            String key,
            EngineeringGoal goal) {
        out.put(key, goal);
        remaining.put(goal, remainingCapacity(remaining, goal) - 1);
    }

    private static boolean sameShipAndModule(Row row, EngineeringGoal goal) {
        if (!goal.hasShip() || goal.getShipId() != row.shipId()) {
            return false;
        }
        return EngineeringJournalBlueprintResolver.sameModuleType(goal.getModuleType(), row.moduleType());
    }

    /**
     * Gap rows match by module type only. Partial/Done require the same blueprint name when the
     * row has one.
     */
    private static boolean blueprintCompatible(Row row, EngineeringGoal goal) {
        if (row.band() == Band.GAP) {
            return true;
        }
        String rowBp = EngineeringJournalBlueprintResolver.normalizeToken(row.blueprintLabel());
        if (rowBp.isEmpty()) {
            return true;
        }
        String goalBp = EngineeringJournalBlueprintResolver.normalizeToken(goal.getBlueprintName());
        return rowBp.equals(goalBp);
    }
}
