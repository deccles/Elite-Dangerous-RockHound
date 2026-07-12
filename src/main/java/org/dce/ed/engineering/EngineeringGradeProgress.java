package org.dce.ed.engineering;

/**
 * Elite engineering requires multiple applications per blueprint grade before the next grade unlocks.
 */
public final class EngineeringGradeProgress {

    /** Journal rolls required at each grade (G1–G5) before the next grade unlocks. */
    public static final int ROLLS_PER_GRADE = 5;

    private EngineeringGradeProgress() {
    }

    /** Rolls still needed at {@code grade} for this goal (0 if grade is already complete or out of range). */
    public static int rollsRemainingAtGrade(EngineeringGoal goal, int grade) {
        if (goal == null || grade < 1 || grade > goal.getTargetGrade()) {
            return 0;
        }
        if (grade <= goal.getFromGrade()) {
            return 0;
        }
        if (grade == goal.getFromGrade() + 1) {
            return Math.max(0, ROLLS_PER_GRADE - goal.getCraftsAtCurrentGrade());
        }
        return ROLLS_PER_GRADE;
    }

    /** Applies one journal craft at {@code craftLevel} to goal progress. */
    public static EngineeringGoal afterCraft(EngineeringGoal goal, int craftLevel) {
        if (goal == null || craftLevel < 1) {
            return goal;
        }
        int completed = goal.getFromGrade();
        int crafts = goal.getCraftsAtCurrentGrade();

        if (craftLevel <= completed) {
            return goal;
        }
        if (craftLevel == completed + 1) {
            crafts++;
            if (crafts >= ROLLS_PER_GRADE) {
                completed = craftLevel;
                crafts = 0;
            }
            return goal.withProgress(completed, crafts);
        }
        // Craft at a higher grade than tracked — assume earlier grades were done before tracking started.
        return goal.withProgress(craftLevel - 1, 1);
    }

    public static String progressLabel(EngineeringGoal goal) {
        if (goal == null) {
            return "";
        }
        if (goal.getFromGrade() >= goal.getTargetGrade()) {
            return "G" + goal.getTargetGrade();
        }
        int working = goal.getFromGrade() + 1;
        if (goal.getFromGrade() > 0 || goal.getCraftsAtCurrentGrade() > 0) {
            return "G" + goal.getTargetGrade() + " (G" + working + " "
                    + goal.getCraftsAtCurrentGrade() + "/" + ROLLS_PER_GRADE + ")";
        }
        return "G" + goal.getTargetGrade();
    }
}
