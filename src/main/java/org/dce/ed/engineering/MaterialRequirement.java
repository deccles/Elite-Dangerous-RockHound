package org.dce.ed.engineering;

import java.util.Objects;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MaterialRequirement other)) {
            return false;
        }
        return count == other.count && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, count);
    }
}
