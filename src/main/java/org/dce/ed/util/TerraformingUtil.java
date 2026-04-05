package org.dce.ed.util;

import java.util.Locale;

/**
 * Parsing {@link org.dce.ed.state.BodyInfo#getTerraformState()} for exploration rules.
 * <p>
 * {@code "Not terraformable"} incorrectly matches naive {@code String.contains("terraformable")};
 * that inflated {@link ExplorationBodyCredits#explorationK} and table estimates vs tools like ED Exploration Buddy.
 */
public final class TerraformingUtil {

    private TerraformingUtil() {
    }

    /**
     * Whether the body qualifies for the +terraformable exploration coefficient (Elite journal / EDSM strings).
     */
    public static boolean isTerraformableExplorationTier(String terraformState) {
        if (terraformState == null || terraformState.isBlank()) {
            return false;
        }
        String t = terraformState.toLowerCase(Locale.ROOT);
        if (t.contains("not terraformable")) {
            return false;
        }
        if (t.contains("non terraformable") || t.contains("non-terraformable")) {
            return false;
        }
        if (t.contains("terraformable")) {
            return true;
        }
        return t.contains("candidate for terraforming");
    }
}
