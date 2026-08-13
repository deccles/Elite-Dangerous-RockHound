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
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.dce.ed.CargoMonitor;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.MissionAbandonedEvent;
import org.dce.ed.logreader.event.MissionAcceptedEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionFailedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.logreader.event.MissionsEvent;
import org.dce.ed.logreader.event.SupercruiseExitEvent;
import org.dce.ed.session.EdoSessionState;
import org.dce.ed.session.MissionSessionData;
import org.dce.ed.session.MissionSessionData.MissionRecordPersisted;

import com.google.gson.JsonObject;

/**
 * Commander-scoped active mission board; hydrated from {@link EdoSessionState} and journal events.
 */
public final class MissionTracker {

    private final Map<Long, MissionRecord> activeById = new ConcurrentHashMap<>();
    private final Set<Long> dismissedRedirectIds = ConcurrentHashMap.newKeySet();
    private volatile Runnable changeCallback;
    private volatile Instant lastUpdated = Instant.now();
    private volatile Supplier<String> currentSystemSupplier;
    private volatile Supplier<String> currentStationSupplier;
    /**
     * Lowest remaining kills among missions updated by the last qualifying {@link BountyEvent};
     * consumed for speech.
     */
    private volatile Integer lastMassacreKillRemaining;

    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    /** Used to require commander presence in the mission hunt system before attributing kills. */
    public void setCurrentSystemSupplier(Supplier<String> currentSystemSupplier) {
        this.currentSystemSupplier = currentSystemSupplier;
    }

    /** Station at accept time (Transport From row), when docked. */
    public void setCurrentStationSupplier(Supplier<String> currentStationSupplier) {
        this.currentStationSupplier = currentStationSupplier;
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
        } else if (event instanceof BountyEvent e) {
            changed = onBounty(e);
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
        // Snapshot accept location (pickup / From). Fill system and station independently
        // so a known system still allows a later station backfill (e.g. journal hydrate).
        if (r.getOriginSystem() == null || r.getOriginSystem().isBlank()) {
            String originSys = currentSystemSupplier != null ? currentSystemSupplier.get() : null;
            if (originSys != null && !originSys.isBlank()) {
                r.setOriginSystem(originSys.trim());
            }
        }
        if (r.getOriginStation() == null || r.getOriginStation().isBlank()) {
            String originStn = currentStationSupplier != null ? currentStationSupplier.get() : null;
            if (originStn != null && !originStn.isBlank()) {
                r.setOriginStation(originStn.trim());
            }
        }
        r.setTargetFaction(e.getTargetFaction());
        String targetName = e.getTargetLocalised();
        if (targetName == null || targetName.isBlank()) {
            targetName = e.getTarget();
        }
        r.setTarget(targetName);
        if (e.getTargetType() != null) {
            r.setTargetType(e.getTargetType());
        }
        if (e.getTargetTypeLocalised() != null) {
            r.setTargetTypeLocalised(e.getTargetTypeLocalised());
        }
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
        // Before the redirect overwrites the hunt system and forces progress to full.
        recalibrateSiblingsFromRedirect(r);
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
        if (r.getKillCount() > 0) {
            r.setKillsCompleted(r.getKillCount());
        }
        dismissedRedirectIds.remove(e.getMissionId());
        return true;
    }

