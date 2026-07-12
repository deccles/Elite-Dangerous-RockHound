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
        this.blueprintId = blueprintId != null ? blueprintId : "";
        this.moduleType = moduleType != null ? moduleType : "";
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.fromGrade = Math.max(0, fromGrade);
        this.craftsAtCurrentGrade = clampCrafts(craftsAtCurrentGrade);
        this.targetGrade = Math.max(1, targetGrade);
        this.experimentalId = experimentalId != null ? experimentalId : "";
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

    public EngineeringGoal withProgress(int newFromGrade, int newCraftsAtCurrentGrade) {
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                Math.max(0, newFromGrade),
                clampCrafts(newCraftsAtCurrentGrade),
                targetGrade,
                experimentalId);
    }

    public EngineeringGoal withFromGrade(int newFromGrade) {
        return withProgress(newFromGrade, 0);
    }

    public String displayLabel() {
        String base = moduleType + ": " + blueprintName + " → " + EngineeringGradeProgress.progressLabel(this);
        if (!experimentalId.isBlank()) {
            base += " + experimental";
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
                && experimentalId.equals(other.experimentalId);
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
        return result;
    }
}
