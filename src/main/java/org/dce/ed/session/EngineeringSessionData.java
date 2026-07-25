package org.dce.ed.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Engineering tab goals persisted in {@link EdoSessionState}.
 */
public final class EngineeringSessionData {

    private List<EngineeringGoalPersisted> goals = new ArrayList<>();
    private List<MaterialsGoalPersisted> materialGoals = new ArrayList<>();
    private List<ShipPersisted> knownShips = new ArrayList<>();
    /** null = All ships. */
    private Long goalsShipFilterId;

    /** When true and a ship filter is set, materials/trades exclude other ships' goals. */
    private Boolean hideMatsFromOtherShips;

    public List<EngineeringGoalPersisted> getGoals() {
        return goals;
    }

    public void setGoals(List<EngineeringGoalPersisted> goals) {
        this.goals = goals != null ? goals : new ArrayList<>();
    }

    public List<EngineeringGoalPersisted> goalsOrEmpty() {
        return goals != null ? goals : new ArrayList<>();
    }

    public List<MaterialsGoalPersisted> getMaterialGoals() {
        return materialGoals;
    }

    public void setMaterialGoals(List<MaterialsGoalPersisted> materialGoals) {
        this.materialGoals = materialGoals != null ? materialGoals : new ArrayList<>();
    }

    public List<MaterialsGoalPersisted> materialGoalsOrEmpty() {
        return materialGoals != null ? materialGoals : new ArrayList<>();
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

    /** null = legacy sessions (treat as true). */
    public Boolean getHideMatsFromOtherShips() {
        return hideMatsFromOtherShips;
    }

    public void setHideMatsFromOtherShips(Boolean hideMatsFromOtherShips) {
        this.hideMatsFromOtherShips = hideMatsFromOtherShips;
    }

    public boolean hideMatsFromOtherShipsOrDefault() {
        return hideMatsFromOtherShips == null || hideMatsFromOtherShips.booleanValue();
    }

    public static final class MaterialsGoalPersisted {
        private String label;
        private List<MaterialNeedPersisted> materials = new ArrayList<>();
        /** {@code HIGH}/{@code MEDIUM}/{@code LOW}; legacy {@code DISABLED} migrates to enabled=false. */
        private String priority;
        /** {@code null} = enabled (legacy). When false, omitted from materials/trades. */
        private Boolean includeInPlanning;
        /** null / missing = unset (commander-wide). */
        private Long shipId;
        private String shipLabel;

        public String getLabel() {
            return label != null ? label : "";
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public List<MaterialNeedPersisted> getMaterials() {
            return materials;
        }

        public void setMaterials(List<MaterialNeedPersisted> materials) {
            this.materials = materials != null ? materials : new ArrayList<>();
        }

        public List<MaterialNeedPersisted> materialsOrEmpty() {
            return materials != null ? materials : new ArrayList<>();
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public org.dce.ed.engineering.GoalPriority priorityOrDefault() {
            if (priority != null && !priority.isBlank()) {
                return org.dce.ed.engineering.GoalPriority.normalize(
                        org.dce.ed.engineering.GoalPriority.parse(priority));
            }
            return org.dce.ed.engineering.GoalPriority.MEDIUM;
        }

        public Boolean getIncludeInPlanning() {
            return includeInPlanning;
        }

        public void setIncludeInPlanning(Boolean includeInPlanning) {
            this.includeInPlanning = includeInPlanning;
        }

        public boolean includeInPlanningOrDefault() {
            if (includeInPlanning != null) {
                return includeInPlanning;
            }
            if (priority != null && "DISABLED".equalsIgnoreCase(priority.trim())) {
                return false;
            }
            return true;
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

    public static final class MaterialNeedPersisted {
        private String key;
        private int count;

        public MaterialNeedPersisted() {
        }

        public MaterialNeedPersisted(String key, int count) {
            this.key = key;
            this.count = count;
        }

        public String getKey() {
            return key != null ? key : "";
        }

        public void setKey(String key) {
            this.key = key;
        }

        public int getCount() {
            return Math.max(0, count);
        }

        public void setCount(int count) {
            this.count = Math.max(0, count);
        }
    }

    public static final class EngineeringGoalPersisted {
        private String blueprintId;
        private String moduleType;
        private String blueprintName;
        private int fromGrade;
        private int craftsAtCurrentGrade;
        private int targetGrade;
        private String experimentalId;
        /** {@code null} = include (legacy sessions). Prefer this over legacy DISABLED priority. */
        private Boolean includeInPlanning;
        /** {@code HIGH}/{@code MEDIUM}/{@code LOW}; legacy {@code DISABLED} migrates to enabled=false. */
        private String priority;
        private boolean experimentalApplied;
        /** {@code null} or {@code <= 0} = 1 (legacy sessions). */
        private Integer quantity;
        private int completedUnits;
        /** null / missing = unset (legacy). */
        private Long shipId;
        private String shipLabel;
        /** Journal slot pin; null/blank = unscoped (legacy). */
        private String targetSlot;

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
            if (includeInPlanning != null) {
                return includeInPlanning;
            }
            if (priority != null && "DISABLED".equalsIgnoreCase(priority.trim())) {
                return false;
            }
            return true;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        /** Resolved priority for load; never returns legacy DISABLED. */
        public org.dce.ed.engineering.GoalPriority priorityOrDefault() {
            if (priority != null && !priority.isBlank()) {
                return org.dce.ed.engineering.GoalPriority.normalize(
                        org.dce.ed.engineering.GoalPriority.parse(priority));
            }
            return org.dce.ed.engineering.GoalPriority.MEDIUM;
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

        public String getTargetSlot() {
            return targetSlot != null ? targetSlot : "";
        }

        public void setTargetSlot(String targetSlot) {
            this.targetSlot = targetSlot != null ? targetSlot : "";
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