    /**
     * A redirect is the one exact progress figure the journal ever gives us: the game counted
     * {@code KillCount} kills over this mission's lifetime. A sibling accepted no earlier, hunting
     * the same faction in the same system, was active for a subset of that window, so its true
     * progress cannot exceed that figure. Bounty estimates overshoot whenever security, a
     * ship-launched fighter or another commander lands the killing blow — the journal reports those
     * vouchers identically to a solo kill — so clamp the sibling down to the proven bound.
     */
    private void recalibrateSiblingsFromRedirect(MissionRecord finished) {
        if (finished.getCategory() != MissionCategory.COMBAT
                || finished.getKillCount() <= 0
                || finished.isRedirected()) {
            return;
        }
        Instant finishedAccepted = finished.getAcceptedAt();
        String faction = finished.getTargetFaction();
        String huntSystem = finished.getDestinationSystem();
        if (finishedAccepted == null || faction == null || faction.isBlank()
                || huntSystem == null || huntSystem.isBlank()) {
            return;
        }
        for (MissionRecord other : activeById.values()) {
            if (other == finished
                    || other.getCategory() != MissionCategory.COMBAT
                    || other.getKillCount() <= 0
                    || other.isRedirected()
                    || other.getKillsCompleted() <= finished.getKillCount()) {
                continue;
            }
            if (!faction.equalsIgnoreCase(other.getTargetFaction())
                    || !huntSystem.equalsIgnoreCase(other.getDestinationSystem())) {
                continue;
            }
            Instant accepted = other.getAcceptedAt();
            if (accepted == null || accepted.isBefore(finishedAccepted)) {
                continue;
            }
            other.setKillsCompleted(finished.getKillCount());
        }
    }

    /**
     * Clears estimated massacre kill progress for incomplete combat missions.
     * Used before a full journal replay that will rebuild counts from {@link BountyEvent}s.
     * Redirected missions are left alone (objective already complete).
     */
    public void resetEstimatedMassacreProgress() {
        for (MissionRecord r : activeById.values()) {
            if (r.getCategory() != MissionCategory.COMBAT) {
                continue;
            }
            if (r.getKillCount() <= 0 || r.isRedirected()) {
                continue;
            }
            r.setKillsCompleted(0);
        }
        lastMassacreKillRemaining = null;
    }

    /**
     * Attributes a wanted kill to one incomplete massacre mission per issuing faction whose
     * {@code TargetFaction} matches {@link BountyEvent#getVictimFaction()}, whose hunt
     * {@code DestinationSystem} matches the commander's current system, and that was accepted
     * before the kill.
     * <p>
     * Still an estimate (body-specific / pirate-vs-deserter nuance is not in the journal),
     * but system gating avoids counting bounties from unrelated systems and the accept-time gate
     * avoids crediting kills made before the mission existed.
     * Missions from different issuing factions stack. Missions from the same issuing faction
     * progress oldest-first rather than sharing the same kill, matching how the game credits them.
     */
    private boolean onBounty(BountyEvent e) {
        String victimFaction = e.getVictimFaction();
        if (victimFaction == null || victimFaction.isBlank()) {
            return false;
        }
        String target = e.getTarget();
        if (target != null && target.equalsIgnoreCase("Skimmer")) {
            return false;
        }
        String currentSystem = currentSystemSupplier != null ? currentSystemSupplier.get() : null;
        if (currentSystem == null || currentSystem.isBlank()) {
            return false;
        }
        Instant killedAt = eventTimestamp(e);
        Map<String, MissionRecord> matchedByIssuer = new LinkedHashMap<>();
        for (MissionRecord r : activeById.values()) {
            if (!isMassacreKillCandidate(r, victimFaction, currentSystem, killedAt)) {
                continue;
            }
            String issuer = r.getFaction();
            String issuerKey = issuer == null || issuer.isBlank()
                    ? "mission:" + r.getMissionId()
                    : issuer.trim().toLowerCase();
            MissionRecord existing = matchedByIssuer.get(issuerKey);
            if (existing == null || acceptedBefore(r, existing)) {
                matchedByIssuer.put(issuerKey, r);
            }
        }
        List<MissionRecord> matched = new ArrayList<>(matchedByIssuer.values());
        if (matched.isEmpty()) {
            return false;
        }
        int minRemaining = Integer.MAX_VALUE;
        for (MissionRecord r : matched) {
            r.setKillsCompleted(r.getKillsCompleted() + 1);
            int remaining = Math.max(0, r.getKillCount() - r.getKillsCompleted());
            if (remaining < minRemaining) {
                minRemaining = remaining;
            }
        }
        lastMassacreKillRemaining = Integer.valueOf(minRemaining);
        return true;
    }

