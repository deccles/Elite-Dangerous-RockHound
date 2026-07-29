package org.dce.ed;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.ShipTargetedEvent;
import org.dce.ed.tts.PollyTtsCached;
import org.dce.ed.tts.TtsSprintf;

/**
 * Tracks per-session ship bounty scans from {@link ShipTargetedEvent} stage 3 and announces
 * first-scan and Kill Warrant Scanner (higher bounty) totals via speech.
 */
public final class BountyScanTracker {

    public static final String FIRST_BOUNTY_SPEECH = "Bounty of {credits} credits found";
    public static final String ADDITIONAL_BOUNTY_SPEECH =
            "Additional bounty of {credits} credits found for a total of {credits} credits";

    private static final BountyScanTracker INSTANCE = new BountyScanTracker();

    private static final TtsSprintf TTS = new TtsSprintf(new PollyTtsCached());

    public static BountyScanTracker getInstance() {
        return INSTANCE;
    }

    private enum ScanState {
        UNSEEN,
        FIRST_ANNOUNCED,
        ADDITIONAL_ANNOUNCED
    }

    public static final class SpeechRequest {
        private final String template;
        private final long credits1;
        private final long credits2;

        SpeechRequest(String template, long credits1, long credits2) {
            this.template = template;
            this.credits1 = credits1;
            this.credits2 = credits2;
        }

        public String getTemplate() {
            return template;
        }

        public long getCredits1() {
            return credits1;
        }

        /** Total bounty for additional announcements; {@code 0} for first-scan announcements. */
        public long getCredits2() {
            return credits2;
        }
    }

    private final Map<String, Long> lastBountyByPilot = new HashMap<>();
    private final Map<String, ScanState> stateByPilot = new HashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Pilot key of the currently locked combat target, when any. */
    private volatile String lockedTargetPilotKey;
    /** Bounty on the locked target when scan stage 3 reports a wanted pilot. */
    private volatile Long targetedBountyInSight;

