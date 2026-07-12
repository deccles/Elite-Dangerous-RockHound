package org.dce.ed.engineering;

/**
 * One row in the engineering shopping list.
 */
public final class ShoppingListRow {
    private final String materialKey;
    private final String displayName;
    private final String type;
    private final int required;
    private final int owned;
    private final int ownedAfterTrades;
    private final int shortfall;
    private final int shortfallAfterTrades;

    public ShoppingListRow(String materialKey,
                           String displayName,
                           String type,
                           int required,
                           int owned) {
        this(materialKey, displayName, type, required, owned, owned);
    }

    public ShoppingListRow(String materialKey,
                           String displayName,
                           String type,
                           int required,
                           int owned,
                           int ownedAfterTrades) {
        this.materialKey = materialKey != null ? materialKey : "";
        this.displayName = displayName != null ? displayName : materialKey;
        this.type = type != null ? type : "";
        this.required = Math.max(0, required);
        this.owned = Math.max(0, owned);
        this.ownedAfterTrades = Math.max(0, ownedAfterTrades);
        this.shortfall = Math.max(0, required - this.owned);
        this.shortfallAfterTrades = Math.max(0, required - this.ownedAfterTrades);
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getType() {
        return type;
    }

    public int getRequired() {
        return required;
    }

    public int getOwned() {
        return owned;
    }

    public int getOwnedAfterTrades() {
        return ownedAfterTrades;
    }

    public int getShortfall() {
        return shortfall;
    }

    /** Shortfall after applying all suggested material-trader swaps. */
    public int getShortfallAfterTrades() {
        return shortfallAfterTrades;
    }
}
