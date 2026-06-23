package org.dce.ed;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.RedeemVoucherEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Tracks unclaimed combat bounty credits from journal {@code Bounty} and {@code RedeemVoucher} events
 * for the status bar (similar to exobiology / geo survey totals).
 */
public final class BountyCreditsTracker {

    private long unclaimedTotal;

    public long getUnclaimedTotal() {
        return unclaimedTotal;
    }

    public void setUnclaimedTotal(long total) {
        unclaimedTotal = Math.max(0L, total);
    }

    /**
     * @return {@code true} when the running total changed
     */
    public boolean applyJournalEvent(EliteLogEvent event) {
        if (event instanceof BountyEvent bounty) {
            return addEarned(bounty.getTotalReward());
        }
        if (event instanceof RedeemVoucherEvent redeem && redeem.isBountyRedemption()) {
            return subtractRedeemed(redeem.getRedeemedAmount());
        }
        return false;
    }

    boolean addEarned(long amount) {
        if (amount <= 0L) {
            return false;
        }
        unclaimedTotal += amount;
        return true;
    }

    boolean subtractRedeemed(long amount) {
        if (amount <= 0L) {
            return false;
        }
        long before = unclaimedTotal;
        unclaimedTotal = Math.max(0L, unclaimedTotal - amount);
        return unclaimedTotal != before;
    }

    /** Face-value bounty earned from a {@code Bounty} journal line. */
    public static long earnedAmountFromJson(JsonObject obj) {
        if (obj == null) {
            return 0L;
        }
        if (obj.has("TotalReward") && !obj.get("TotalReward").isJsonNull()) {
            long total = positiveLongFromJson(obj.get("TotalReward"));
            if (total > 0L) {
                return total;
            }
        }
        if (obj.has("Reward") && !obj.get("Reward").isJsonNull()) {
            long reward = positiveLongFromJson(obj.get("Reward"));
            if (reward > 0L) {
                return reward;
            }
        }
        if (!obj.has("Rewards") || !obj.get("Rewards").isJsonArray()) {
            return 0L;
        }
        long sum = 0L;
        JsonArray rewards = obj.getAsJsonArray("Rewards");
        for (JsonElement el : rewards) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject entry = el.getAsJsonObject();
            if (entry.has("Reward") && !entry.get("Reward").isJsonNull()) {
                sum += positiveLongFromJson(entry.get("Reward"));
            }
        }
        return sum;
    }

    /**
     * Gross bounty voucher value redeemed from a {@code RedeemVoucher} journal line.
     * Prefers per-faction amounts when present; otherwise uses net {@code Amount}.
     */
    public static long redeemedBountyAmountFromJson(JsonObject obj) {
        if (obj == null) {
            return 0L;
        }
        String type = stringField(obj, "Type");
        if (type == null || !"bounty".equalsIgnoreCase(type.trim())) {
            return 0L;
        }
        if (obj.has("Factions") && obj.get("Factions").isJsonArray()) {
            long sum = 0L;
            JsonArray factions = obj.getAsJsonArray("Factions");
            for (JsonElement el : factions) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject entry = el.getAsJsonObject();
                if (entry.has("Amount") && !entry.get("Amount").isJsonNull()) {
                    sum += positiveLongFromJson(entry.get("Amount"));
                }
            }
            if (sum > 0L) {
                return sum;
            }
        }
        if (obj.has("Amount") && !obj.get("Amount").isJsonNull()) {
            return positiveLongFromJson(obj.get("Amount"));
        }
        return 0L;
    }

    private static long positiveLongFromJson(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return 0L;
        }
        try {
            return Math.max(0L, Math.round(el.getAsDouble()));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String stringField(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        try {
            String s = obj.get(field).getAsString();
            return s != null ? s.trim() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