    private static boolean acceptedBefore(MissionRecord candidate, MissionRecord existing) {
        Instant candidateAccepted = candidate.getAcceptedAt();
        Instant existingAccepted = existing.getAcceptedAt();
        if (candidateAccepted != null && existingAccepted != null) {
            int byAccepted = candidateAccepted.compareTo(existingAccepted);
            if (byAccepted != 0) {
                return byAccepted < 0;
            }
        } else if (candidateAccepted != null) {
            return true;
        } else if (existingAccepted != null) {
            return false;
        }
        return candidate.getMissionId() < existing.getMissionId();
    }

    static boolean isMassacreKillCandidate(MissionRecord r, String victimFaction, String currentSystem,
            Instant killedAt) {
        if (r == null || r.getCategory() != MissionCategory.COMBAT) {
            return false;
        }
        if (r.getKillCount() <= 0 || r.isRedirected()) {
            return false;
        }
        if (r.getKillsCompleted() >= r.getKillCount()) {
            return false;
        }
        // Kills predating the mission board offer cannot count toward it. Missions known only from a
        // Missions snapshot have no accepted time, so they keep the pre-gate behaviour.
        Instant acceptedAt = r.getAcceptedAt();
        if (acceptedAt != null && killedAt != null && killedAt.isBefore(acceptedAt)) {
            return false;
        }
        String tf = r.getTargetFaction();
        if (tf == null || !tf.equalsIgnoreCase(victimFaction.trim())) {
            return false;
        }
        // Named assassination targets are not advanced by generic faction bounties.
        if (r.getTarget() != null && !r.getTarget().isBlank() && r.getKillCount() <= 1) {
            return false;
        }
        String huntSystem = r.getDestinationSystem();
        if (huntSystem == null || huntSystem.isBlank()) {
            return false;
        }
        return huntSystem.equalsIgnoreCase(currentSystem.trim());
    }

