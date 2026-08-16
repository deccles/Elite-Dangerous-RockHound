package org.dce.ed.mission;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.BountyEvent;
import org.dce.ed.logreader.event.CargoDepotEvent;
import org.dce.ed.logreader.event.FactionKillBondEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.MissionCompletedEvent;
import org.dce.ed.logreader.event.MissionRedirectedEvent;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;

/**
 * Live mission-progress speech (massacre kill progress, combat complete, cargo deliveries).
 * Call {@link #announceAfterLiveApply} only for live journal tail events — not journal replay/hydrate.
 */
public final class MissionSpeechTracker {

    public static final String TARGET_DESTROYED_SPEECH =
            "Mission target destroyed. {n} targets remaining";
    public static final String COMBAT_COMPLETE_SPEECH = "Combat mission completed";
    public static final String DELIVERED_SPEECH =
            "Delivered {n} mission items. {n} remaining";

    private static final MissionSpeechTracker INSTANCE = new MissionSpeechTracker();
    private static final TtsSprintf TTS = new TtsSprintf(new PollyTtsCached());

    private final Set<Long> combatCompleteAnnounced = ConcurrentHashMap.newKeySet();

    public static MissionSpeechTracker getInstance() {
        return INSTANCE;
    }

    private MissionSpeechTracker() {
    }

    public void resetSession() {
        combatCompleteAnnounced.clear();
    }

    /**
     * Evaluates speech after {@link MissionTracker#applyEvent} for a live event.
     * Visible for unit tests (returns request without speaking when {@code speak} is false).
     */
    public Optional<SpeechRequest> announceAfterLiveApply(
            MissionTracker tracker,
            EliteLogEvent event,
            MissionRecord completedPrior,
            boolean speak) {
        if (event instanceof LoadGameEvent) {
            resetSession();
            return Optional.empty();
        }
        if (!OverlayPreferences.isMissionProgressAnnouncementEnabled()) {
            return Optional.empty();
        }

        Optional<SpeechRequest> req = Optional.empty();
        if (event instanceof BountyEvent || event instanceof FactionKillBondEvent) {
            req = massacreKillSpeech(tracker);
        } else if (event instanceof MissionRedirectedEvent e) {
            req = combatCompleteIfNeeded(tracker.findById(e.getMissionId()), e.getMissionId());
        } else if (event instanceof MissionCompletedEvent e) {
            req = combatCompleteIfNeeded(completedPrior, e.getMissionId());
        } else if (event instanceof CargoDepotEvent depot) {
            req = deliverySpeech(depot);
        }

        if (req.isEmpty()) {
            return Optional.empty();
        }
        if (speak && OverlayPreferences.isSpeechEnabled()) {
            speak(req.get());
        }
        return req;
    }

    private static Optional<SpeechRequest> massacreKillSpeech(MissionTracker tracker) {
        if (tracker == null) {
            return Optional.empty();
        }
        OptionalInt remaining = tracker.consumeLastMassacreKillRemaining();
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SpeechRequest(TARGET_DESTROYED_SPEECH, remaining.getAsInt(), -1));
    }

    private Optional<SpeechRequest> combatCompleteIfNeeded(MissionRecord mission, long missionId) {
        if (mission == null || mission.getCategory() != MissionCategory.COMBAT) {
            return Optional.empty();
        }
        if (!combatCompleteAnnounced.add(missionId)) {
            return Optional.empty();
        }
        return Optional.of(new SpeechRequest(COMBAT_COMPLETE_SPEECH, -1, -1));
    }

    private static Optional<SpeechRequest> deliverySpeech(CargoDepotEvent depot) {
        if (depot == null) {
            return Optional.empty();
        }
        String updateType = depot.getUpdateType();
        if (updateType == null || !updateType.equalsIgnoreCase("Deliver")) {
            return Optional.empty();
        }
        int deliveredNow = depot.getCount();
        if (deliveredNow <= 0) {
            return Optional.empty();
        }
        int remaining = Math.max(0, depot.getTotalItemsToDeliver() - depot.getItemsDelivered());
        return Optional.of(new SpeechRequest(DELIVERED_SPEECH, deliveredNow, remaining));
    }

    private static void speak(SpeechRequest req) {
        if (COMBAT_COMPLETE_SPEECH.equals(req.template)) {
            TTS.speakf(req.template);
        } else if (TARGET_DESTROYED_SPEECH.equals(req.template)) {
            TTS.speakf(req.template, Integer.valueOf(req.n1));
        } else {
            TTS.speakf(req.template, Integer.valueOf(req.n1), Integer.valueOf(req.n2));
        }
    }

    public static final class SpeechRequest {
        private final String template;
        private final int n1;
        private final int n2;

        SpeechRequest(String template, int n1, int n2) {
            this.template = template;
            this.n1 = n1;
            this.n2 = n2;
        }

        public String getTemplate() {
            return template;
        }

        public int getN1() {
            return n1;
        }

        public int getN2() {
            return n2;
        }
    }
}
