package org.dce.ed.logreader.event;

import java.time.Instant;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal event: ShipTargeted (combat target lock / scan progress).
 */
public final class ShipTargetedEvent extends EliteLogEvent {

    private final boolean targetLocked;
    private final int scanStage;
    private final String pilotName;
    /** Raw journal {@code PilotName} (may be an {@code $npc_…} token). */
    private final String rawPilotName;
    private final Long bounty;
    private final String ship;
    private final String shipLocalised;
    private final String legalStatus;
    private final String faction;
    private final String pilotRank;
    private final Double shieldHealth;
    private final Double hullHealth;
    private final String squadronId;
    private final boolean player;

    public ShipTargetedEvent(Instant timestamp,
            JsonObject rawJson,
            boolean targetLocked,
            int scanStage,
            String pilotName,
            Long bounty) {
        this(timestamp, rawJson, targetLocked, scanStage, pilotName, null, bounty,
                null, null, null, null, null, null, null, null, false);
    }

    public ShipTargetedEvent(Instant timestamp,
            JsonObject rawJson,
            boolean targetLocked,
            int scanStage,
            String pilotName,
            String rawPilotName,
            Long bounty,
            String ship,
            String shipLocalised,
            String legalStatus,
            String faction,
            String pilotRank,
            Double shieldHealth,
            Double hullHealth,
            String squadronId,
            boolean player) {
        super(timestamp, EliteEventType.SHIP_TARGETED, rawJson);
        this.targetLocked = targetLocked;
        this.scanStage = scanStage;
        this.pilotName = pilotName;
        this.rawPilotName = rawPilotName;
        this.bounty = bounty;
        this.ship = ship;
        this.shipLocalised = shipLocalised;
        this.legalStatus = legalStatus;
        this.faction = faction;
        this.pilotRank = pilotRank;
        this.shieldHealth = shieldHealth;
        this.hullHealth = hullHealth;
        this.squadronId = squadronId;
        this.player = player;
    }

    public boolean isTargetLocked() {
        return targetLocked;
    }

    public int getScanStage() {
        return scanStage;
    }

    /** Localised pilot name when present, otherwise journal {@code PilotName}. */
    public String getPilotName() {
        return pilotName;
    }

    /** Raw {@code PilotName} from the journal line (may be an NPC localization token). */
    public String getRawPilotName() {
        return rawPilotName;
    }

    /** Bounty credits at scan stage 3 when the target is wanted; otherwise {@code null}. */
    public Long getBounty() {
        return bounty;
    }

    public String getShip() {
        return ship;
    }

    public String getShipLocalised() {
        return shipLocalised;
    }

    /** Display ship name: localised when present, otherwise internal ship id. */
    public String getShipDisplayName() {
        if (shipLocalised != null && !shipLocalised.isBlank()) {
            return shipLocalised;
        }
        return ship;
    }

    public String getLegalStatus() {
        return legalStatus;
    }

    public String getFaction() {
        return faction;
    }

    public String getPilotRank() {
        return pilotRank;
    }

    /** Shield percent 0–100 at scan stage ≥2, otherwise {@code null}. */
    public Double getShieldHealth() {
        return shieldHealth;
    }

    /** Hull percent 0–100 at scan stage ≥2, otherwise {@code null}. */
    public Double getHullHealth() {
        return hullHealth;
    }

    public String getSquadronId() {
        return squadronId;
    }

    /** True when the target appears to be a human player (not an NPC localization token). */
    public boolean isPlayer() {
        return player;
    }

    /**
     * Heuristic: NPC pilots use {@code $…} localization tokens in {@code PilotName};
     * players use a plain name and may include {@code SquadronID}.
     */
    public static boolean detectPlayer(String rawPilotName, String squadronId) {
        if (squadronId != null && !squadronId.isBlank()) {
            return true;
        }
        if (rawPilotName == null || rawPilotName.isBlank()) {
            return false;
        }
        return !rawPilotName.trim().startsWith("$");
    }
}
