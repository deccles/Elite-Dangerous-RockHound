package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.RedeemVoucherEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BountyCreditsTrackerTest {

    private final EliteLogParser parser = new EliteLogParser();
    private BountyCreditsTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BountyCreditsTracker();
    }

    @Test
    void bountyEventAddsTotalReward() {
        String line = "{ \"timestamp\":\"2018-04-17T11:11:02Z\", \"event\":\"Bounty\", "
                + "\"Rewards\":[{\"Faction\":\"Nehet Patron's Principles\",\"Reward\":5620}], "
                + "\"Target\":\"empire_eagle\", \"TotalReward\":5620, "
                + "\"VictimFaction\":\"Nehet Progressive Party\" }";
        assertTrue(tracker.applyJournalEvent((BountyEvent) parser.parseRecord(line)));
        assertEquals(5_620L, tracker.getUnclaimedTotal());
    }

    @Test
    void skimmerBountyUsesRewardField() {
        String line = "{\"timestamp\":\"2018-05-20T21:19:58Z\",\"event\":\"Bounty\","
                + "\"Faction\":\"HIP 18828 Empire Consulate\",\"Target\":\"Skimmer\","
                + "\"Reward\":1000,\"VictimFaction\":\"HIP 18828 Empire Consulate\"}";
        assertTrue(tracker.applyJournalEvent((BountyEvent) parser.parseRecord(line)));
        assertEquals(1_000L, tracker.getUnclaimedTotal());
    }

    @Test
    void redeemVoucherClearsTrackedTotal() {
        tracker.setUnclaimedTotal(12_280L);
        String line = "{ \"timestamp\":\"2016-06-10T14:32:03Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Amount\":1000 }";
        assertTrue(tracker.applyJournalEvent((RedeemVoucherEvent) parser.parseRecord(line)));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }

    @Test
    void redeemVoucherClearsEvenWhenFactionAmountsLessThanTracked() {
        tracker.setUnclaimedTotal(5_000L);
        String line = "{ \"timestamp\":\"2016-06-10T14:32:03Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Factions\":["
                + "{\"Faction\":\"Ed's 38\",\"Amount\":1000},"
                + "{\"Faction\":\"Zac's Lads\",\"Amount\":2000}"
                + "] }";
        assertTrue(tracker.applyJournalEvent((RedeemVoucherEvent) parser.parseRecord(line)));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }

    @Test
    void redeemVoucherClearsWhenFullyRedeemed() {
        tracker.setUnclaimedTotal(3_000L);
        String line = "{ \"timestamp\":\"2016-06-10T14:32:03Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Amount\":3000 }";
        assertTrue(tracker.applyJournalEvent((RedeemVoucherEvent) parser.parseRecord(line)));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }

    @Test
    void brokerRedeemClearsTrackedTotal() {
        tracker.setUnclaimedTotal(693_300L);
        String line = "{ \"timestamp\":\"2026-06-23T18:46:11Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Amount\":519975, "
                + "\"Factions\":[{\"Faction\":\"Pioneers and eXplorers\",\"Amount\":519975}], "
                + "\"BrokerPercentage\":25.000000 }";
        assertTrue(tracker.applyJournalEvent((RedeemVoucherEvent) parser.parseRecord(line)));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }

    @Test
    void redeemClearsEvenWhenNewBountiesWereEarnedBeforeTurnIn() {
        String earn1 = "{ \"timestamp\":\"2026-06-23T18:05:53Z\", \"event\":\"Bounty\", \"TotalReward\":390900 }";
        String earn2 = "{ \"timestamp\":\"2026-06-23T18:11:27Z\", \"event\":\"Bounty\", \"TotalReward\":302400 }";
        String earn3 = "{ \"timestamp\":\"2026-06-23T18:12:27Z\", \"event\":\"Bounty\", \"TotalReward\":50000 }";
        String redeem = "{ \"timestamp\":\"2026-06-23T18:46:11Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Amount\":519975, "
                + "\"Factions\":[{\"Faction\":\"Pioneers and eXplorers\",\"Amount\":519975}], "
                + "\"BrokerPercentage\":25.000000 }";

        tracker.applyJournalEvent(parser.parseRecord(earn1));
        tracker.applyJournalEvent(parser.parseRecord(earn2));
        tracker.applyJournalEvent(parser.parseRecord(earn3));
        assertEquals(743_300L, tracker.getUnclaimedTotal());
        tracker.applyJournalEvent(parser.parseRecord(redeem));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }

    @Test
    void combatBondRedeemIsIgnored() {
        tracker.setUnclaimedTotal(5_000L);
        String line = "{ \"timestamp\":\"2016-06-10T14:32:03Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"CombatBond\", \"Amount\":1000 }";
        assertFalse(tracker.applyJournalEvent((RedeemVoucherEvent) parser.parseRecord(line)));
        assertEquals(5_000L, tracker.getUnclaimedTotal());
    }

    @Test
    void earnedAmountFromJsonSumsRewardsWhenTotalRewardMissing() {
        JsonObject obj = JsonParser.parseString(
                "{ \"Rewards\":[{\"Reward\":1000},{\"Reward\":250}] }").getAsJsonObject();
        assertEquals(1_250L, BountyCreditsTracker.earnedAmountFromJson(obj));
    }

    @Test
    void applyJournalEventIgnoresZeroOrMissingAmounts() {
        BountyEvent zero = new BountyEvent(Instant.EPOCH, new JsonObject(), 0L);
        assertFalse(tracker.applyJournalEvent(zero));
        RedeemVoucherEvent zeroRedeem = new RedeemVoucherEvent(
                Instant.EPOCH, new JsonObject(), "bounty", 0L);
        tracker.setUnclaimedTotal(100L);
        assertFalse(tracker.applyJournalEvent(zeroRedeem));
        assertEquals(100L, tracker.getUnclaimedTotal());
    }

    @Test
    void journalFlowAccumulatesThenClears() {
        String earn1 = "{ \"timestamp\":\"2018-04-17T11:11:02Z\", \"event\":\"Bounty\", "
                + "\"TotalReward\":5620 }";
        String earn2 = "{ \"timestamp\":\"2018-04-17T11:12:02Z\", \"event\":\"Bounty\", "
                + "\"TotalReward\":4380 }";
        String redeem = "{ \"timestamp\":\"2016-06-10T14:32:03Z\", \"event\":\"RedeemVoucher\", "
                + "\"Type\":\"bounty\", \"Amount\":10000 }";

        tracker.applyJournalEvent(parser.parseRecord(earn1));
        tracker.applyJournalEvent(parser.parseRecord(earn2));
        assertEquals(10_000L, tracker.getUnclaimedTotal());
        tracker.applyJournalEvent(parser.parseRecord(redeem));
        assertEquals(0L, tracker.getUnclaimedTotal());
    }
}
