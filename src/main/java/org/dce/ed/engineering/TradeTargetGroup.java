package org.dce.ed.engineering;

import java.util.List;

/**
 * One material shortfall and alternative trader exchanges that can fill it (pick one).
 */
public final class TradeTargetGroup {
    private final String toKey;
    private final String toName;
    private final String traderType;
    private final int shortfall;
    private final List<TradeSuggestion> options;

    public TradeTargetGroup(String toKey,
                            String toName,
                            String traderType,
                            int shortfall,
                            List<TradeSuggestion> options) {
        this.toKey = toKey != null ? toKey : "";
        this.toName = toName != null ? toName : "";
        this.traderType = traderType != null ? traderType : "";
        this.shortfall = Math.max(0, shortfall);
        this.options = options != null ? List.copyOf(options) : List.of();
    }

    public String getToKey() {
        return toKey;
    }

    public String getToName() {
        return toName;
    }

    public String getTraderType() {
        return traderType;
    }

    public int getShortfall() {
        return shortfall;
    }

    public List<TradeSuggestion> getOptions() {
        return options;
    }
}
