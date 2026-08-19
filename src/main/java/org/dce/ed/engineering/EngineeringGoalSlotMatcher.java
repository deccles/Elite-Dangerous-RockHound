package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dce.ed.engineering.ShipEngineeringSummary.Band;
import org.dce.ed.engineering.ShipEngineeringSummary.Row;

/**
 * Assigns engineering goals to loadout rows using optional {@link EngineeringGoal#getTargetSlots()}
 * pins, then greedy matching for unscoped goals.
 *
 * <p>An unscoped (or multi-quantity) goal may claim up to {@link EngineeringGoal#getQuantity()}
 * compatible rows so a qty-2 plan lights up two fitted modules of that type.
 *
 * <p>Partial/Done rows with a fitted experimental prefer the goal that wants that experimental,
 * and reject goals that require a different one — so a Corrosive Multi-cannon is not claimed by
 * an Auto Loader plan (which would hide Add goal on stock hardpoints).
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
        return assign(rows, goals, EngineeringDatabase.getInstance());
    }

    /**
     * @param database used to resolve goal experimental ids to catalog names; may be null (weaker
     *                 experimental matching via id token only)
     */
    public static Map<String, EngineeringGoal> assign(List<Row> rows,
                                                      List<EngineeringGoal> goals,
                                                      EngineeringDatabase database) {
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
            EngineeringGoal best = null;
            int bestScore = Integer.MIN_VALUE;
            for (EngineeringGoal goal : goals) {
                if (goal == null || remainingCapacity(remaining, goal) <= 0 || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (!goal.hasTargetSlot() || !goal.targetsSlot(slot)) {
                    continue;
                }
                int score = compatibilityScore(row, goal, database);
                if (score < 0) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = goal;
                }
            }
            if (best != null) {
                claim(out, remaining, key, best);
            }
        }

        // Pass 2: fill remaining capacity by stable slot order.
        // Qty-1 pins stay exclusive to their slot; multi-qty goals may claim additional free rows
        // even if a preferred pin was already consumed.
        // Prefer experimental matches on Partial/Done before handing stock GAP rows to sibling plans.
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

        // Pass 2a: engineered rows first (so Corrosive Huge is claimed before stock hardpoints).
        claimByScore(ordered, goals, remaining, out, database, true);
        // Pass 2b: GAP / remaining rows.
        claimByScore(ordered, goals, remaining, out, database, false);
        return out;
    }

    private static void claimByScore(List<Row> ordered,
                                     List<EngineeringGoal> goals,
                                     Map<EngineeringGoal, Integer> remaining,
                                     Map<String, EngineeringGoal> out,
                                     EngineeringDatabase database,
                                     boolean engineeredOnly) {
        for (Row row : ordered) {
            String key = rowKey(row);
            if (key.isBlank() || out.containsKey(key)) {
                continue;
            }
            boolean engineered = row.band() == Band.PARTIAL || row.band() == Band.DONE;
            if (engineeredOnly != engineered) {
                continue;
            }
            EngineeringGoal best = null;
            int bestScore = Integer.MIN_VALUE;
            for (EngineeringGoal goal : goals) {
                if (goal == null || remainingCapacity(remaining, goal) <= 0 || !sameShipAndModule(row, goal)) {
                    continue;
                }
                if (goal.hasTargetSlot()) {
                    // Slot-scoped goals were fully handled above; never steal an unrelated row.
                    continue;
                }
                int score = compatibilityScore(row, goal, database);
                if (score < 0) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = goal;
                }
            }
            if (best != null) {
                claim(out, remaining, key, best);
            }
        }
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
     * Compatibility score for claiming a row. {@code < 0} means incompatible.
     * Higher scores win when multiple goals could claim the same row.
     */
    private static int compatibilityScore(Row row, EngineeringGoal goal, EngineeringDatabase database) {
        if (row.band() == Band.GAP) {
            return 1;
        }
        String rowBp = EngineeringJournalBlueprintResolver.normalizeToken(row.blueprintLabel());
        if (!rowBp.isEmpty()) {
            String goalBp = EngineeringJournalBlueprintResolver.normalizeToken(goal.getBlueprintName());
            if (!rowBp.equals(goalBp)) {
                return -1;
            }
        }
        String rowExp = row.experimentalLabel();
        boolean rowHasExp = rowExp != null && !rowExp.isBlank();
        boolean goalWantsExp = goal.getExperimentalId() != null && !goal.getExperimentalId().isBlank();
        if (!rowHasExp) {
            return 2; // blueprint match, experimental still open
        }
        if (!goalWantsExp) {
            return 2; // goal does not care which experimental is fitted
        }
        if (experimentalMatchesRow(rowExp, goal, database)) {
            return 4; // exact experimental preference
        }
        return -1; // fitted experimental belongs to a different plan
    }

    private static boolean experimentalMatchesRow(String fittedLabel,
                                                  EngineeringGoal goal,
                                                  EngineeringDatabase database) {
        if (fittedLabel == null || fittedLabel.isBlank() || goal == null) {
            return false;
        }
        String fitted = EngineeringJournalBlueprintResolver.normalizeToken(fittedLabel);
        if (fitted.isEmpty()) {
            return false;
        }
        if (database != null) {
            Optional<BlueprintGrade> exp = database.findById(goal.getExperimentalId());
            if (exp.isPresent()) {
                String want = EngineeringJournalBlueprintResolver.normalizeToken(exp.get().getName());
                if (!want.isEmpty() && (fitted.equals(want) || fitted.contains(want) || want.contains(fitted))) {
                    return true;
                }
            }
        }
        String id = EngineeringJournalBlueprintResolver.normalizeToken(goal.getExperimentalId());
        return !id.isEmpty() && (fitted.contains(id) || id.contains(fitted));
    }
}
