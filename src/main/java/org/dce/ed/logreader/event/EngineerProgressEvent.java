package org.dce.ed.logreader.event;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.EliteEventType;
import org.dce.ed.logreader.EliteLogEvent;

import com.google.gson.JsonObject;

/**
 * Journal {@code EngineerProgress}: full engineer list on startup, or a single-engineer rank update
 * while crafting at their workshop.
 */
public class EngineerProgressEvent extends EliteLogEvent {

    public record EngineerRank(String engineer, long engineerId, String progress, int rank, int rankProgress) {
        public EngineerRank {
            engineer = engineer != null ? engineer : "";
            progress = progress != null ? progress : "";
            rank = Math.max(0, Math.min(5, rank));
            rankProgress = Math.max(0, rankProgress);
        }

        public boolean isUnlocked() {
            return rank > 0 || "Unlocked".equalsIgnoreCase(progress);
        }
    }

    private final List<EngineerRank> engineers;
    /** True when this event is a full {@code Engineers[]} snapshot rather than a single update. */
    private final boolean fullSnapshot;

    public EngineerProgressEvent(Instant timestamp,
                                 JsonObject rawJson,
                                 List<EngineerRank> engineers,
                                 boolean fullSnapshot) {
        super(timestamp, EliteEventType.ENGINEER_PROGRESS, rawJson);
        this.engineers = engineers != null ? List.copyOf(engineers) : List.of();
        this.fullSnapshot = fullSnapshot;
    }

    public List<EngineerRank> getEngineers() {
        return engineers;
    }

    public boolean isFullSnapshot() {
        return fullSnapshot;
    }
}
