package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
    /** Planning priority (High / Medium / Low). Legacy Disabled is normalized away. */
    private final GoalPriority priority;
    /** When false, omitted from materials and trade totals (priority is preserved). */
    private final boolean enabled;
    /** True after the goal's experimental effect has been applied on the in-progress unit. */
    private final boolean experimentalApplied;
    /** How many modules to engineer (e.g. four gimbal weapons). */
    private final int quantity;
    /** How many modules are fully finished (grades + experimental when required). */
    private final int completedUnits;
    /** Elite ShipID for this goal's hull; {@link EngineeringShipRef#UNKNOWN_SHIP_ID} if unset. */
    private final long shipId;
    /** Cached display label for the ship (name / type). */
    private final String shipLabel;
    /**
     * Optional journal slot key this goal is pinned to (e.g. {@code Slot08_Size4}).
     * Empty = unscoped (best-effort 1:1 match among same module type on the ship).
     */
    private final List<String> targetSlots;

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
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade, experimentalId,
                GoalPriority.MEDIUM);
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
                experimentalId, GoalPriority.MEDIUM, false, 1, 0,
                EngineeringShipRef.UNKNOWN_SHIP_ID, "", includeInPlanning);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, false);
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
                experimentalId, GoalPriority.MEDIUM, experimentalApplied, 1, 0,
                EngineeringShipRef.UNKNOWN_SHIP_ID, "", includeInPlanning);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, experimentalApplied, 1, 0);
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
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, GoalPriority.MEDIUM, experimentalApplied, quantity,
                completedUnits, EngineeringShipRef.UNKNOWN_SHIP_ID, "", includeInPlanning);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, experimentalApplied, quantity, completedUnits,
                EngineeringShipRef.UNKNOWN_SHIP_ID, "");
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
                           int completedUnits,
                           long shipId,
                           String shipLabel) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, GoalPriority.MEDIUM, experimentalApplied, quantity,
                completedUnits, shipId, shipLabel, includeInPlanning);
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits,
                           long shipId,
                           String shipLabel) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, experimentalApplied, quantity, completedUnits, shipId, shipLabel,
                priority == null || priority.isActive());
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits,
                           long shipId,
                           String shipLabel,
                           boolean enabled) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, experimentalApplied, quantity, completedUnits,
                shipId, shipLabel, enabled, "");
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits,
                           long shipId,
                           String shipLabel,
                           boolean enabled,
                           String targetSlot) {
        this(blueprintId, moduleType, blueprintName, fromGrade, craftsAtCurrentGrade, targetGrade,
                experimentalId, priority, experimentalApplied, quantity, completedUnits,
                shipId, shipLabel, enabled,
                targetSlot == null || targetSlot.isBlank() ? List.of() : List.of(targetSlot));
    }

    public EngineeringGoal(String blueprintId,
                           String moduleType,
                           String blueprintName,
                           int fromGrade,
                           int craftsAtCurrentGrade,
                           int targetGrade,
                           String experimentalId,
                           GoalPriority priority,
                           boolean experimentalApplied,
                           int quantity,
                           int completedUnits,
                           long shipId,
                           String shipLabel,
                           boolean enabled,
                           List<String> targetSlots) {
        this.blueprintId = blueprintId != null ? blueprintId : "";
        this.moduleType = moduleType != null ? moduleType : "";
        this.blueprintName = blueprintName != null ? blueprintName : "";
        this.fromGrade = Math.max(0, fromGrade);
        this.craftsAtCurrentGrade = clampCrafts(craftsAtCurrentGrade);
        this.targetGrade = Math.max(1, targetGrade);
        this.experimentalId = experimentalId != null ? experimentalId : "";
        this.priority = GoalPriority.normalize(priority);
        this.enabled = enabled;
        this.experimentalApplied = experimentalApplied;
        this.quantity = Math.max(1, quantity);
        this.completedUnits = Math.max(0, Math.min(completedUnits, this.quantity));
        this.shipId = shipId;
        this.shipLabel = shipLabel != null ? shipLabel : "";
        LinkedHashSet<String> normalizedSlots = new LinkedHashSet<>();
        if (targetSlots != null) {
            for (String slot : targetSlots) {
                if (slot != null && !slot.isBlank()) {
                    normalizedSlots.add(slot.trim());
                }
            }
        }
        this.targetSlots = List.copyOf(normalizedSlots);
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

    public GoalPriority getPriority() {
        return priority;
    }

    /** True when this goal is included in materials and trade suggestions. */
    public boolean isIncludeInPlanning() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
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

    public long getShipId() {
        return shipId;
    }

    public String getShipLabel() {
        return shipLabel;
    }

    /** Journal slot key when this goal is pinned to a fitted module; empty if unscoped. */
    public String getTargetSlot() {
        return targetSlots.isEmpty() ? "" : targetSlots.get(0);
    }

    /** All journal slots represented by this goal; grouped imports may contain several. */
    public List<String> getTargetSlots() {
        return targetSlots;
    }

    public boolean hasTargetSlot() {
        return !targetSlots.isEmpty();
    }

    public boolean targetsSlot(String slot) {
        if (slot == null || slot.isBlank()) {
            return false;
        }
        return targetSlots.stream().anyMatch(target -> target.equalsIgnoreCase(slot.trim()));
    }

    public boolean hasShip() {
        return shipId >= 0;
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
                priority,
                experimentalApplied,
                quantity,
                completedUnits,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
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
                priority,
                applied,
                quantity,
                completedUnits,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
    }

    public EngineeringGoal withPriority(GoalPriority newPriority) {
        GoalPriority p = GoalPriority.normalize(newPriority);
        if (priority == p) {
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
                p,
                experimentalApplied,
                quantity,
                completedUnits,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
    }

    public EngineeringGoal withIncludeInPlanning(boolean include) {
        return withEnabled(include);
    }

    public EngineeringGoal withEnabled(boolean include) {
        if (enabled == include) {
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
                priority,
                experimentalApplied,
                quantity,
                completedUnits,
                shipId,
                shipLabel,
                include,
                targetSlots);
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
                priority,
                experimentalApplied,
                q,
                Math.min(completedUnits, q),
                shipId,
                shipLabel,
                enabled,
                targetSlots);
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
                priority,
                experimentalApplied,
                quantity,
                units,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
    }

    /**
     * Updates user-facing settings from the edit dialog while preserving journal progress where possible.
     *
     * <p>Raising the target grade clears {@code completedUnits}: those units were finished for the
     * previous target only. Callers should re-bootstrap from the journal so multi-module progress
     * is recomputed against the new target.
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
        int crafts = craftsAtCurrentGrade;
        if (grade > targetGrade) {
            // Previously "done" units only met the old target.
            units = 0;
        }
        if (progFrom >= grade) {
            crafts = 0;
        } else if (progFrom < fromGrade) {
            crafts = 0;
        }
        return new EngineeringGoal(
                blueprintId,
                moduleType,
                blueprintName,
                progFrom,
                crafts,
                grade,
                expId,
                priority,
                expApplied,
                q,
                units,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
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
                priority,
                false,
                quantity,
                0,
                shipId,
                shipLabel,
                enabled,
                targetSlots);
    }

    public EngineeringGoal withShip(long newShipId, String newShipLabel) {
        String label = newShipLabel != null ? newShipLabel : "";
        if (shipId == newShipId && shipLabel.equals(label)) {
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
                priority,
                experimentalApplied,
                quantity,
                completedUnits,
                newShipId,
                label,
                enabled,
                targetSlots);
    }

    public EngineeringGoal withTargetSlot(String newTargetSlot) {
        String slot = newTargetSlot != null ? newTargetSlot : "";
        return withTargetSlots(slot.isBlank() ? List.of() : List.of(slot));
    }

    public EngineeringGoal withTargetSlots(List<String> newTargetSlots) {
        List<String> slots = newTargetSlots != null ? new ArrayList<>(newTargetSlots) : List.of();
        if (targetSlots.equals(slots)) {
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
                priority,
                experimentalApplied,
                quantity,
                completedUnits,
                shipId,
                shipLabel,
                enabled,
                slots);
    }

    public EngineeringGoal withUserSettings(int newTargetGrade,
                                           String newExperimentalId,
                                           int newQuantity,
                                           long newShipId,
                                           String newShipLabel) {
        return withUserSettings(newTargetGrade, newExperimentalId, newQuantity)
                .withShip(newShipId, newShipLabel);
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
                && priority == other.priority
                && enabled == other.enabled
                && experimentalApplied == other.experimentalApplied
                && quantity == other.quantity
                && completedUnits == other.completedUnits
                && shipId == other.shipId
                && shipLabel.equals(other.shipLabel)
                && targetSlots.equals(other.targetSlots);
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
        result = 31 * result + priority.hashCode();
        result = 31 * result + Boolean.hashCode(enabled);
        result = 31 * result + Boolean.hashCode(experimentalApplied);
        result = 31 * result + quantity;
        result = 31 * result + completedUnits;
        result = 31 * result + Long.hashCode(shipId);
        result = 31 * result + shipLabel.hashCode();
        result = 31 * result + targetSlots.hashCode();
        return result;
    }
}
