package org.dce.ed.engineering;

/**
 * Elite engineering grade progress and roll counts.
 *
 * <p>Journal {@code Quality} is authoritative when present: a grade finishes when Quality reaches
 * ~1.0. Roll counts for material estimates follow engineer reputation (see
 * {@link #rollsRequired(int, int)}).
 */
public final class EngineeringGradeProgress {

    /** Worst-case / legacy rolls per grade when reputation is unknown. */
    public static final int ROLLS_PER_GRADE = 5;

    /**
     * Rolls to finish {@code grade} at engineer access {@code rank} (both 1–5).
     *
     * <pre>
     *        G1 G2 G3 G4 G5
     * Rank1   5  -  -  -  -
     * Rank2   4  5  -  -  -
     * Rank3   3  4  5  -  -
     * Rank4   2  3  4  5  -
     * Rank5   1  2  3  4  5
     * </pre>
     */
    private static final int[][] ROLLS_BY_RANK_AND_GRADE = {
            {0, 0, 0, 0, 0, 0},
            {0, 5, 5, 5, 5, 5},
            {0, 4, 5, 5, 5, 5},
            {0, 3, 4, 5, 5, 5},
            {0, 2, 3, 4, 5, 5},
            {0, 1, 2, 3, 4, 5}
    };

    private EngineeringGradeProgress() {
    }

    /**
     * Rolls needed to complete {@code grade} given engineer reputation {@code rank}.
     * Unknown/zero rank uses the conservative 5-roll schedule. Grades above the current unlock
     * assume the minimum rank required to craft that grade.
     */
    public static int rollsRequired(int engineerRank, int grade) {
        if (grade < 1 || grade > 5) {
            return 0;
        }
        int rank = Math.max(0, Math.min(5, engineerRank));
        if (rank <= 0) {
            return ROLLS_PER_GRADE;
        }
        int effective = Math.min(5, Math.max(rank, grade));
        return ROLLS_BY_RANK_AND_GRADE[effective][grade];
    }

    /** Rolls still needed at {@code grade} for this goal (unknown rank ⇒ 5-roll schedule). */
    public static int rollsRemainingAtGrade(EngineeringGoal goal, int grade) {
        return rollsRemainingAtGrade(goal, grade, 0);
    }

    public static int rollsRemainingAtGrade(EngineeringGoal goal, int grade, int engineerRank) {
        if (goal == null || grade < 1 || grade > goal.getTargetGrade()) {
            return 0;
        }
        if (grade <= goal.getFromGrade()) {
            return 0;
        }
        int required = rollsRequired(engineerRank, grade);
        if (grade == goal.getFromGrade() + 1) {
            int done = Math.min(required, rescaleCrafts(
                    goal.getCraftsAtCurrentGrade(), ROLLS_PER_GRADE, required));
            return Math.max(0, required - done);
        }
        return required;
    }

    /**
     * Reinterprets crafts stored on the legacy 5-roll scale onto {@code toScale} rolls for the
     * current reputation schedule.
     */
    static int rescaleCrafts(int craftsOnFiveScale, int fromScale, int toScale) {
        if (toScale <= 0) {
            return 0;
        }
        if (fromScale <= 0 || fromScale == toScale) {
            return Math.max(0, Math.min(toScale, craftsOnFiveScale));
        }
        if (craftsOnFiveScale <= 0) {
            return 0;
        }
        int scaled = (int) Math.round(craftsOnFiveScale * (double) toScale / (double) fromScale);
        return Math.max(0, Math.min(toScale, scaled));
    }

    public static EngineeringGoal afterCraft(EngineeringGoal goal, int craftLevel) {
        return afterCraft(goal, craftLevel, Double.NaN, 0);
    }

    public static EngineeringGoal afterCraft(EngineeringGoal goal, int craftLevel, double quality) {
        return afterCraft(goal, craftLevel, quality, 0);
    }

