package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;

/**
 * Assigns engineering goals to loadout rows using optional {@link EngineeringGoal#getTargetSlot()}
 * pins, then greedy 1:1 matching for unscoped goals.
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
     * Maps {@link #rowKey(Row)} → matched goal. Exact slot pins win; remaining unscoped goals
     * are assigned at most once each in stable slot order.
     */
    public static Map<String, EngineeringGoal> assign(List<Row> rows, List<EngineeringGoal> goals) {
        Map<String, EngineeringGoal> out = new HashMap<>();
        if (rows == null || rows.isEmpty() || goals == null || goals.isEmpty()) {
            return out;
        }
        Set<EngineeringGoal> assigned = CollectionsIdentity.newSet();

        // Pass 1: exact targetSlot match.
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
                if (goal == null || assigned.contains(goal) || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (!goal.hasTargetSlot() || !slot.equalsIgnoreCase(goal.getTargetSlot().trim())) {
                    continue;
                }
                if (!blueprintCompatible(row, goal)) {
                    continue;
                }
                out.put(key, goal);
                assigned.add(goal);
                break;
            }
        }

        // Pass 2: unscoped goals, 1:1 by stable slot order.
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
                if (goal == null || assigned.contains(goal) || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (goal.hasTargetSlot()) {
                    // Pinned to a different (or unmatched) slot — never steal for another row.
                    continue;
                }
                if (!blueprintCompatible(row, goal)) {
                    continue;
                }
                out.put(key, goal);
                assigned.add(goal);
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

    /** Identity set without requiring equals/hashCode of goals. */
    private static final class CollectionsIdentity {
        static Set<EngineeringGoal> newSet() {
            return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        }
    }
}
