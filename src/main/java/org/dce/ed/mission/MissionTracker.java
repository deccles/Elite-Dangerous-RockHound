package org.dce.ed.mission;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.dce.ed.CargoMonitor;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.MissionAbandonedEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionFailedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.MissionSessionData;
import org.dce.ed.session.MissionSessionData.MissionRecordPersisted;

/**
 * Commander-scoped active mission board; hydrated from {@link EdoSessionState} and journal events.
 */
public final class MissionTracker {

    private final Map<Long, MissionRecord> activeById = new ConcurrentHashMap<>();
    private final Set<Long> dismissedRedirectIds = ConcurrentHashMap.newKeySet();
    private volatile Runnable changeCallback;
    private volatile Instant lastUpdated = Instant.now();

    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    public boolean applyEvent(EliteLogEvent event) {
        if (event == null) {
            return false;
        }
        boolean changed = false;
        if (event instanceof MissionAcceptedEvent e) {
            changed = onAccepted(e);
        } else if (event instanceof MissionCompletedEvent e) {
            changed = removeMission(e.getMissionId());
        } else if (event instanceof MissionFailedEvent e) {
            changed = removeMission(e.getMissionId());
        } else if (event instanceof MissionAbandonedEvent e) {
            changed = removeMission(e.getMissionId());
        } else if (event instanceof MissionRedirectedEvent e) {
            changed = onRedirected(e);
        } else if (event instanceof CargoDepotEvent e) {
            changed = onCargoDepot(e);
        } else if (event instanceof MissionsEvent e) {
            changed = onMissionsSnapshot(e);
        }
        if (changed) {
            lastUpdated = Instant.now();
            notifyChanged();
        }
        return changed;
    }

    private boolean onAccepted(MissionAcceptedEvent e) {
        if (e.getMissionId() == 0L) {
            return false;
        }
        MissionRecord r = activeById.computeIfAbsent(e.getMissionId(), MissionRecord::new);
        r.setFaction(e.getFaction());
        r.setName(e.getName());
        r.setLocalisedName(e.getLocalisedName());
        if (e.getCommodityLocalised() != null && !e.getCommodityLocalised().isBlank()) {
            r.setCommodityLocalised(e.getCommodityLocalised());
        }
        if (e.getCount() > 0) {
            r.setCountRequired(e.getCount());
        }
        if (e.getDestinationSystem() != null) {
            r.setDestinationSystem(e.getDestinationSystem());
        }
        if (e.getDestinationStation() != null) {
            r.setDestinationStation(e.getDestinationStation());
        }
        if (e.getDestinationSettlement() != null) {
            r.setDestinationSettlement(e.getDestinationSettlement());
        }
        r.setTargetFaction(e.getTargetFaction());
        r.setTarget(e.getTarget());
        if (e.getKillCount() > 0) {
            r.setKillCount(e.getKillCount());
        }
        if (e.getDonation() > 0) {
            r.setDonation(e.getDonation());
        }
        if (e.getReward() > 0) {
            r.setReward(e.getReward());
        }
        r.setExpiryIso(e.getExpiry());
        r.setWing(e.isWing());
        r.setInfluence(e.getInfluence());
        r.setReputation(e.getReputation());
        r.setAcceptedAt(eventTimestamp(e));
        r.setDetailsPending(false);
        return true;
    }

    private boolean onRedirected(MissionRedirectedEvent e) {
        if (e.getMissionId() == 0L) {
            return false;
        }
        MissionRecord r = activeById.get(e.getMissionId());
        if (r == null) {
            r = new MissionRecord(e.getMissionId());
            activeById.put(e.getMissionId(), r);
        }
        if (e.getName() != null) {
            r.setName(e.getName());
        }
        if (e.getLocalisedName() != null) {
            r.setLocalisedName(e.getLocalisedName());
        }
        if (e.getNewDestinationSystem() != null) {
            r.setDestinationSystem(e.getNewDestinationSystem());
        }
        if (e.getNewDestinationStation() != null) {
            r.setDestinationStation(e.getNewDestinationStation());
        }
        r.setRedirected(true);
        dismissedRedirectIds.remove(e.getMissionId());
        return true;
    }

    private boolean onCargoDepot(CargoDepotEvent e) {
        if (e.getMissionId() == 0L) {
            return false;
        }
        MissionRecord r = activeById.get(e.getMissionId());
        if (r == null) {
            return false;
        }
        if (e.getCargoType() != null) {
            r.setCargoType(e.getCargoType());
        }
        if (e.getTotalItemsToDeliver() > 0) {
            r.setTotalItemsToDeliver(e.getTotalItemsToDeliver());
            if (r.getCountRequired() <= 0) {
                r.setCountRequired(e.getTotalItemsToDeliver());
            }
        }
        r.setItemsDelivered(e.getItemsDelivered());
        return true;
    }

