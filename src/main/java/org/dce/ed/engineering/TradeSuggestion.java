package org.dce.ed.engineering;

/**
 * Suggested material trader exchange.
 */
public final class TradeSuggestion {
    private final String fromKey;
    private final String fromName;
    private final int fromCount;
    private final String toKey;
    private final String toName;
    private final int toCount;
    private final boolean sameGroup;
    private final String traderType;

    public TradeSuggestion(String fromKey,
                           String fromName,
                           int fromCount,
                           String toKey,
                           String toName,
                           int toCount,
                           boolean sameGroup,
                           String traderType) {
        this.fromKey = fromKey;
        this.fromName = fromName;
        this.fromCount = fromCount;
        this.toKey = toKey;
        this.toName = toName;
        this.toCount = toCount;
        this.sameGroup = sameGroup;
        this.traderType = traderType != null ? traderType : "";
    }

    /** @deprecated use constructor with {@code traderType} */
    public TradeSuggestion(String fromKey,
                           String fromName,
                           int fromCount,
                           String toKey,
                           String toName,
                           int toCount,
                           boolean sameGroup) {
        this(fromKey, fromName, fromCount, toKey, toName, toCount, sameGroup, "");
    }

    public String getFromKey() {
        return fromKey;
    }

    public String getFromName() {
        return fromName;
    }

    public int getFromCount() {
        return fromCount;
    }

    public String getToKey() {
        return toKey;
    }

    public String getToName() {
        return toName;
    }

    public int getToCount() {
        return toCount;
    }

    public boolean isSameGroup() {
        return sameGroup;
    }

    /** Material trader category: Raw, Manufactured, or Encoded. */
    public String getTraderType() {
        return traderType;
    }

    public String summary() {
        return "Trade " + fromCount + "× " + fromName + " → " + toCount + "× " + toName;
    }
}
