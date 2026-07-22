package org.dce.ed.engineering;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.EngineerProgressEvent;

/**
 * Commander reputation (access rank 1–5) with each engineer, from {@code EngineerProgress} journal events.
 *
 * <p>Rank 5 unlocks the optimized roll schedule (1 roll for G1, 2 for G2, …). Lower ranks need more
 * rolls for the same grade. Remote Workshop (pinned) crafts do not raise rank — only workshop visits do.
 */
public final class EngineerReputationTracker {

    private final Map<String, Integer> rankByEngineer = new ConcurrentHashMap<>();
    private volatile Runnable changeCallback;

    public void setChangeCallback(Runnable changeCallback) {
        this.changeCallback = changeCallback;
    }

    /** Access rank 0–5; 0 means unknown / not unlocked. */
    public int rank(String engineerName) {
        if (engineerName == null || engineerName.isBlank()) {
            return 0;
        }
        return rankByEngineer.getOrDefault(normalize(engineerName), 0);
    }

    /**
     * Highest known rank among {@code engineerNames} (0 if none are tracked).
     */
    public int bestRank(Iterable<String> engineerNames) {
        int best = 0;
        if (engineerNames == null) {
            return 0;
        }
        for (String name : engineerNames) {
            best = Math.max(best, rank(name));
        }
        return best;
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(rankByEngineer));
    }

    public boolean applyEvent(EliteLogEvent event) {
        if (!(event instanceof EngineerProgressEvent progress)) {
            return false;
        }
        boolean changed = false;
        if (progress.isFullSnapshot()) {
            // Startup dump lists every engineer; replace tracked ranks from it.
            Map<String, Integer> next = new HashMap<>();
            for (EngineerProgressEvent.EngineerRank entry : progress.getEngineers()) {
                int r = entry.rank();
                if (r <= 0 && entry.isUnlocked()) {
                    r = 1;
                }
                if (r > 0) {
                    next.put(normalize(entry.engineer()), Integer.valueOf(r));
                }
            }
            if (!next.equals(rankByEngineer)) {
                rankByEngineer.clear();
                rankByEngineer.putAll(next);
                changed = true;
            }
        } else {
            for (EngineerProgressEvent.EngineerRank entry : progress.getEngineers()) {
                String key = normalize(entry.engineer());
                if (key.isEmpty()) {
                    continue;
                }
                int r = entry.rank();
                if (r <= 0) {
                    if ("Unlocked".equalsIgnoreCase(entry.progress())) {
                        r = Math.max(1, rankByEngineer.getOrDefault(key, 0));
                        if (r <= 0) {
                            r = 1;
                        }
                    } else {
                        continue;
                    }
                }
                Integer prev = rankByEngineer.put(key, Integer.valueOf(r));
                if (prev == null || prev.intValue() != r) {
                    changed = true;
                }
            }
        }
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public void bootstrapFromJournal(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        Runnable previous = changeCallback;
        changeCallback = null;
        try {
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            for (EliteLogEvent event : reader.readAllEvents()) {
                if (event.getType() == EliteEventType.ENGINEER_PROGRESS) {
                    applyEvent(event);
                }
            }
        } catch (Exception ignored) {
            // journal unavailable
        } finally {
            changeCallback = previous;
        }
    }

    static String normalize(String engineerName) {
        if (engineerName == null) {
            return "";
        }
        return engineerName.trim().toLowerCase(Locale.ROOT);
    }

    private void notifyChanged() {
        Runnable cb = changeCallback;
        if (cb != null) {
            cb.run();
        }
    }
}