    private boolean onMissionsSnapshot(MissionsEvent e) {
        Set<Long> activeIds = new HashSet<>();
        Instant snapTs = e.getTimestamp();
        for (MissionsEvent.MissionSnapshotEntry entry : e.getActive()) {
            activeIds.add(entry.missionId);
            MissionRecord r = activeById.computeIfAbsent(entry.missionId, MissionRecord::new);
            if (entry.name != null) {
                r.setName(entry.name);
            }
            r.setPassengerMission(entry.passengerMission);
            if (entry.expiresSeconds > 0) {
                r.setExpiresSeconds(entry.expiresSeconds);
            }
            if (r.getAcceptedAt() == null && snapTs != null) {
                r.setAcceptedAt(snapTs);
            }
            if (r.getName() == null || r.getCommodityLocalised() == null) {
                r.setDetailsPending(true);
            }
        }
        boolean changed = false;
        /*
         * Ignore empty Active arrays when we already track missions — a bad or partial snapshot must not wipe
         * MissionAccepted state (e.g. after UI tab rebuild before journal tail catches up).
         */
        if (!activeIds.isEmpty() || activeById.isEmpty()) {
            for (Long id : new ArrayList<>(activeById.keySet())) {
                if (!activeIds.contains(id)) {
                    activeById.remove(id);
                    dismissedRedirectIds.remove(id);
                    changed = true;
                }
            }
        }
        return changed || !activeIds.isEmpty();
    }

