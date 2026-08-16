package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * RedeemVoucher – written when combat bounties or bonds are claimed at a station or broker.
 */
public final class RedeemVoucherEvent extends EliteLogEvent {

    private final String voucherType;
    private final long redeemedAmount;

    public RedeemVoucherEvent(Instant timestamp, JsonObject rawJson, String voucherType, long redeemedAmount) {
        super(timestamp, EliteEventType.REDEEM_VOUCHER, rawJson);
        this.voucherType = voucherType;
        this.redeemedAmount = redeemedAmount;
    }

    public String getVoucherType() {
        return voucherType;
    }

    public long getRedeemedAmount() {
        return redeemedAmount;
    }

    public boolean isBountyRedemption() {
        return voucherType != null && "bounty".equalsIgnoreCase(voucherType.trim());
    }

    public boolean isCombatBondRedemption() {
        return voucherType != null && "CombatBond".equalsIgnoreCase(voucherType.trim());
    }
}
