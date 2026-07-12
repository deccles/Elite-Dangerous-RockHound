package org.dce.ed.engineering;

/**
 * Material count required for one blueprint roll.
 */
public final class MaterialRequirement {
    private final String key;
    private final int count;

    public MaterialRequirement(String key, int count) {
        this.key = key != null ? key : "";
        this.count = Math.max(0, count);
    }

    public String getKey() {
        return key;
    }

    public int getCount() {
        return count;
    }
}
