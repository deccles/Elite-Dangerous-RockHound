package org.dce.ed.logreader;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates known Elite Dangerous journal event types.
 * The names match the "event" field in the journal JSON.
 */
public enum EliteEventType {
    FILEHEADER("Fileheader"),
    COMMANDER("Commander"),
    MATERIALS("Materials"),
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
    RECEIVE_TEXT("ReceiveText"),
    MUSIC("Music"),
    RESERVOIR_REPLENISHED("ReservoirReplenished"),
    PROSPECTED_ASTEROID("ProspectedAsteroid"),
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
}
