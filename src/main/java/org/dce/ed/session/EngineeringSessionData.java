package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Engineering tab goals persisted in {@link EdoSessionState}.
 */
public final class EngineeringSessionData {

    private List<EngineeringGoalPersisted> goals = new ArrayList<>();

    public List<EngineeringGoalPersisted> getGoals() {
        return goals;
    }

    public void setGoals(List<EngineeringGoalPersisted> goals) {
        this.goals = goals != null ? goals : new ArrayList<>();
    }

    public List<EngineeringGoalPersisted> goalsOrEmpty() {
        return goals != null ? goals : new ArrayList<>();
    }

    public static final class EngineeringGoalPersisted {
        private String blueprintId;
        private String moduleType;
        private String blueprintName;
        private int fromGrade;
        private int craftsAtCurrentGrade;
        private int targetGrade;
        private String experimentalId;
        /** {@code null} = include (legacy sessions). */
        private Boolean includeInPlanning;
        private boolean experimentalApplied;
        /** {@code null} or {@code <= 0} = 1 (legacy sessions). */
        private Integer quantity;
        private int completedUnits;

        public String getBlueprintId() {
            return blueprintId;
        }

        public void setBlueprintId(String blueprintId) {
            this.blueprintId = blueprintId;
        }

        public String getModuleType() {
            return moduleType;
        }

        public void setModuleType(String moduleType) {
            this.moduleType = moduleType;
        }

        public String getBlueprintName() {
            return blueprintName;
        }

        public void setBlueprintName(String blueprintName) {
            this.blueprintName = blueprintName;
        }

        public int getFromGrade() {
            return fromGrade;
        }

        public void setFromGrade(int fromGrade) {
            this.fromGrade = fromGrade;
        }

        public int getCraftsAtCurrentGrade() {
            return craftsAtCurrentGrade;
        }

        public void setCraftsAtCurrentGrade(int craftsAtCurrentGrade) {
            this.craftsAtCurrentGrade = craftsAtCurrentGrade;
        }

        public int getTargetGrade() {
            return targetGrade;
        }

        public void setTargetGrade(int targetGrade) {
            this.targetGrade = targetGrade;
        }

        public String getExperimentalId() {
            return experimentalId;
        }

        public void setExperimentalId(String experimentalId) {
            this.experimentalId = experimentalId;
        }

        public Boolean getIncludeInPlanning() {
            return includeInPlanning;
        }

        public void setIncludeInPlanning(Boolean includeInPlanning) {
            this.includeInPlanning = includeInPlanning;
        }

        public boolean includeInPlanningOrDefault() {
            return includeInPlanning == null || includeInPlanning;
        }

        public boolean isExperimentalApplied() {
            return experimentalApplied;
        }

        public void setExperimentalApplied(boolean experimentalApplied) {
            this.experimentalApplied = experimentalApplied;
        }

        public int getQuantity() {
            return quantity != null && quantity > 0 ? quantity : 1;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity > 0 ? quantity : 1;
        }

        public int getCompletedUnits() {
            return Math.max(0, completedUnits);
        }

        public void setCompletedUnits(int completedUnits) {
            this.completedUnits = Math.max(0, completedUnits);
        }
    }
}
