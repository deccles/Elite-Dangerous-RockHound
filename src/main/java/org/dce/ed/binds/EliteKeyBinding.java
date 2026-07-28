package org.dce.ed.binds;

import java.util.List;
import java.util.Objects;

/**
 * A resolved keyboard binding: virtual-key code plus optional modifier keys, with a short display label.
 */
public final class EliteKeyBinding {

    private final int virtualKey;
    private final List<Integer> modifierVirtualKeys;
    private final String displayLabel;

    public EliteKeyBinding(int virtualKey, List<Integer> modifierVirtualKeys, String displayLabel) {
        this.virtualKey = virtualKey;
        this.modifierVirtualKeys = modifierVirtualKeys == null || modifierVirtualKeys.isEmpty()
                ? List.of()
                : List.copyOf(modifierVirtualKeys);
        this.displayLabel = displayLabel != null ? displayLabel : "";
    }

    public int getVirtualKey() {
        return virtualKey;
    }

    public List<Integer> getModifierVirtualKeys() {
        return modifierVirtualKeys;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EliteKeyBinding other)) {
            return false;
        }
        return virtualKey == other.virtualKey
                && modifierVirtualKeys.equals(other.modifierVirtualKeys)
                && displayLabel.equals(other.displayLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(virtualKey, modifierVirtualKeys, displayLabel);
    }

    @Override
    public String toString() {
        return displayLabel;
    }
}
