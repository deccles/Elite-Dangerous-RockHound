package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Commander-wide (or optionally ship-scoped) materials reserve goal — e.g. a mission request.
 * Quantities are additional reserved stock beyond blueprint engineering needs.
 */
public final class MaterialsGoal {

    public static final int MAX_MATERIAL_COUNT = 300;

    private final String label;
    private final List<MaterialRequirement> materials;
    private final GoalPriority priority;
    private final boolean enabled;
    private final long shipId;
    private final String shipLabel;

    public MaterialsGoal(String label, List<MaterialRequirement> materials, GoalPriority priority) {
        this(label, materials, priority, true, EngineeringShipRef.UNKNOWN_SHIP_ID, "");
    }

    public MaterialsGoal(String label,
                         List<MaterialRequirement> materials,
                         GoalPriority priority,
                         long shipId,
                         String shipLabel) {
        this(label, materials, priority, true, shipId, shipLabel);
    }

    public MaterialsGoal(String label,
                         List<MaterialRequirement> materials,
                         GoalPriority priority,
                         boolean enabled,
                         long shipId,
                         String shipLabel) {
        this.label = label != null ? label.trim() : "";
        this.materials = canonicalize(materials);
        this.priority = GoalPriority.normalize(priority);
        this.enabled = enabled;
        this.shipId = shipId;
        this.shipLabel = shipLabel != null ? shipLabel : "";
    }

    private static List<MaterialRequirement> canonicalize(List<MaterialRequirement> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (MaterialRequirement req : source) {
            if (req == null) {
                continue;
            }
            String key = EngineeringMaterialKeys.canonicalKey(req.getKey());
            if (key.isBlank() || req.getCount() <= 0) {
                continue;
            }
            int count = Math.min(MAX_MATERIAL_COUNT, req.getCount());
            merged.merge(key, count, (a, b) -> Math.min(MAX_MATERIAL_COUNT, a + b));
        }
        List<MaterialRequirement> out = new ArrayList<>(merged.size());
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            out.add(new MaterialRequirement(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }

    public String getLabel() {
        return label;
    }

    public List<MaterialRequirement> getMaterials() {
        return materials;
    }

    public GoalPriority getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isIncludeInPlanning() {
        return enabled;
    }

    public long getShipId() {
        return shipId;
    }

    public String getShipLabel() {
        return shipLabel;
    }

    public boolean hasShip() {
        return shipId >= 0;
    }

    public boolean isValid() {
        return !label.isBlank() && !materials.isEmpty();
    }

    /** Flat required map (canonical keys). */
    public Map<String, Integer> requiredMaterials() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (MaterialRequirement req : materials) {
            out.merge(req.getKey(), req.getCount(), Integer::sum);
        }
        return out;
    }

    public boolean isSatisfied(Map<String, Integer> inventory) {
        for (Map.Entry<String, Integer> e : requiredMaterials().entrySet()) {
            int have = EngineeringMaterialKeys.countInInventory(inventory, e.getKey());
            if (have < e.getValue()) {
                return false;
            }
        }
        return !materials.isEmpty();
    }

    public Map<String, Integer> shortfalls(Map<String, Integer> inventory) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : requiredMaterials().entrySet()) {
            int have = EngineeringMaterialKeys.countInInventory(inventory, e.getKey());
            int shortfall = Math.max(0, e.getValue() - have);
            if (shortfall > 0) {
                out.put(e.getKey(), shortfall);
            }
        }
        return out;
    }

    public String targetSummary() {
        return targetSummary(null);
    }

    public String targetSummary(EngineeringDatabase database) {
        if (materials.isEmpty()) {
            return "—";
        }
        if (materials.size() == 1) {
            return "×" + materials.get(0).getCount();
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        String first = db.materialDisplayName(materials.get(0).getKey());
        int extra = materials.size() - 1;
        if (first != null && !first.isBlank() && first.length() <= 18) {
            return first + " +" + extra;
        }
        return materials.size() + " mats";
    }

    public String materialsTooltip(EngineeringDatabase database) {
        if (materials.isEmpty()) {
            return label;
        }
        StringBuilder sb = new StringBuilder();
        if (!label.isBlank()) {
            sb.append(label).append('\n');
        }
        EngineeringDatabase db = database != null ? database : EngineeringDatabase.getInstance();
        for (MaterialRequirement req : materials) {
            String name = db.materialDisplayName(req.getKey());
            sb.append(name).append(" ×").append(req.getCount()).append('\n');
        }
        return sb.toString().trim();
    }

    public MaterialsGoal withPriority(GoalPriority newPriority) {
        GoalPriority p = GoalPriority.normalize(newPriority);
        if (priority == p) {
            return this;
        }
        return new MaterialsGoal(label, materials, p, enabled, shipId, shipLabel);
    }

    public MaterialsGoal withEnabled(boolean include) {
        if (enabled == include) {
            return this;
        }
        return new MaterialsGoal(label, materials, priority, include, shipId, shipLabel);
    }

    public MaterialsGoal withIncludeInPlanning(boolean include) {
        return withEnabled(include);
    }

    public MaterialsGoal withLabel(String newLabel) {
        String text = newLabel != null ? newLabel.trim() : "";
        if (label.equals(text)) {
            return this;
        }
        return new MaterialsGoal(text, materials, priority, enabled, shipId, shipLabel);
    }

    public MaterialsGoal withMaterials(List<MaterialRequirement> newMaterials) {
        return new MaterialsGoal(label, newMaterials, priority, enabled, shipId, shipLabel);
    }

    public MaterialsGoal withShip(long newShipId, String newShipLabel) {
        String text = newShipLabel != null ? newShipLabel : "";
        if (shipId == newShipId && shipLabel.equals(text)) {
            return this;
        }
        return new MaterialsGoal(label, materials, priority, enabled, newShipId, text);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MaterialsGoal other)) {
            return false;
        }
        return label.equals(other.label)
                && materials.equals(other.materials)
                && priority == other.priority
                && enabled == other.enabled
                && shipId == other.shipId
                && shipLabel.equals(other.shipLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, materials, priority, enabled, shipId, shipLabel);
    }
}