    /**
     * Replays mission-related journal lines from the current session file when the tracker is empty
     * (e.g. after {@link org.dce.ed.OverlayContentPanel#rebuildTabbedPane()}).
     */
    public boolean replayMissionEventsFromCurrentJournalFile(String clientKey) {
        Path dir = OverlayPreferences.resolveJournalDirectory(clientKey);
        if (dir == null) {
            return false;
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(dir);
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(1);
            if (events.isEmpty()) {
                return false;
            }
            int start = 0;
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i).getType() == EliteEventType.LOAD_GAME) {
                    start = i;
                    break;
                }
            }
            Runnable savedCallback = changeCallback;
            changeCallback = null;
            boolean changed = false;
            try {
                for (int i = start; i < events.size(); i++) {
                    EliteLogEvent event = events.get(i);
                    if (event instanceof MissionAcceptedEvent
                            || event instanceof MissionCompletedEvent
                            || event instanceof MissionFailedEvent
                            || event instanceof MissionAbandonedEvent
                            || event instanceof MissionRedirectedEvent
                            || event instanceof CargoDepotEvent
                            || event instanceof MissionsEvent) {
                        if (applyEvent(event)) {
                            changed = true;
                        }
                    }
                }
            } finally {
                changeCallback = savedCallback;
            }
            if (changed) {
                lastUpdated = Instant.now();
                notifyChanged();
            }
            return changed;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean removeMission(long missionId) {
        if (missionId == 0L) {
            return false;
        }
        boolean removed = activeById.remove(missionId) != null;
        dismissedRedirectIds.remove(missionId);
        return removed;
    }

    private static Instant eventTimestamp(EliteLogEvent e) {
        return e != null ? e.getTimestamp() : null;
    }

    public List<MissionRecord> getActive() {
        List<MissionRecord> list = new ArrayList<>(activeById.values());
        list.sort(Comparator.comparing(MissionRecord::summaryLine, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public List<CommodityMissionGroup> getCommodityGroups(Function<String, Integer> inHoldForCommodity) {
        Map<String, List<MissionRecord>> byCommodity = new LinkedHashMap<>();
        for (MissionRecord r : activeById.values()) {
            if (!r.isCommodityMission()) {
                continue;
            }
            String key = normalizeCommodity(r.getCommodityLocalised());
            byCommodity.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        List<CommodityMissionGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<MissionRecord>> e : byCommodity.entrySet()) {
            List<MissionRecord> missions = e.getValue();
            int totalRequired = 0;
            int totalDelivered = 0;
            Instant soonest = null;
            Set<String> turnInKeys = new HashSet<>();
            MissionDestination sharedTurnIn = null;
            for (MissionRecord r : missions) {
                int req = r.getCountRequired() > 0 ? r.getCountRequired() : r.getTotalItemsToDeliver();
                totalRequired += req;
                totalDelivered += r.getItemsDelivered();
                Instant exp = expiryInstant(r);
                if (exp != null && (soonest == null || exp.isBefore(soonest))) {
                    soonest = exp;
                }
                MissionDestination turnIn = MissionDestinationResolver.turnInFor(r);
                String tk = turnInKey(turnIn);
                turnInKeys.add(tk);
                if (sharedTurnIn == null) {
                    sharedTurnIn = turnIn;
                }
            }
            int inHold = inHoldForCommodity != null
                    ? inHoldForCommodity.apply(missions.get(0).getCommodityLocalised())
                    : 0;
            boolean multipleTurnIns = turnInKeys.size() > 1;
            MissionDestination turnInDest = multipleTurnIns
                    ? new MissionDestination(null, null, "Multiple")
                    : (sharedTurnIn != null ? sharedTurnIn : new MissionDestination(null, null, null));
            groups.add(new CommodityMissionGroup(
                    missions.get(0).getCommodityLocalised(),
                    missions.size(),
                    totalRequired,
                    inHold,
                    totalDelivered,
                    turnInDest,
                    multipleTurnIns,
                    soonest,
                    missions));
        }
        groups.sort(Comparator.comparing(CommodityMissionGroup::getCommodityLocalised, String.CASE_INSENSITIVE_ORDER));
        return groups;
    }

    public List<MissionRecord> getRedirectedNotDismissed() {
        List<MissionRecord> out = new ArrayList<>();
        for (MissionRecord r : activeById.values()) {
            if (r.isRedirected() && !dismissedRedirectIds.contains(r.getMissionId())) {
                out.add(r);
            }
        }
        return out;
    }

    public void dismissRedirectBanner() {
        for (MissionRecord r : activeById.values()) {
            if (r.isRedirected()) {
                dismissedRedirectIds.add(r.getMissionId());
            }
        }
        notifyChanged();
    }

    public static Instant expiryInstant(MissionRecord r) {
        if (r == null) {
            return null;
        }
        if (r.getExpiryIso() != null && !r.getExpiryIso().isBlank()) {
            try {
                return Instant.parse(r.getExpiryIso());
            } catch (Exception ignored) {
            }
        }
        if (r.getExpiresSeconds() > 0 && r.getAcceptedAt() != null) {
            return r.getAcceptedAt().plusSeconds(r.getExpiresSeconds());
        }
        return null;
    }

    public static String formatExpiryRemaining(MissionRecord r) {
        Instant exp = expiryInstant(r);
        if (exp == null) {
            return "—";
        }
        long sec = exp.getEpochSecond() - Instant.now().getEpochSecond();
        if (sec <= 0) {
            return "expired";
        }
        if (sec < 3600) {
            return (sec / 60) + "m";
        }
        if (sec < 86400) {
            return (sec / 3600) + "h " + ((sec % 3600) / 60) + "m";
        }
        return (sec / 86400) + "d " + ((sec % 86400) / 3600) + "h";
    }

    public static boolean isExpiringSoon(MissionRecord r) {
        Instant exp = expiryInstant(r);
        if (exp == null) {
            return false;
        }
        long sec = exp.getEpochSecond() - Instant.now().getEpochSecond();
        return sec > 0 && sec < 3600;
    }

    public static boolean isUrgent(MissionRecord r) {
        Instant exp = expiryInstant(r);
        if (exp == null) {
            return false;
        }
        long sec = exp.getEpochSecond() - Instant.now().getEpochSecond();
        return sec > 0 && sec < 900;
    }

    public void fillSessionState(EdoSessionState state) {
        if (state == null) {
            return;
        }
        MissionSessionData data = new MissionSessionData();
        Map<String, MissionRecordPersisted> map = new HashMap<>();
        for (MissionRecord r : activeById.values()) {
            map.put(Long.toString(r.getMissionId()), toPersisted(r));
        }
        data.setActiveById(map);
        data.setDismissedRedirectIds(new ArrayList<>(dismissedRedirectIds));
        data.setLastUpdated(lastUpdated != null ? lastUpdated.toString() : null);
        state.setMissions(data);
        state.setVersion(3);
    }

    public void applySessionState(EdoSessionState state) {
        activeById.clear();
        dismissedRedirectIds.clear();
        if (state == null || state.getMissions() == null) {
            return;
        }
        MissionSessionData data = state.getMissions();
        for (Map.Entry<String, MissionRecordPersisted> e : data.activeByIdOrEmpty().entrySet()) {
            MissionRecordPersisted p = e.getValue();
            if (p == null) {
                continue;
            }
            long id = p.getMissionId() != 0L ? p.getMissionId() : parseId(e.getKey());
            if (id == 0L) {
                continue;
            }
            activeById.put(id, fromPersisted(p, id));
        }
        for (Long id : data.dismissedRedirectIdsOrEmpty()) {
            if (id != null) {
                dismissedRedirectIds.add(id);
            }
        }
        if (data.getLastUpdated() != null) {
            try {
                lastUpdated = Instant.parse(data.getLastUpdated());
            } catch (Exception ignored) {
            }
        }
    }

    private static long parseId(String key) {
        try {
            return Long.parseLong(key);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static MissionRecordPersisted toPersisted(MissionRecord r) {
        MissionRecordPersisted p = new MissionRecordPersisted();
        p.setMissionId(r.getMissionId());
        p.setFaction(r.getFaction());
        p.setName(r.getName());
        p.setLocalisedName(r.getLocalisedName());
        p.setCategory(r.getCategory().name());
        p.setCommodityLocalised(r.getCommodityLocalised());
        p.setCountRequired(r.getCountRequired());
        p.setDestinationSystem(r.getDestinationSystem());
        p.setDestinationStation(r.getDestinationStation());
        p.setDestinationSettlement(r.getDestinationSettlement());
        p.setTargetFaction(r.getTargetFaction());
        p.setTarget(r.getTarget());
        p.setKillCount(r.getKillCount());
        p.setDonation(r.getDonation());
        p.setReward(r.getReward());
        p.setExpiryIso(r.getExpiryIso());
        p.setExpiresSeconds(r.getExpiresSeconds());
        p.setWing(r.isWing());
        p.setPassengerMission(r.isPassengerMission());
        p.setInfluence(r.getInfluence());
        p.setReputation(r.getReputation());
        p.setItemsDelivered(r.getItemsDelivered());
        p.setTotalItemsToDeliver(r.getTotalItemsToDeliver());
        p.setCargoType(r.getCargoType());
        p.setRedirected(r.isRedirected());
        p.setAcceptedAt(r.getAcceptedAt() != null ? r.getAcceptedAt().toString() : null);
        p.setDetailsPending(r.isDetailsPending());
        return p;
    }

    private static MissionRecord fromPersisted(MissionRecordPersisted p, long id) {
        MissionRecord r = new MissionRecord(id);
        r.setFaction(p.getFaction());
        r.setName(p.getName());
        r.setLocalisedName(p.getLocalisedName());
        if (p.getCategory() != null) {
            try {
                r.setCategory(MissionCategory.valueOf(p.getCategory()));
            } catch (Exception ignored) {
                r.setCategory(MissionCategory.fromMissionName(p.getName()));
            }
        }
        r.setCommodityLocalised(p.getCommodityLocalised());
        r.setCountRequired(p.getCountRequired());
        r.setDestinationSystem(p.getDestinationSystem());
        r.setDestinationStation(p.getDestinationStation());
        r.setDestinationSettlement(p.getDestinationSettlement());
        r.setTargetFaction(p.getTargetFaction());
        r.setTarget(p.getTarget());
        r.setKillCount(p.getKillCount());
        r.setDonation(p.getDonation());
        r.setReward(p.getReward());
        r.setExpiryIso(p.getExpiryIso());
        r.setExpiresSeconds(p.getExpiresSeconds());
        r.setWing(p.isWing());
        r.setPassengerMission(p.isPassengerMission());
        r.setInfluence(p.getInfluence());
        r.setReputation(p.getReputation());
        r.setItemsDelivered(p.getItemsDelivered());
        r.setTotalItemsToDeliver(p.getTotalItemsToDeliver());
        r.setCargoType(p.getCargoType());
        r.setRedirected(p.isRedirected());
        if (p.getAcceptedAt() != null) {
            try {
                r.setAcceptedAt(Instant.parse(p.getAcceptedAt()));
            } catch (Exception ignored) {
            }
        }
        r.setDetailsPending(p.isDetailsPending());
        return r;
    }

    private static String normalizeCommodity(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static String turnInKey(MissionDestination d) {
        if (d == null || d.isEmpty()) {
            return "";
        }
        return (d.getSystem() != null ? d.getSystem() : "")
                + "|" + (d.getStation() != null ? d.getStation() : "")
                + "|" + (d.getSettlement() != null ? d.getSettlement() : "");
    }

    public static int commodityInHold(String commodityLocalised) {
        CargoMonitor.Snapshot snap = CargoMonitor.getInstance().getSnapshot();
        if (snap == null || snap.getCargoJson() == null || commodityLocalised == null) {
            return 0;
        }
        return CargoMonitor.countCommodityTons(snap.getCargoJson(), commodityLocalised);
    }

    private void notifyChanged() {
        Runnable cb = changeCallback;
        if (cb != null) {
            try {
                cb.run();
            } catch (Exception ignored) {
            }
        }
    }
}
