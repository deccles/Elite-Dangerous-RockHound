package org.dce.ed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.RedeemVoucherEvent;
import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.dce.ed.session.CombatSessionData;
import org.dce.ed.session.EdoSessionState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Combat-tab state: current lock, scanned wanted ships (local vs KWS remote), and kill victims
 * until bounty vouchers are redeemed.
 */
public final class CombatTargetTracker {

    private static final CombatTargetTracker INSTANCE = new CombatTargetTracker();

    public static CombatTargetTracker getInstance() {
        return INSTANCE;
    }

    /** Snapshot of the currently locked combat target. */
    public static final class LockedTarget {
        private final String pilotName;
        private final String shipDisplay;
        private final String legalStatus;
        private final Long bounty;
        private final Long localBounty;
        private final long remoteBounty;
        private final boolean warrantScanned;
        private final boolean player;

        public LockedTarget(String pilotName, String shipDisplay, String legalStatus,
                Long bounty, Long localBounty, long remoteBounty, boolean warrantScanned,
                boolean player) {
            this.pilotName = pilotName;
            this.shipDisplay = shipDisplay;
            this.legalStatus = legalStatus;
            this.bounty = bounty;
            this.localBounty = localBounty;
            this.remoteBounty = remoteBounty;
            this.warrantScanned = warrantScanned;
            this.player = player;
        }

        public String getPilotName() { return pilotName; }
        public String getShipDisplay() { return shipDisplay; }
        public String getLegalStatus() { return legalStatus; }
        public Long getBounty() { return bounty; }
        public Long getLocalBounty() { return localBounty; }
        public long getRemoteBounty() { return remoteBounty; }
        public boolean isWarrantScanned() { return warrantScanned; }
        public boolean isPlayer() { return player; }
    }

    /**
     * Stage-3 wanted scan retained until bounty redeem.
     * <p>
     * {@code warrantScanned} becomes true after a later stage-3 bounty sighting for the same pilot
     * (Kill Warrant Scanner pass — whether or not the total increased).
     */
    public static final class ScannedWantedShip {
        private final String pilotKey;
        private final String pilotName;
        private String shipDisplay;
        private String legalStatus;
        private final long firstBounty;
        private long currentBounty;
        private boolean warrantScanned;
        private final boolean player;

        ScannedWantedShip(String pilotKey, String pilotName, String shipDisplay, String legalStatus,
                long firstBounty, boolean player) {
            this.pilotKey = pilotKey;
            this.pilotName = pilotName;
            this.shipDisplay = shipDisplay;
            this.legalStatus = legalStatus;
            this.firstBounty = firstBounty;
            this.currentBounty = firstBounty;
            this.warrantScanned = false;
            this.player = player;
        }

        public String getPilotKey() { return pilotKey; }
        public String getPilotName() { return pilotName; }
        public String getShipDisplay() { return shipDisplay; }
        public String getLegalStatus() { return legalStatus; }
        public long getFirstBounty() { return firstBounty; }
        public long getCurrentBounty() { return currentBounty; }
        public long getRemoteBounty() { return Math.max(0L, currentBounty - firstBounty); }
        public boolean isWarrantScanned() { return warrantScanned; }
        public boolean isPlayer() { return player; }
    }

    /** One kill from a journal {@code Bounty} event. */
    public static final class KillVictim {
        private final Instant timestamp;
        /** Internal journal ship id (e.g. {@code asp_scout}). */
        private final String target;
        /** Localised / pretty ship name for UI. */
        private final String shipDisplay;
        /** Pilot when known from a prior scan/lock; otherwise {@code null}. */
        private final String pilotName;
        private final String victimFaction;
        private final long totalReward;
        private final long otherReward;
        private final int sharedWithOthers;
        private final boolean combatBond;

        public KillVictim(Instant timestamp, String target, String shipDisplay, String pilotName,
                String victimFaction, long totalReward, long otherReward, int sharedWithOthers,
                boolean combatBond) {
            this.timestamp = timestamp;
            this.target = target;
            this.shipDisplay = shipDisplay;
            this.pilotName = pilotName;
            this.victimFaction = victimFaction;
            this.totalReward = totalReward;
            this.otherReward = otherReward;
            this.sharedWithOthers = sharedWithOthers;
            this.combatBond = combatBond;
        }