    /**
     * Lowest remaining massacre kills after the last qualifying {@link BountyEvent}, if any.
     * Cleared on read so each kill is announced at most once.
     */
    public OptionalInt consumeLastMassacreKillRemaining() {
        Integer v = lastMassacreKillRemaining;
        lastMassacreKillRemaining = null;
        if (v == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(v.intValue());
    }

    public MissionRecord findById(long missionId) {
        if (missionId == 0L) {
            return null;
        }
        return activeById.get(missionId);
    }

    public boolean setSourcedFrom(long missionId, String system, String station) {
        MissionRecord r = findById(missionId);
        if (r == null || !r.isSelfSourcedCommodityMission()) {
            return false;
        }
        String nextSystem = system == null ? null : system.trim();
        String nextStation = station == null ? null : station.trim();
        if (nextSystem == null || nextSystem.isBlank() || nextStation == null || nextStation.isBlank()) {
            return false;
        }
        if (nextSystem.equals(r.getSourcedFromSystem()) && nextStation.equals(r.getSourcedFromStation())) {
            return false;
        }
        r.setSourcedFromSystem(nextSystem);
        r.setSourcedFromStation(nextStation);
        lastUpdated = Instant.now();
        notifyChanged();
        return true;
    }

    /** Atomically assigns a source only to eligible missions that are still unassigned. */
    public int setSourcedFromIfUnassigned(List<Long> missionIds, String system, String station) {
        String nextSystem = system == null ? "" : system.trim();
        String nextStation = station == null ? "" : station.trim();
        if (missionIds == null || nextSystem.isBlank() || nextStation.isBlank()) return 0;
        int changed = 0;
        for (Long missionId : missionIds) {
            MissionRecord r = missionId == null ? null : findById(missionId.longValue());
            if (r == null || !r.isSelfSourcedCommodityMission()) continue;
            if ((r.getSourcedFromSystem() != null && !r.getSourcedFromSystem().isBlank())
                    || (r.getSourcedFromStation() != null && !r.getSourcedFromStation().isBlank())) continue;
            r.setSourcedFromSystem(nextSystem);
            r.setSourcedFromStation(nextStation);
            changed++;
        }
        if (changed > 0) {
            lastUpdated = Instant.now();
            notifyChanged();
        }
        return changed;
    }

    record ManualSource(String system, String station) { }

    Map<Long, ManualSource> snapshotManualSources() {
        Map<Long, ManualSource> sources = new HashMap<>();
        for (MissionRecord mission : activeById.values()) {
            if ((mission.getSourcedFromSystem() != null && !mission.getSourcedFromSystem().isBlank())
                    || (mission.getSourcedFromStation() != null && !mission.getSourcedFromStation().isBlank())) {
                sources.put(mission.getMissionId(),
                        new ManualSource(mission.getSourcedFromSystem(), mission.getSourcedFromStation()));
            }
        }
        return sources;
    }

    void restoreManualSources(Map<Long, ManualSource> sources) {
        if (sources == null || sources.isEmpty()) return;
        for (Map.Entry<Long, ManualSource> entry : sources.entrySet()) {
            MissionRecord mission = activeById.get(entry.getKey());
            ManualSource source = entry.getValue();
            if (mission == null || source == null) continue;
            if (mission.getSourcedFromSystem() == null || mission.getSourcedFromSystem().isBlank()) {
                mission.setSourcedFromSystem(source.system());
            }
            if (mission.getSourcedFromStation() == null || mission.getSourcedFromStation().isBlank()) {
                mission.setSourcedFromStation(source.station());
            }
        }
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

    public boolean hasDetailsPending() {
        for (MissionRecord r : activeById.values()) {
            if (r.isDetailsPending() || (r.isCommodityMission() && r.getCommodityLocalised() == null)) {
                return true;
            }
            if (r.getCategory() == MissionCategory.COMMODITY
                    && (r.getCommodityLocalised() == null || r.getCommodityLocalised().isBlank())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replays mission-related journal lines. Use full history when active missions only came from
     * {@code Missions} snapshots, since those snapshots omit commodity, count, destination, and reward.
     */
    public boolean replayMissionEventsFromJournals(String clientKey, boolean fullHistory) {
        Path dir = OverlayPreferences.resolveJournalDirectory(clientKey);
        if (dir == null) {
            return false;
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(dir);
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(fullHistory ? Integer.MAX_VALUE : 1);
            if (events.isEmpty()) {
                return false;
            }
            int start = 0;
            if (!fullHistory) {
                for (int i = events.size() - 1; i >= 0; i--) {
                    if (events.get(i).getType() == EliteEventType.LOAD_GAME) {
                        start = i;
                        break;
                    }
                }
            }
            Runnable savedCallback = changeCallback;
            Map<Long, ManualSource> savedManualSources = snapshotManualSources();
            Supplier<String> savedSystem = currentSystemSupplier;
            Supplier<String> savedStation = currentStationSupplier;
            final String[] replaySystem = { null };
            final String[] replayStation = { null };
            changeCallback = null;
            currentSystemSupplier = () -> replaySystem[0];
            currentStationSupplier = () -> replayStation[0];
            boolean changed = false;
            try {
                for (int i = start; i < events.size(); i++) {
                    EliteLogEvent event = events.get(i);
                    if (event == null) {
                        continue;
                    }
                    applyReplayLocationContext(event, replaySystem, replayStation);
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
                restoreManualSources(savedManualSources);
                changeCallback = savedCallback;
                currentSystemSupplier = savedSystem;
                currentStationSupplier = savedStation;
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

    /**
     * Tracks commander system/station while replaying journals so {@link #onAccepted} can snapshot
     * Transport From (including station name from {@code Docked}/{@code Location}).
     */
    private static void applyReplayLocationContext(EliteLogEvent event, String[] system, String[] station) {
        if (event == null || system == null || station == null) {
            return;
        }
        EliteEventType type = event.getType();
        if (type == EliteEventType.DOCKED) {
            JsonObject raw = event.getRawJson();
            String stn = jsonString(raw, "StationName");
            if (stn != null) {
                station[0] = stn;
            }
            String sys = jsonString(raw, "StarSystem");
            if (sys != null) {
                system[0] = sys;
            }
            return;
        }
        if (type == EliteEventType.UNDOCKED) {
            station[0] = null;
            return;
        }
        if (event instanceof LocationEvent loc) {
            if (loc.getStarSystem() != null && !loc.getStarSystem().isBlank()) {
                system[0] = loc.getStarSystem().trim();
            }
            if (loc.isDocked()) {
                String stn = jsonString(event.getRawJson(), "StationName");
                if (stn != null) {
                    station[0] = stn;
                }
            } else {
                station[0] = null;
            }
            return;
        }
        if (event instanceof FsdJumpEvent jump) {
            if (jump.getStarSystem() != null && !jump.getStarSystem().isBlank()) {
                system[0] = jump.getStarSystem().trim();
            }
            station[0] = null;
            return;
        }
        if (event instanceof CarrierJumpEvent jump) {
            if (jump.getStarSystem() != null && !jump.getStarSystem().isBlank()) {
                system[0] = jump.getStarSystem().trim();
            }
            station[0] = null;
            return;
        }
        if (event instanceof SupercruiseExitEvent sc) {
            if (sc.getStarSystem() != null && !sc.getStarSystem().isBlank()) {
                system[0] = sc.getStarSystem().trim();
            }
        }
    }

    private static String jsonString(JsonObject raw, String key) {
        if (raw == null || key == null || !raw.has(key) || raw.get(key).isJsonNull()) {
            return null;
        }
        String v = raw.get(key).getAsString();
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    /**
     * Rebuilds incomplete massacre {@code killsCompleted} from journal {@code Bounty} events,
     * gated by hunt {@code DestinationSystem} + {@code TargetFaction}. Safe to call after session
     * restore or full rescan when live attribution may have missed kills (overlay off / wrong system).
     */
    public boolean rebuildMassacreKillProgressFromJournals(String clientKey) {
        if (!hasIncompleteMassacreMissions()) {
            return false;
        }
        Path dir = OverlayPreferences.resolveJournalDirectory(clientKey);
        if (dir == null) {
            return false;
        }
        try {
            EliteJournalReader reader = new EliteJournalReader(dir);
            Set<String> include = Set.of(
                    "Location",
                    "FSDJump",
                    "CarrierJump",
                    "SupercruiseExit",
                    "MissionAccepted",
                    "MissionRedirected",
                    "MissionCompleted",
                    "MissionFailed",
                    "MissionAbandoned",
                    "Bounty");
            List<EliteLogEvent> events = reader.readEventsFromLastNJournalFiles(Integer.MAX_VALUE, include);
            if (events.isEmpty()) {
                return false;
            }
            boolean changed = adoptMassacreKillProgress(replayMissionHistory(events));
            if (changed) {
                lastUpdated = Instant.now();
                notifyChanged();
            }
            return changed;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * Replays journal history into a throwaway tracker. Missions that have since been turned in are
     * re-accepted and removed at their original times, so they still consume the kills they were
     * credited with while active instead of leaving them for whatever mission is active today.
     */
    static MissionTracker replayMissionHistory(List<EliteLogEvent> events) {
        MissionTracker replay = new MissionTracker();
        final String[] system = { null };
        replay.setCurrentSystemSupplier(() -> system[0]);
        for (EliteLogEvent event : events) {
            if (event instanceof LocationEvent le) {
                system[0] = le.getStarSystem();
            } else if (event instanceof FsdJumpEvent je) {
                system[0] = je.getStarSystem();
            } else if (event instanceof CarrierJumpEvent cj) {
                system[0] = cj.getStarSystem();
            } else if (event instanceof SupercruiseExitEvent sc) {
                system[0] = sc.getStarSystem();
            } else {
                replay.applyEvent(event);
            }
        }
        return replay;
    }

    /** Copies replayed kill estimates onto the live board, leaving missions the replay never saw. */
    boolean adoptMassacreKillProgress(MissionTracker replay) {
        boolean changed = false;
        for (MissionRecord r : activeById.values()) {
            if (r.getCategory() != MissionCategory.COMBAT || r.getKillCount() <= 0 || r.isRedirected()) {
                continue;
            }
            MissionRecord replayed = replay.findById(r.getMissionId());
            if (replayed == null) {
                continue;
            }
            int kills = Math.min(replayed.getKillsCompleted(), r.getKillCount());
            if (kills != r.getKillsCompleted()) {
                r.setKillsCompleted(kills);
                changed = true;
            }
        }
        if (changed) {
            lastMassacreKillRemaining = null;
        }
        return changed;
    }

    private boolean hasIncompleteMassacreMissions() {
        for (MissionRecord r : activeById.values()) {
            if (r.getCategory() == MissionCategory.COMBAT
                    && r.getKillCount() > 0
                    && !r.isRedirected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replays mission-related journal lines from the current session file when the tracker is empty
     * (e.g. after {@link org.dce.ed.OverlayContentPanel#rebuildTabbedPane()}).
     */
    public boolean replayMissionEventsFromCurrentJournalFile(String clientKey) {
        return replayMissionEventsFromJournals(clientKey, false);
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
            String id = Long.toString(r.getMissionId());
            MissionRecordPersisted persisted = toPersisted(r);
            MissionRecordPersisted previous = state.getMissions() != null
                    ? state.getMissions().activeByIdOrEmpty().get(id) : null;
            preserveManualSource(persisted, previous);
            map.put(id, persisted);
        }
        data.setActiveById(map);
        data.setDismissedRedirectIds(new ArrayList<>(dismissedRedirectIds));
        data.setLastUpdated(lastUpdated != null ? lastUpdated.toString() : null);
        state.setMissions(data);
        state.setVersion(3);
    }

    private static void preserveManualSource(MissionRecordPersisted target, MissionRecordPersisted previous) {
        if (target == null || previous == null) return;
        if (target.getSourcedFromSystem() == null || target.getSourcedFromSystem().isBlank()) {
            target.setSourcedFromSystem(previous.getSourcedFromSystem());
        }
        if (target.getSourcedFromStation() == null || target.getSourcedFromStation().isBlank()) {
            target.setSourcedFromStation(previous.getSourcedFromStation());
        }
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
        p.setOriginSystem(r.getOriginSystem());
        p.setOriginStation(r.getOriginStation());
        p.setSourcedFromSystem(r.getSourcedFromSystem());
        p.setSourcedFromStation(r.getSourcedFromStation());
        p.setTargetFaction(r.getTargetFaction());
        p.setTarget(r.getTarget());
        p.setTargetType(r.getTargetType());
        p.setTargetTypeLocalised(r.getTargetTypeLocalised());
        p.setKillCount(r.getKillCount());
        p.setKillsCompleted(r.getKillsCompleted());
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
        r.setOriginSystem(p.getOriginSystem());
        r.setOriginStation(p.getOriginStation());
        r.setSourcedFromSystem(p.getSourcedFromSystem());
        r.setSourcedFromStation(p.getSourcedFromStation());
        r.setTargetFaction(p.getTargetFaction());
        r.setTarget(p.getTarget());
        r.setTargetType(p.getTargetType());
        r.setTargetTypeLocalised(p.getTargetTypeLocalised());
        r.setKillCount(p.getKillCount());
        r.setKillsCompleted(p.getKillsCompleted());
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