    private BountyScanTracker() {
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Bounty credits on the ship currently locked in the combat HUD, or {@code null} when none.
     */
    public Long getTargetedBountyInSight() {
        return targetedBountyInSight;
    }

    public void resetSession() {
        lastBountyByPilot.clear();
        stateByPilot.clear();
        clearTargetedBountyInSight();
    }

    public void applyJournalEvent(EliteLogEvent event) {
        if (event instanceof LoadGameEvent) {
            resetSession();
            return;
        }
        if (!(event instanceof ShipTargetedEvent st)) {
            return;
        }
        updateTargetedBountyInSight(st);
        Optional<SpeechRequest> speech = onShipTargeted(st);
        if (speech.isEmpty()) {
            return;
        }
        if (!OverlayPreferences.isSpeechEnabled()) {
            return;
        }
        SpeechRequest req = speech.get();
        if (req.credits2 == 0L) {
            TTS.speakf(req.template, Long.valueOf(req.credits1));
        } else {
            TTS.speakf(req.template, Long.valueOf(req.credits1), Long.valueOf(req.credits2));
        }
    }

    /**
     * Evaluates a stage-3 bounty scan and updates per-pilot session state.
     * Visible for unit tests (no speech).
     */
    Optional<SpeechRequest> onShipTargeted(ShipTargetedEvent event) {
        if (event == null || !event.isTargetLocked() || event.getScanStage() != 3) {
            return Optional.empty();
        }
        Long bounty = event.getBounty();
        if (bounty == null || bounty <= 0L) {
            return Optional.empty();
        }
        String pilotKey = pilotKey(event.getPilotName());
        if (pilotKey == null) {
            return Optional.empty();
        }

        ScanState state = stateByPilot.getOrDefault(pilotKey, ScanState.UNSEEN);
        Long previous = lastBountyByPilot.get(pilotKey);
        lastBountyByPilot.put(pilotKey, bounty);

        if (state == ScanState.UNSEEN) {
            stateByPilot.put(pilotKey, ScanState.FIRST_ANNOUNCED);
            if (!OverlayPreferences.isBountyScanFirstAnnouncementEnabled()) {
                return Optional.empty();
            }
            long valuableThreshold = OverlayPreferences.getBountyScanValuableThresholdCredits();
            if (bounty.longValue() < valuableThreshold) {
                return Optional.empty();
            }
            long rounded = TtsSprintf.roundCreditsForSpeech(bounty);
            if (rounded <= 0L) {
                return Optional.empty();
            }
            return Optional.of(new SpeechRequest(FIRST_BOUNTY_SPEECH, rounded, 0L));
        }

        if (state == ScanState.FIRST_ANNOUNCED
                && previous != null
                && bounty > previous
                && OverlayPreferences.isBountyScanAdditionalAnnouncementEnabled()) {
            long delta = bounty - previous;
            long valuableThreshold = OverlayPreferences.getBountyScanValuableThresholdCredits();
            // Additional speech is gated on the KWS delta alone, not the running total.
            if (delta < valuableThreshold) {
                return Optional.empty();
            }
            long roundedDelta = TtsSprintf.roundCreditsForSpeech(delta);
            long roundedTotal = TtsSprintf.roundCreditsForSpeech(bounty);
            if (roundedDelta <= 0L) {
                return Optional.empty();
            }
            stateByPilot.put(pilotKey, ScanState.ADDITIONAL_ANNOUNCED);
            return Optional.of(new SpeechRequest(
                    ADDITIONAL_BOUNTY_SPEECH,
                    roundedDelta,
                    roundedTotal));
        }

        return Optional.empty();
    }

    /**
     * Tracks the bounty shown on the combat HUD while a wanted target stays locked.
     * Visible for unit tests (no speech).
     */
    void updateTargetedBountyInSight(ShipTargetedEvent event) {
        if (event == null) {
            return;
        }
        if (!event.isTargetLocked()) {
            clearTargetedBountyInSight();
            return;
        }
        String pilotKey = pilotKey(event.getPilotName());
        if (pilotKey != null && lockedTargetPilotKey != null && !pilotKey.equals(lockedTargetPilotKey)) {
            setTargetedBountyInSight(null);
        }
        if (pilotKey != null) {
            lockedTargetPilotKey = pilotKey;
        }
        if (event.getScanStage() == 3) {
            Long bounty = event.getBounty();
            if (bounty != null && bounty > 0L) {
                setTargetedBountyInSight(bounty);
            } else {
                setTargetedBountyInSight(null);
            }
        }
    }

    private void clearTargetedBountyInSight() {
        boolean changed = lockedTargetPilotKey != null || targetedBountyInSight != null;
        lockedTargetPilotKey = null;
        targetedBountyInSight = null;
        if (changed) {
            notifyListeners();
        }
    }

    private void setTargetedBountyInSight(Long bounty) {
        Long previous = targetedBountyInSight;
        if (previous != null && bounty != null && previous.longValue() == bounty.longValue()) {
            return;
        }
        if (previous == null && bounty == null) {
            return;
        }
        targetedBountyInSight = bounty;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static String pilotKey(String pilotName) {
        if (pilotName == null) {
            return null;
        }
        String trimmed = pilotName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("$npc_name_decorate:")) {
            String extracted = extractDecoratedNpcName(trimmed);
            if (extracted != null && !extracted.isBlank()) {
                trimmed = extracted.trim();
            }
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** {@code $npc_name_decorate:#name=Pilot Name;} → {@code Pilot Name}. */
    static String extractDecoratedNpcName(String token) {
        if (token == null) {
            return null;
        }
        int start = token.indexOf("#name=");
        if (start < 0) {
            return null;
        }
        String name = token.substring(start + 6);
        int end = name.indexOf(';');
        if (end >= 0) {
            name = name.substring(0, end);
        }
        return name.trim();
    }
}