    /**
     * @param quality journal {@code Quality} after the craft, or {@link Double#NaN} if unknown
     * @param engineerRank reputation with the crafting engineer (0 = unknown ⇒ 5-roll schedule)
     */
    public static EngineeringGoal afterCraft(EngineeringGoal goal,
                                             int craftLevel,
                                             double quality,
                                             int engineerRank) {
        if (goal == null || craftLevel < 1) {
            return goal;
        }
        if (craftLevel <= goal.getFromGrade()) {
            return goal;
        }

        int rollsForLevel = rollsRequired(engineerRank, craftLevel);
        boolean qualityComplete = !Double.isNaN(quality) && quality >= 0.999d;

        if (qualityComplete) {
            return goal.withProgress(craftLevel, 0);
        }

        int completed = goal.getFromGrade();
        int craftsFive = goal.getCraftsAtCurrentGrade();

        if (craftLevel == completed + 1) {
            if (!Double.isNaN(quality)) {
                int onSchedule = craftsFromQuality(quality, rollsForLevel);
                if (onSchedule >= rollsForLevel) {
                    return goal.withProgress(craftLevel, 0);
                }
                // Count this application, but never regress below journal Quality.
                craftsFive = Math.max(craftsFive + 1, craftsFromQualityFive(quality));
            } else {
                craftsFive++;
                int onSchedule = rescaleCrafts(craftsFive, ROLLS_PER_GRADE, rollsForLevel);
                if (onSchedule >= rollsForLevel) {
                    return goal.withProgress(craftLevel, 0);
                }
            }
            if (craftsFive >= ROLLS_PER_GRADE) {
                return goal.withProgress(craftLevel, 0);
            }
            return goal.withProgress(completed, Math.min(ROLLS_PER_GRADE - 1, craftsFive));
        }

        // craftLevel > completed + 1: skipped grades (incomplete history).
        if (!Double.isNaN(quality)) {
            int onSchedule = craftsFromQuality(quality, rollsForLevel);
            if (onSchedule >= rollsForLevel) {
                return goal.withProgress(craftLevel, 0);
            }
            int intoFive = Math.max(1, craftsFromQualityFive(quality));
            return goal.withProgress(craftLevel - 1, intoFive);
        }
        return goal.withProgress(craftLevel - 1, 1);
    }

    /** Maps journal Quality onto 0..{@code rollsPerGrade} progress steps. */
    static int craftsFromQuality(double quality, int rollsPerGrade) {
        if (Double.isNaN(quality) || quality <= 0.01d || rollsPerGrade <= 0) {
            return 0;
        }
        if (quality >= 0.999d) {
            return rollsPerGrade;
        }
        int rolls = (int) Math.round(quality * rollsPerGrade);
        if (rolls <= 0) {
            rolls = 1;
        }
        return Math.min(rollsPerGrade, rolls);
    }

    /** Legacy 5-scale craft count from quality (stored on {@link EngineeringGoal}). */
    static int craftsFromQualityFive(double quality) {
        int rolls = craftsFromQuality(quality, ROLLS_PER_GRADE);
        if (rolls >= ROLLS_PER_GRADE) {
            return ROLLS_PER_GRADE;
        }
        return Math.min(ROLLS_PER_GRADE - 1, rolls);
    }

    public static String progressLabel(EngineeringGoal goal) {
        return progressLabel(goal, 0);
    }

    public static String progressLabel(EngineeringGoal goal, int engineerRank) {
        if (goal == null) {
            return "";
        }
        if (goal.getFromGrade() >= goal.getTargetGrade()) {
            return "G" + goal.getTargetGrade();
        }
        int working = goal.getFromGrade() + 1;
        int required = rollsRequired(engineerRank, working);
        int done = rescaleCrafts(goal.getCraftsAtCurrentGrade(), ROLLS_PER_GRADE, required);
        if (goal.getFromGrade() > 0 || goal.getCraftsAtCurrentGrade() > 0) {
            return "G" + goal.getTargetGrade() + " (G" + working + " " + done + "/" + required + ")";
        }
        return "G" + goal.getTargetGrade();
    }

    /**
     * 0..1 craft completion for UI (grades + optional experimental, averaged across quantity).
     * Uses the reputation roll schedule when {@code engineerRank} &gt; 0.
     */
    public static double completionFraction(EngineeringGoal goal, int engineerRank) {
        if (goal == null) {
            return 0.0;
        }
        if (goal.isComplete()) {
            return 1.0;
        }
        int qty = Math.max(1, goal.getQuantity());
        double currentUnit = unitCompletionFraction(goal, engineerRank);
        return Math.min(1.0, (goal.getCompletedUnits() + currentUnit) / (double) qty);
    }

    /** 0..1 progress for the in-progress unit only (ignores already-completed quantity). */
    public static double unitCompletionFraction(EngineeringGoal goal, int engineerRank) {
        if (goal == null) {
            return 0.0;
        }
        if (goal.isCurrentUnitComplete()) {
            return 1.0;
        }
        int target = Math.max(0, goal.getTargetGrade());
        if (target <= 0) {
            return 0.0;
        }
        int total = 0;
        int done = 0;
        for (int g = 1; g <= target; g++) {
            int required = rollsRequired(engineerRank, g);
            total += required;
            if (g <= goal.getFromGrade()) {
                done += required;
            } else if (g == goal.getFromGrade() + 1) {
                done += Math.min(required, rescaleCrafts(
                        goal.getCraftsAtCurrentGrade(), ROLLS_PER_GRADE, required));
            }
        }
        if (!goal.getExperimentalId().isBlank()) {
            total += 1;
            if (goal.isExperimentalApplied()) {
                done += 1;
            }
        }
        if (total <= 0) {
            return 0.0;
        }
        return Math.min(1.0, done / (double) total);
    }
}