        public Instant getTimestamp() { return timestamp; }
        public String getTarget() { return target; }
        public String getShipDisplay() {
            if (shipDisplay != null && !shipDisplay.isBlank()) {
                return shipDisplay;
            }
            return target;
        }
        public String getPilotName() { return pilotName; }
        public String getVictimFaction() { return victimFaction; }
        public long getTotalReward() { return totalReward; }
        public long getOtherReward() { return otherReward; }
        public int getSharedWithOthers() { return sharedWithOthers; }
        public boolean isCombatBond() { return combatBond; }
    }

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, ScannedWantedShip> scannedWanted = new LinkedHashMap<>();
    /** Clean pilots seen at scan stage 3, used only to recognize a later KWS pass. */
    private final Set<String> scannedCleanPilots = new LinkedHashSet<>();
    /** Internal ship id → localised display from recent {@link ShipTargetedEvent}s. */
    private final Map<String, String> shipDisplayById = new LinkedHashMap<>();
    /** Internal ship id → last known pilot display name. */
    private final Map<String, String> pilotByShipId = new LinkedHashMap<>();
    private final List<KillVictim> kills = new ArrayList<>();

    private volatile LockedTarget lockedTarget;
    private volatile long totalBountiesEarned;
    private volatile long totalOtherBounties;
    private volatile Runnable sessionStateChangeCallback;

