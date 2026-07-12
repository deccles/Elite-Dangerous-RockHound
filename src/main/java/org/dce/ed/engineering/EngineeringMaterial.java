package org.dce.ed.engineering;

/**
 * Engineering material metadata (journal key, trader group, grade).
 */
public final class EngineeringMaterial {
    private final String key;
    private final String name;
    private final String type;
    private final String subtype;
    private final int grade;

    public EngineeringMaterial(String key, String name, String type, String subtype, int grade) {
        this.key = key != null ? key : "";
        this.name = name != null ? name : "";
        this.type = type != null ? type : "";
        this.subtype = subtype != null ? subtype : "";
        this.grade = Math.max(1, grade);
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String subtype() {
        return subtype;
    }

    public String getSubtype() {
        return subtype;
    }

    public int getGrade() {
        return grade;
    }
}
