package org.dce.ed.engineering;

/**
 * User goal: engineer a module to a target grade (and optional experimental).
 */
public final class EngineeringGoal {

    private final String blueprintId;
    private final String moduleType;
    private final String blueprintName;
    /** Highest fully completed grade (all {@link EngineeringGradeProgress#ROLLS_PER_GRADE} rolls done). */
    private final int fromGrade;
    /** Rolls completed at grade {@code fromGrade + 1} (0 until that grade is finished). */
    private final int craftsAtCurrentGrade;
    private final int targetGrade;
    private final String experimentalId;
    /** When false, goal stays listed but is omitted from materials and trade suggestions. */
    private final boolean includeInPlanning;
    /** True after the goal's experimental effect has been applied on the in-progress unit. */
    private final boolean experimentalApplied;
    /** How many modules to engineer (e.g. four gimbal weapons). */
    private final int quantity;
    /** How many modules are fully finished (grades + experimental when required). */
    private final int completedUnits;

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int targetGrade,
                           String experimentalId) {
        this(blueprintId, moduleType, blueprintName, fromGrade, 0, targetGrade, experimentalId);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade, experimentalId, true);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           boolean includeInPlanning) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, includeInPlanning, false);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           boolean includeInPlanning,
                           boolean experimentalApplied) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, includeInPlanning, experimentalApplied, 1, 0);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           boolean includeInPlanning,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits) {
        this.blueprintId = blueprintId != null ? blueprintId : "";
        this.moduleType = moduleType != null ? moduleType : "";
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.fromGrade = Math.max(0, fromGrade);
        this.craftsAtCurrentGrade = clampCrafts(craftsAtCurrentGrade);
        this.targetGrade = Math.max(1, targetGrade);
        this.experimentalId = experimentalId != null ? experimentalId : "";
        this.includeInPlanning = includeInPlanning;
        this.experimentalApplied = experimentalApplied;
        this.quantity = Math.max(1, quantity);
        this.completedUnits = Math.max(0, Math.min(completedUnits, this.quantity));
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public String getModuleType() {
        return moduleType;
    }

    public String getBlueprintName() {
        return blueprintName;
    }

    public int getFromGrade() {
        return fromGrade;
    }

    public int getCraftsAtCurrentGrade() {
        return craftsAtCurrentGrade;
    }

    public int getTargetGrade() {
        return targetGrade;
    }

    public String getExperimentalId() {
        return experimentalId;
    }

    public boolean isIncludeInPlanning() {
        return includeInPlanning;
    }

    public boolean isExperimentalApplied() {
        return experimentalApplied;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getCompletedUnits() {
        return completedUnits;
    }

    /** Modules still to finish (including the in-progress unit). */
    public int remainingUnits() {
        if (isComplete()) {
            return 0;
        }
        int remaining = quantity - completedUnits;
        if (isCurrentUnitComplete()) {
            remaining--;
        }
        return Math.max(0, remaining);
    }

    /** True when the in-progress unit has reached target grade and experimental (if any). */
    public boolean isCurrentUnitComplete() {
        if (fromGrade < targetGrade) {
            return false;
        }
        return experimentalId.isBlank() || experimentalApplied;
    }

    /** True when all requested units, grades, and any experimental effect are finished. */
    public boolean isComplete() {
        int done = completedUnits;
        if (isCurrentUnitComplete()) {
            done++;
        }
        return done >= quantity;
    }

    public EngineeringGoal withProgress(int newFromGrade, int newCraftsAtCurrentGrade) {
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                Math.max(0, newFromGrade),
                clampCrafts(newCraftsAtCurrentGrade),
                targetGrade,
                experimentalId,
                includeInPlanning,
                experimentalApplied,
                quantity,
                completedUnits);
    }

    public EngineeringGoal withExperimentalApplied(boolean applied) {
        if (experimentalApplied == applied) {
            return this;
        }
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                fromGrade,
                craftsAtCurrentGrade,
                targetGrade,
                experimentalId,
                includeInPlanning,
                applied,
                quantity,
                completedUnits);
    }

    public EngineeringGoal withIncludeInPlanning(boolean include) {
        if (includeInPlanning == include) {
            return this;
        }
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                fromGrade,
                craftsAtCurrentGrade,
                targetGrade,
                experimentalId,
                include,
                experimentalApplied,
                quantity,
                completedUnits);
    }

    public EngineeringGoal withQuantity(int newQuantity) {
        int q = Math.max(1, newQuantity);
        if (quantity == q && completedUnits <= q) {
            return this;
        }
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                fromGrade,
                craftsAtCurrentGrade,
                targetGrade,
                experimentalId,
                includeInPlanning,
                experimentalApplied,
                q,
                Math.min(completedUnits, q));
    }

    public EngineeringGoal withCompletedUnits(int newCompletedUnits) {
        int units = Math.max(0, Math.min(newCompletedUnits, quantity));
        if (completedUnits == units) {
            return this;
        }
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                fromGrade,
                craftsAtCurrentGrade,
                targetGrade,
                experimentalId,
                includeInPlanning,
                experimentalApplied,
                quantity,
                units);
    }

    /**
     * Updates user-facing settings from the edit dialog while preserving journal progress where possible.
     */
    public EngineeringGoal withUserSettings(int newTargetGrade, String newExperimentalId, int newQuantity) {
        int grade = Math.max(1, newTargetGrade);
        String expId = newExperimentalId != null ? newExperimentalId : "";
        int q = Math.max(1, newQuantity);
        int units = Math.min(completedUnits, q);
        boolean expApplied = experimentalApplied;
        if (!expId.equals(experimentalId)) {
            expApplied = false;
        }
        int progFrom = Math.min(fromGrade, grade);
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                progFrom,
                craftsAtCurrentGrade,
                grade,
                expId,
                includeInPlanning,
                expApplied,
                q,
                units);
    }

    public EngineeringGoal withFromGrade(int newFromGrade) {
        return withProgress(newFromGrade, 0);
    }

    /** Clears roll, unit, and experimental progress before replaying journal crafts. */
    public EngineeringGoal resetJournalProgress() {
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                0,
                0,
                targetGrade,
                experimentalId,
                includeInPlanning,
                false,
                quantity,
                0);
    }

    public String displayLabel() {
        String base = moduleType + ": " + blueprintName + " → " + EngineeringGradeProgress.progressLabel(this);
        if (!experimentalId.isBlank()) {
            base += experimentalApplied ? " + experimental (done)" : " + experimental";
        }
        if (quantity > 1) {
            base += " (" + completedUnits + "/" + quantity + ")";
        }
        return base;
    }

    private static int clampCrafts(int crafts) {
        return Math.max(0, Math.min(crafts, EngineeringGradeProgress.ROLLS_PER_GRADE - 1));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EngineeringGoal other)) {
            return false;
        }
        return blueprintId.equals(other.blueprintId)
                && moduleType.equals(other.moduleType)
                && blueprintName.equals(other.blueprintName)
                && fromGrade == other.fromGrade
                && craftsAtCurrentGrade == other.craftsAtCurrentGrade
                && targetGrade == other.targetGrade
                && experimentalId.equals(other.experimentalId)
                && includeInPlanning == other.includeInPlanning
                && experimentalApplied == other.experimentalApplied
                && quantity == other.quantity
                && completedUnits == other.completedUnits;
    }

    @Override
    public int hashCode() {
        int result = blueprintId.hashCode();
        result = 31 * result + moduleType.hashCode();
        result = 31 * result + blueprintName.hashCode();
        result = 31 * result + fromGrade;
        result = 31 * result + craftsAtCurrentGrade;
        result = 31 * result + targetGrade;
        result = 31 * result + experimentalId.hashCode();
        result = 31 * result + Boolean.hashCode(includeInPlanning);
        result = 31 * result + Boolean.hashCode(experimentalApplied);
        result = 31 * result + quantity;
        result = 31 * result + completedUnits;
        return result;
    }
}
