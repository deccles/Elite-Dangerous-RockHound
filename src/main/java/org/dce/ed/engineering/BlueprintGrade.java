package org.dce.ed.engineering;

import java.util.ArrayList;
import java.util.List;

/**
 * One engineering blueprint grade (1–5) or experimental effect (grade 0).
 */
public final class BlueprintGrade {
    private final String id;
    private final int inaraBlueprintId;
    private final String moduleType;
    private final String name;
    private final int grade;
    private final boolean experimental;
    private final String description;
    private final String parentBlueprint;
    private final List<String> engineers;
    private final List<MaterialRequirement> materials;
    private final List<BlueprintModifier> modifiers;

    public BlueprintGrade(String id,
                          int inaraBlueprintId,
                          String moduleType,
                          String name,
                          int grade,
                          boolean experimental,
                          String description,
                          String parentBlueprint,
                          List<String> engineers,
                          List<MaterialRequirement> materials,
                          List<BlueprintModifier> modifiers) {
        this.id = id != null ? id : "";
        this.inaraBlueprintId = inaraBlueprintId;
        this.moduleType = moduleType != null ? moduleType : "";
        this.name = name != null ? name : "";
        this.grade = grade;
        this.experimental = experimental;
        this.description = description != null ? description : "";
        this.parentBlueprint = parentBlueprint != null ? parentBlueprint : "";
        this.engineers = engineers == null ? List.of() : List.copyOf(engineers);
        this.materials = materials == null ? List.of() : List.copyOf(materials);
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public String getId() {
        return id;
    }

    public int getInaraBlueprintId() {
        return inaraBlueprintId;
    }

    public String getModuleType() {
        return moduleType;
    }

    public String getName() {
        return name;
    }

    public int getGrade() {
        return grade;
    }

    public boolean isExperimental() {
        return experimental;
    }

    public String getDescription() {
        return description;
    }

    public String getParentBlueprint() {
        return parentBlueprint;
    }

    public List<String> getEngineers() {
        return engineers;
    }

    public List<MaterialRequirement> getMaterials() {
        return materials;
    }

    public List<BlueprintModifier> getModifiers() {
        return modifiers;
    }

    public String modifierSummary() {
        if (modifiers.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (BlueprintModifier m : modifiers) {
            parts.add(m.summary());
        }
        return String.join(", ", parts);
    }

    public String displayLabel() {
        if (experimental) {
            return name + " (experimental)";
        }
        return name + " G" + grade;
    }
}