    private CombatTargetTracker() {
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void setSessionStateChangeCallback(Runnable callback) {
        this.sessionStateChangeCallback = callback;
    }

    public LockedTarget getLockedTarget() {
        return lockedTarget;
    }

    public List<ScannedWantedShip> getScannedWantedShips() {
        List<ScannedWantedShip> out = new ArrayList<>(scannedWanted.values());
        out.sort(Comparator.comparingLong(ScannedWantedShip::getCurrentBounty).reversed());
        return Collections.unmodifiableList(out);
    }

    public List<KillVictim> getKills() {
        synchronized (kills) {
            List<KillVictim> out = new ArrayList<>(kills);
            out.sort(Comparator.comparing(
                    KillVictim::getTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            return Collections.unmodifiableList(out);
        }
    }

    public long getTotalBountiesEarned() {
        return totalBountiesEarned;
    }

    public long getTotalOtherBounties() {
        return totalOtherBounties;
    }

    public void resetForTests() {
        scannedWanted.clear();
        scannedCleanPilots.clear();
        shipDisplayById.clear();
        pilotByShipId.clear();
        synchronized (kills) {
            kills.clear();
        }
        lockedTarget = null;
        totalBountiesEarned = 0L;
        totalOtherBounties = 0L;
        sessionStateChangeCallback = null;
    }

    public void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        CombatSessionData combat = new CombatSessionData();
        List<CombatSessionData.ScannedWantedPersisted> scannedOut = new ArrayList<>();
        for (ScannedWantedShip s : scannedWanted.values()) {
            if (s == null || s.pilotKey == null || s.pilotKey.isBlank()) {
                continue;
            }
            CombatSessionData.ScannedWantedPersisted row = new CombatSessionData.ScannedWantedPersisted();
            row.setPilotKey(s.pilotKey);
            row.setPilotName(s.pilotName);
            row.setShipDisplay(s.shipDisplay);
            row.setLegalStatus(s.legalStatus);
            row.setFirstBounty(s.firstBounty);
            row.setCurrentBounty(s.currentBounty);
            row.setWarrantScanned(s.warrantScanned);
            row.setPlayer(s.player);
            scannedOut.add(row);
        }
        combat.setScanned(scannedOut);

        List<CombatSessionData.KillPersisted> killsOut = new ArrayList<>();
        synchronized (kills) {
            for (KillVictim k : kills) {
                if (k == null) {
                    continue;
                }
                CombatSessionData.KillPersisted row = new CombatSessionData.KillPersisted();
                if (k.getTimestamp() != null) {
                    row.setTimestamp(k.getTimestamp().toString());
                }
                row.setTarget(k.getTarget());
                row.setShipDisplay(k.getShipDisplay());
                row.setPilotName(k.getPilotName());
                row.setVictimFaction(k.getVictimFaction());
                row.setTotalReward(k.getTotalReward());
                row.setOtherReward(k.getOtherReward());
                row.setSharedWithOthers(k.getSharedWithOthers());
                row.setCombatBond(k.isCombatBond());
                killsOut.add(row);
            }
        }
        combat.setKills(killsOut);
        combat.setTotalBountiesEarned(totalBountiesEarned);
        combat.setTotalOtherBounties(totalOtherBounties);
        combat.setShipDisplayById(new LinkedHashMap<>(shipDisplayById));
        combat.setPilotByShipId(new LinkedHashMap<>(pilotByShipId));
        state.setCombat(combat);
    }

    public void applySessionState(EdoSessionState state) {
        scannedWanted.clear();
        scannedCleanPilots.clear();
        shipDisplayById.clear();
        pilotByShipId.clear();
        synchronized (kills) {
            kills.clear();
        }
        totalBountiesEarned = 0L;
        totalOtherBounties = 0L;
        // Do not clear lockedTarget here — live ShipTargeted may already have set it.
        if (state == null || state.getCombat() == null) {
            notifyListenersOnly();
            return;
        }
        CombatSessionData combat = state.getCombat();
        for (CombatSessionData.ScannedWantedPersisted row : combat.scannedOrEmpty()) {
            if (row == null || row.getPilotKey() == null || row.getPilotKey().isBlank()) {
                continue;
            }
            String key = row.getPilotKey().trim();
            long first = Math.max(0L, row.getFirstBounty());
            long current = Math.max(first, row.getCurrentBounty());
            ScannedWantedShip scanned = new ScannedWantedShip(
                    key,
                    row.getPilotName() != null && !row.getPilotName().isBlank() ? row.getPilotName() : key,
                    row.getShipDisplay(),
                    row.getLegalStatus(),
                    first,
                    row.isPlayer());
            scanned.currentBounty = current;
            scanned.warrantScanned = row.isWarrantScanned();
            scannedWanted.put(key, scanned);
        }
        synchronized (kills) {
            for (CombatSessionData.KillPersisted row : combat.killsOrEmpty()) {
                if (row == null) {
                    continue;
                }
                Instant when = null;
                if (row.getTimestamp() != null && !row.getTimestamp().isBlank()) {
                    try {
                        when = Instant.parse(row.getTimestamp().trim());
                    } catch (RuntimeException ignored) {
                    }
                }
                kills.add(new KillVictim(
                        when,
                        row.getTarget(),
                        row.getShipDisplay(),
                        row.getPilotName(),
                        row.getVictimFaction(),
                        Math.max(0L, row.getTotalReward()),
                        Math.max(0L, row.getOtherReward()),
                        Math.max(0, row.getSharedWithOthers()),
                        row.isCombatBond()));
            }
        }
        totalBountiesEarned = Math.max(0L, combat.getTotalBountiesEarned());
        totalOtherBounties = Math.max(0L, combat.getTotalOtherBounties());
        for (Map.Entry<String, String> e : combat.shipDisplayByIdOrEmpty().entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && !e.getValue().isBlank()) {
                shipDisplayById.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue().trim());
            }
        }
        for (Map.Entry<String, String> e : combat.pilotByShipIdOrEmpty().entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null && !e.getValue().isBlank()) {
                pilotByShipId.put(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue().trim());
            }
        }
        notifyListenersOnly();
    }

    public void applyJournalEvent(EliteLogEvent event) {
        if (event instanceof LoadGameEvent) {
            lockedTarget = null;
            scannedWanted.clear();
            scannedCleanPilots.clear();
            shipDisplayById.clear();
            pilotByShipId.clear();
            notifyListeners();
            return;
        }
        if (event instanceof ShipTargetedEvent st) {
            applyShipTargeted(st);
            return;
        }
        if (event instanceof BountyEvent bounty) {
            applyBounty(bounty);
            return;
        }
        if (event instanceof FactionKillBondEvent bond) {
            applyFactionKillBond(bond);
            return;
        }
        if (event instanceof RedeemVoucherEvent redeem) {
            if (redeem.isBountyRedemption()) {
                clearBountyStateOnRedeem();
            } else if (redeem.isCombatBondRedemption()) {
                clearCombatBondKillsOnRedeem();
            }
        }
    }

    void applyShipTargeted(ShipTargetedEvent event) {
        if (event == null) {
            return;
        }
        rememberShipIdentity(event);
        if (!event.isTargetLocked()) {
            if (lockedTarget != null) {
                lockedTarget = null;
                notifyListeners();
            }
            return;
        }

        String pilotKey = BountyScanTracker.pilotKey(event.getPilotName());
        LockedTarget previous = lockedTarget;
        boolean samePilot = previous != null
                && pilotKey != null
                && pilotKey.equalsIgnoreCase(BountyScanTracker.pilotKey(previous.getPilotName()));

        Long local = null;
        long remote = 0L;
        boolean warrantScanned = false;
        Long bounty = event.getBounty();
        boolean cleanWarrantScanned = false;

        if (pilotKey != null && event.getScanStage() == 3
                && "Clean".equalsIgnoreCase(event.getLegalStatus())) {
            cleanWarrantScanned = !scannedCleanPilots.add(pilotKey);
        }

        if (pilotKey != null && bounty != null && bounty > 0L && event.getScanStage() == 3) {
            ScannedWantedShip scanned = scannedWanted.get(pilotKey);
            if (scanned == null) {
                boolean locallyClean = scannedCleanPilots.contains(pilotKey);
                scanned = new ScannedWantedShip(
                        pilotKey,
                        displayPilot(event),
                        event.getShipDisplayName(),
                        locallyClean ? "Clean" : event.getLegalStatus(),
                        locallyClean ? 0L : bounty.longValue(),
                        event.isPlayer());
                if (locallyClean) {
                    scanned.currentBounty = bounty.longValue();
                    scanned.warrantScanned = true;
                }
                scannedWanted.put(pilotKey, scanned);
            } else {
                // A later stage-3 bounty sighting means a warrant scan completed (KWS),
                // even when the total did not increase.
                scanned.warrantScanned = true;
                if (bounty.longValue() > scanned.currentBounty) {
                    scanned.currentBounty = bounty.longValue();
                }
                if (event.getShipDisplayName() != null && !event.getShipDisplayName().isBlank()) {
                    scanned.shipDisplay = event.getShipDisplayName();
                }
                if (scanned.firstBounty > 0L
                        && event.getLegalStatus() != null && !event.getLegalStatus().isBlank()) {
                    scanned.legalStatus = event.getLegalStatus();
                }
            }
            local = Long.valueOf(scanned.firstBounty);
            remote = scanned.getRemoteBounty();
            warrantScanned = scanned.warrantScanned;
            bounty = Long.valueOf(scanned.currentBounty);
        } else if (pilotKey != null && scannedWanted.containsKey(pilotKey)) {
            ScannedWantedShip scanned = scannedWanted.get(pilotKey);
            local = Long.valueOf(scanned.firstBounty);
            remote = scanned.getRemoteBounty();
            warrantScanned = scanned.warrantScanned;
            if (bounty == null || bounty <= 0L) {
                bounty = Long.valueOf(scanned.currentBounty);
            }
        } else if (samePilot) {
            local = previous.getLocalBounty();
            remote = previous.getRemoteBounty();
            warrantScanned = previous.isWarrantScanned();
            if (bounty == null || bounty <= 0L) {
                bounty = previous.getBounty();
            }
        }
        warrantScanned = warrantScanned || cleanWarrantScanned;

        String pilot = displayPilot(event);
        if ("Unknown".equals(pilot) && samePilot) {
            pilot = previous.getPilotName();
        }
        String ship = event.getShipDisplayName();
        if ((ship == null || ship.isBlank()) && samePilot) {
            ship = previous.getShipDisplay();
        }
        String legal = event.getLegalStatus();
        ScannedWantedShip scanned = pilotKey != null ? scannedWanted.get(pilotKey) : null;
        if (scanned != null && scanned.firstBounty == 0L
                && "Clean".equalsIgnoreCase(scanned.legalStatus)) {
            legal = scanned.legalStatus;
        }
        if ((legal == null || legal.isBlank()) && samePilot) {
            legal = previous.getLegalStatus();
        }
        boolean player = event.isPlayer() || (samePilot && previous.isPlayer());

        lockedTarget = new LockedTarget(
                pilot,
                ship,
                legal,
                bounty,
                local,
                remote,
                warrantScanned,
                player);
        notifyListeners();
    }

    void applyBounty(BountyEvent event) {
        if (event == null) {
            return;
        }
        long total = Math.max(0L, event.getTotalReward());
        if (total <= 0L) {
            return;
        }
        long other = otherRewardFromJson(event.getRawJson(), total);
        int shared = event.getSharedWithOthers();
        String shipId = event.getTarget();
        String shipDisplay = firstNonBlank(event.getTargetLocalised(), resolveShipDisplay(shipId));
        String pilot = firstNonBlank(resolvePilotForKill(shipId), event.getPilotLocalised());
        removeScannedVictim(pilot);
        KillVictim victim = new KillVictim(
                event.getTimestamp(),
                shipId,
                shipDisplay,
                pilot,
                event.getVictimFaction(),
                total,
                other,
                shared,
                false);
        synchronized (kills) {
            kills.add(victim);
        }
        totalBountiesEarned += total;
        totalOtherBounties += other;
        notifyListeners();
    }

    void applyFactionKillBond(FactionKillBondEvent event) {
        if (event == null || event.getReward() <= 0L) {
            return;
        }
        LockedTarget target = lockedTarget;
        String pilot = target != null ? target.getPilotName() : null;
        String shipDisplay = target != null ? target.getShipDisplay() : null;
        removeScannedVictim(pilot);
        KillVictim victim = new KillVictim(
                event.getTimestamp(),
                null,
                shipDisplay,
                pilot,
                event.getVictimFaction(),
                event.getReward(),
                0L,
                0,
                true);
        synchronized (kills) {
            kills.add(victim);
        }
        notifyListeners();
    }

    private void clearCombatBondKillsOnRedeem() {
        boolean changed;
        synchronized (kills) {
            changed = kills.removeIf(KillVictim::isCombatBond);
        }
        if (changed) {
            notifyListeners();
        }
    }

    /**
     * Scanned list is living targets only — drop the victim (and clear lock)
     * when a bounty kill is recorded.
     */
    private void removeScannedVictim(String pilotName) {
        String pilotKey = BountyScanTracker.pilotKey(pilotName);
        if (pilotKey == null || pilotKey.isBlank()) {
            return;
        }
        String folded = foldPilotKey(pilotKey);
        String mapKey = null;
        for (String key : scannedWanted.keySet()) {
            if (foldPilotKey(key).equals(folded)) {
                mapKey = key;
                break;
            }
        }
        if (mapKey != null) {
            scannedWanted.remove(mapKey);
        }
        LockedTarget locked = lockedTarget;
        if (locked != null
                && foldPilotKey(BountyScanTracker.pilotKey(locked.getPilotName())).equals(folded)) {
            lockedTarget = null;
        }
    }

    void clearBountyStateOnRedeem() {
        boolean changed = false;
        synchronized (kills) {
            changed = kills.removeIf(kill -> !kill.isCombatBond());
        }
        if (!scannedWanted.isEmpty()) {
            scannedWanted.clear();
            changed = true;
        }
        if (totalBountiesEarned != 0L || totalOtherBounties != 0L) {
            totalBountiesEarned = 0L;
            totalOtherBounties = 0L;
            changed = true;
        }
        if (changed) {
            notifyListeners();
        }
    }

    private static String foldPilotKey(String pilotKey) {
        if (pilotKey == null) {
            return "";
        }
        return pilotKey.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Remote column text: {@code ?} = not warrant-scanned yet, {@code 0} = scanned with no
     * additional bounty, otherwise the compact amount is left to the UI via {@code remoteBounty}.
     */
    public static String remoteDisplayToken(long remoteBounty, boolean warrantScanned) {
        if (!warrantScanned) {
            return "?";
        }
        if (remoteBounty <= 0L) {
            return "0";
        }
        return null; // caller formats the amount
    }

    static long otherRewardFromJson(JsonObject obj, long totalReward) {
        if (obj == null || !obj.has("Rewards") || !obj.get("Rewards").isJsonArray()) {
            return 0L;
        }
        JsonArray rewards = obj.getAsJsonArray("Rewards");
        if (rewards.size() <= 1) {
            return 0L;
        }
        long max = 0L;
        long sum = 0L;
        for (JsonElement el : rewards) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject entry = el.getAsJsonObject();
            if (!entry.has("Reward") || entry.get("Reward").isJsonNull()) {
                continue;
            }
            try {
                long r = Math.max(0L, Math.round(entry.get("Reward").getAsDouble()));
                sum += r;
                if (r > max) {
                    max = r;
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (sum <= 0L) {
            return 0L;
        }
        long other = sum - max;
        if (other > 0L) {
            return other;
        }
        return Math.max(0L, totalReward - max);
    }

    static int sharedWithOthers(JsonObject obj) {
        if (obj == null || !obj.has("SharedWithOthers") || obj.get("SharedWithOthers").isJsonNull()) {
            return 0;
        }
        try {
            return Math.max(0, obj.get("SharedWithOthers").getAsInt());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void rememberShipIdentity(ShipTargetedEvent event) {
        String shipId = normalizeShipId(event.getShip());
        if (shipId == null) {
            return;
        }
        String display = event.getShipDisplayName();
        if (display != null && !display.isBlank()) {
            shipDisplayById.put(shipId, display.trim());
        }
        String pilot = displayPilot(event);
        if (pilot != null && !pilot.isBlank() && !"Unknown".equals(pilot)) {
            pilotByShipId.put(shipId, pilot);
        }
    }

    private String resolveShipDisplay(String shipIdRaw) {
        String shipId = normalizeShipId(shipIdRaw);
        if (shipId == null) {
            return shipIdRaw;
        }
        String cached = shipDisplayById.get(shipId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        LockedTarget locked = lockedTarget;
        if (locked != null && locked.getShipDisplay() != null && !locked.getShipDisplay().isBlank()) {
            // Best-effort when killing the current lock without a prior id cache hit.
            return locked.getShipDisplay();
        }
        return prettyShipId(shipIdRaw);
    }

    private String resolvePilotForKill(String shipIdRaw) {
        String shipId = normalizeShipId(shipIdRaw);
        if (shipId != null) {
            String cached = pilotByShipId.get(shipId);
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        }
        LockedTarget locked = lockedTarget;
        if (locked != null && locked.getPilotName() != null && !locked.getPilotName().isBlank()) {
            return locked.getPilotName();
        }
        return null;
    }

    static String normalizeShipId(String shipId) {
        if (shipId == null || shipId.isBlank()) {
            return null;
        }
        return shipId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Fallback when no Ship_Localised was seen: {@code asp_scout} → {@code Asp Scout}. */
    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return fallback;
    }

    static String prettyShipId(String shipId) {
        if (shipId == null || shipId.isBlank()) {
            return shipId;
        }
        return org.dce.ed.ShipTypeNames.display(shipId);
    }

    private static String displayPilot(ShipTargetedEvent event) {
        String name = event.getPilotName();
        if (name != null && !name.isBlank()) {
            String key = BountyScanTracker.pilotKey(name);
            return key != null ? key : name.trim();
        }
        return "Unknown";
    }

    private void notifyListeners() {
        notifyListenersOnly();
        requestSessionPersist();
    }

    private void notifyListenersOnly() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void requestSessionPersist() {
        Runnable cb = sessionStateChangeCallback;
        if (cb != null) {
            try {
                cb.run();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
