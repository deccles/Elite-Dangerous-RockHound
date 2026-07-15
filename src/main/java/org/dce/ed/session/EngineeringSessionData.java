package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Engineering tab goals persisted in {@link EdoSessionState}.
 */
public final class EngineeringSessionData {

    private List<EngineeringGoalPersisted> goals = new ArrayList<>();
    private List<ShipPersisted> knownShips = new ArrayList<>();
    /** null = All ships filter. */
    private Long goalsShipFilterId;

    public List<EngineeringGoalPersisted> getGoals() {
        return goals;
    }

    public void setGoals(List<EngineeringGoalPersisted> goals) {
        this.goals = goals != null ? goals : new ArrayList<>();
    }

    public List<EngineeringGoalPersisted> goalsOrEmpty() {
        return goals != null ? goals : new ArrayList<>();
    }

    public List<ShipPersisted> getKnownShips() {
        return knownShips;
    }

    public void setKnownShips(List<ShipPersisted> knownShips) {
        this.knownShips = knownShips != null ? knownShips : new ArrayList<>();
    }

    public List<ShipPersisted> knownShipsOrEmpty() {
        return knownShips != null ? knownShips : new ArrayList<>();
    }

    public Long getGoalsShipFilterId() {
        return goalsShipFilterId;
    }

    public void setGoalsShipFilterId(Long goalsShipFilterId) {
        this.goalsShipFilterId = goalsShipFilterId;
    }

    public static final class EngineeringGoalPersisted {
        private String blueprintId;
        private String moduleType;
        private String blueprintName;
        private int fromGrade;
        private int craftsAtCurrentGrade;
        private int targetGrade;
        private String experimentalId;
        /** {@code null} = include (legacy sessions). Prefer {@link #priority}. */
        private Boolean includeInPlanning;
        /** {@code HIGH}/{@code MEDIUM}/{@code LOW}/{@code DISABLED}; null = migrate from includeInPlanning. */
        private String priority;
        private boolean experimentalApplied;
        /** {@code null} or {@code <= 0} = 1 (legacy sessions). */
        private Integer quantity;
        private int completedUnits;
        /** null / missing = unset (legacy). */
        private Long shipId;
        private String shipLabel;

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

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        /** Resolved priority for load; migrates legacy includeInPlanning. */
        public org.dce.ed.engineering.GoalPriority priorityOrDefault() {
            if (priority != null && !priority.isBlank()) {
                return org.dce.ed.engineering.GoalPriority.parse(priority);
            }
            return org.dce.ed.engineering.GoalPriority.fromInclude(includeInPlanningOrDefault());
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

        public Long getShipId() {
            return shipId;
        }

        public void setShipId(Long shipId) {
            this.shipId = shipId;
        }

        public long shipIdOrUnknown() {
            return shipId != null ? shipId.longValue() : -1L;
        }

        public String getShipLabel() {
            return shipLabel != null ? shipLabel : "";
        }

        public void setShipLabel(String shipLabel) {
            this.shipLabel = shipLabel;
        }
    }

    public static final class ShipPersisted {
        private long shipId;
        private String shipType;
        private String shipName;
        private String shipIdent;

        public long getShipId() {
            return shipId;
        }

        public void setShipId(long shipId) {
            this.shipId = shipId;
        }

        public String getShipType() {
            return shipType != null ? shipType : "";
        }

        public void setShipType(String shipType) {
            this.shipType = shipType;
        }

        public String getShipName() {
            return shipName != null ? shipName : "";
        }

        public void setShipName(String shipName) {
            this.shipName = shipName;
        }

        public String getShipIdent() {
            return shipIdent != null ? shipIdent : "";
        }

        public void setShipIdent(String shipIdent) {
            this.shipIdent = shipIdent;
        }
    }
}
