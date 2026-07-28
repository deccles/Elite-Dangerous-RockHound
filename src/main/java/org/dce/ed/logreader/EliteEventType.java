package org.dce.ed.logreader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerates known Elite Dangerous journal event types.
 * The names match the "event" field in the journal JSON.
 */
public enum EliteEventType {
    FILEHEADER("Fileheader"),
    COMMANDER("Commander"),
    MATERIALS("Materials"),
    MATERIAL_COLLECTED("MaterialCollected"),
    MATERIAL_DISCARDED("MaterialDiscarded"),
    MATERIAL_TRADE("MaterialTrade"),
    ENGINEER_CRAFT("EngineerCraft"),
    ENGINEER_CONTRIBUTION("EngineerContribution"),
    RANK("Rank"),
    PROGRESS("Progress"),
    REPUTATION("Reputation"),
    ENGINEER_PROGRESS("EngineerProgress"),
    LOAD_GAME("LoadGame"),
    LOCATION("Location"),
    START_JUMP("StartJump"),
    UNDOCKED("Undocked"),
    DOCKED("Docked"),
    SUPERCRUISE_ENTRY("SupercruiseEntry"),
    SUPERCRUISE_DESTINATION_DROP("SupercruiseDestinationDrop"),
    SUPERCRUISE_EXIT("SupercruiseExit"),
    FSD_JUMP("FSDJump"),
    FSD_TARGET("FSDTarget"),
    NAV_ROUTE("NavRoute"),
    NAV_ROUTE_CLEAR("NavRouteClear"),
    SAASIGNALS_FOUND("SAASignalsFound"),
    SCAN("Scan"),
    SCAN_BARYCENTRE("ScanBaryCentre"),
    FSS_DISCOVERY_SCAN("FSSDiscoveryScan"),
    FSS_ALL_BODIES_FOUND("FSSAllBodiesFound"),  
    FSS_BODY_SIGNAL_DISCOVERED("FSSBodySignals"),
    SAASCAN_COMPLETE("SAAScanComplete"),
    CODEX_ENTRY("CodexEntry"),
    LEAVE_BODY("LeaveBody"),
    APPROACH_BODY("ApproachBody"),
    TOUCHDOWN("Touchdown"),
    LIFTOFF("Liftoff"),
    LAUNCH_SRV("LaunchSRV"),
    DOCK_SRV("DockSRV"),
    SRV_DESTROYED("SRVDestroyed"),
    CARGO("Cargo"),
    LOADOUT("Loadout"),
    STORED_SHIPS("StoredShips"),
    SET_USER_SHIP_NAME("SetUserShipName"),
    SHIP_LOCKER("ShipLocker"),
    MISSIONS("Missions"),
    MISSION_ACCEPTED("MissionAccepted"),
    MISSION_COMPLETED("MissionCompleted"),
    MISSION_FAILED("MissionFailed"),
    MISSION_ABANDONED("MissionAbandoned"),
    MISSION_REDIRECTED("MissionRedirected"),
    CARGO_DEPOT("CargoDepot"),
    STATISTICS("Statistics"),
    CARRIER_LOCATION("CarrierLocation"),
    CARRIER_JUMP("CarrierJump"),
    CARRIER_JUMP_REQUEST("CarrierJumpRequest"),
    CARRIER_JUMP_CANCELLED("CarrierJumpCancelled"),
    /** Written when the carrier owner opens carrier management (journal). */
    CARRIER_STATS("CarrierStats"),
    SELL_ORGANIC_DATA("SellOrganicData"),
    /** Cartographics / exploration data sale (e.g. Universal Cartographics). */
    SELL_EXPLORATION_DATA("SellExplorationData"),
    /** Combat bounty awarded for a kill. */
    BOUNTY("Bounty"),
    /** Bounty or combat bond voucher redemption at a station or broker. */
    REDEEM_VOUCHER("RedeemVoucher"),
    RECEIVE_TEXT("ReceiveText"),
    /** Under fire — coincides with the in-game “Under Attack” voice line. */
    UNDER_ATTACK("UnderAttack"),
    MUSIC("Music"),
    RESERVOIR_REPLENISHED("ReservoirReplenished"),
    PROSPECTED_ASTEROID("ProspectedAsteroid"),
    SHIP_TARGETED("ShipTargeted"),
    STATUS("Status"), // from Status.json or live journal
    // Catch-all for events we don't model explicitly yet:
    SCAN_ORGANIC("ScanOrganic"),
    UNKNOWN("UNKNOWN") ;

    private final String journalName;
    private static final Map<String, EliteEventType> BY_NAME = new HashMap<>();

    static {
        for (EliteEventType t : values()) {
            BY_NAME.put(t.journalName, t);
        }
    }

    EliteEventType(String journalName) {
        this.journalName = journalName;
    }

    public String getJournalName() {
        return journalName;
    }

    public static EliteEventType fromJournalName(String name) {
        EliteEventType t = BY_NAME.get(name);
        return t != null ? t : UNKNOWN;
    }

    /** Journal events available in the Exec tab event picker (sorted by journal name). */
    public static EliteEventType[] execSelectableValues() {
        List<EliteEventType> out = new ArrayList<>();
        for (EliteEventType t : values()) {
            if (t == FILEHEADER || t == UNKNOWN) {
                continue;
            }
            out.add(t);
        }
        out.sort(Comparator.comparing(EliteEventType::getJournalName, String.CASE_INSENSITIVE_ORDER));
        return out.toArray(EliteEventType[]::new);
    }

    /** Brief help text for the Exec tab journal-event reference. */
    public String execHelpDescription() {
        return switch (this) {
            case APPROACH_BODY -> "Commander approaching a planet/moon surface body.";
            case BOUNTY -> "Combat bounty awarded for a kill.";
            case CARGO -> "Cargo manifest updated.";
            case CARGO_DEPOT -> "Mission cargo depot progress updated.";
            case CARRIER_JUMP -> "Fleet carrier arrived in a new system.";
            case CARRIER_JUMP_CANCELLED -> "Scheduled fleet carrier jump was cancelled.";
            case CARRIER_JUMP_REQUEST -> "Fleet carrier jump scheduled (countdown started).";
            case CARRIER_LOCATION -> "Fleet carrier location update (common when off-carrier).";
            case CARRIER_STATS -> "Carrier management opened; includes fuel and callsign.";
            case CODEX_ENTRY -> "Codex discovery recorded.";
            case COMMANDER -> "Commander record written at session start.";
            case DOCK_SRV -> "SRV docked back into the mothership.";
            case DOCKED -> "Commander docked at a station or fleet carrier.";
            case ENGINEER_CRAFT -> "Module engineered at an engineer (materials consumed, grade applied).";
            case ENGINEER_CONTRIBUTION -> "Items or credits donated to unlock/progress an engineer.";
            case ENGINEER_PROGRESS -> "Engineer unlock/progress updated.";
            case FSS_ALL_BODIES_FOUND -> "FSS completed for all bodies in the system.";
            case FSS_BODY_SIGNAL_DISCOVERED -> "FSS body signal discovered.";
            case FSS_DISCOVERY_SCAN -> "FSS discovery scan completed.";
            case FSD_JUMP -> "Ship completed hyperspace jump into a new system.";
            case FSD_TARGET -> "Galactic route FSD target selected.";
            case LAUNCH_SRV -> "SRV launched from the mothership.";
            case LEAVE_BODY -> "Commander left orbital cruise near a body.";
            case LIFTOFF -> "Ship lifted off from planetary surface.";
            case LOAD_GAME -> "Game loaded; commander, ship, and credits.";
            case LOADOUT -> "Ship loadout snapshot (modules, hull health at snapshot).";
            case LOCATION -> "Commander location updated (system, body, docked state).";
            case MATERIALS -> "Raw/engineered materials inventory updated.";
            case MATERIAL_COLLECTED -> "Engineering material collected.";
            case MATERIAL_DISCARDED -> "Engineering material discarded.";
            case MATERIAL_TRADE -> "Material trader exchange completed.";
            case MISSION_ABANDONED -> "Mission abandoned.";
            case MISSION_ACCEPTED -> "Mission accepted.";
            case MISSION_COMPLETED -> "Mission completed.";
            case MISSION_FAILED -> "Mission failed.";
            case MISSION_REDIRECTED -> "Mission destination changed.";
            case MISSIONS -> "Active missions list updated.";
            case MUSIC -> "Background music track changed.";
            case NAV_ROUTE -> "Galactic route plotted or updated.";
            case NAV_ROUTE_CLEAR -> "Galactic route cleared.";
            case PROGRESS -> "Powerplay/BGS progress updated.";
            case PROSPECTED_ASTEROID -> "Asteroid prospected in a ring.";
            case RANK -> "Combat/trade/exploration rank changed.";
            case RECEIVE_TEXT -> "Incoming comms message received.";
            case REDEEM_VOUCHER -> "Bounty/combat bond vouchers redeemed.";
            case REPUTATION -> "Faction reputation updated.";
            case RESERVOIR_REPLENISHED -> "Ship reservoir (fuel transfer) replenished.";
            case SAASCAN_COMPLETE -> "Detailed surface scan completed.";
            case SAASIGNALS_FOUND -> "SAA signals found on a body.";
            case SCAN -> "Detailed discovery scan of a body.";
            case SCAN_BARYCENTRE -> "System barycentre scanned.";
            case SCAN_ORGANIC -> "Organic scan recorded.";
            case SELL_EXPLORATION_DATA -> "Exploration data sold.";
            case SELL_ORGANIC_DATA -> "Exobiology data sold.";
            case SHIP_LOCKER -> "Ship locker contents updated.";
            case SHIP_TARGETED -> "Combat target lock / scan stage updated.";
            case SRV_DESTROYED -> "SRV destroyed.";
            case START_JUMP -> "Hyperspace or supercruise jump started.";
            case STATISTICS -> "Lifetime statistics updated.";
            case STATUS -> "High-frequency status snapshot (from Status.json).";
            case SUPERCRUISE_ENTRY -> "Entered supercruise in-system.";
            case SUPERCRUISE_DESTINATION_DROP ->
                    "Supercruise drop on a navigation target (station/carrier); fields Type, MarketID.";
            case SUPERCRUISE_EXIT -> "Dropped out of supercruise.";
            case TOUCHDOWN -> "Ship touched down on planetary surface.";
            case UNDOCKED -> "Commander undocked from a station or carrier.";
            default -> "Journal event \"" + journalName + "\".";
        };
    }
}
