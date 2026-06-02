package org.dce.ed.cache;

import java.util.ArrayList;
import java.util.List;

/**
     * Represents a cached system and its bodies.
     */
    public class CachedSystem {
        public long systemAddress;
        public String systemName;
        public double starPos[];
        public List<CachedBody> bodies = new ArrayList<>();

    /**
     * Exobiology running total (expected credits, unsold).
     * Persisted inside each cached system JSON when using the JSON file backend.
     * When using SQLite, this field is stored in table {@code overlay_global_state} instead
     * and is omitted from {@code systems.payload_json}.
     */
    public Long exobiologyCreditsTotalUnsold;

        public Integer totalBodies;
        public Integer nonBodyCount;
        public Double fssProgress;
        public Boolean allBodiesFound;

        /** Ordered Scan + ScanBaryCentre journal records (JSON). */
        public String journalEventLogJson;
        /** Frozen {@link org.dce.systemmodel.snapshot.SystemSnapshot}. */
        public String modelSnapshotJson;
        /** OK | INCOMPLETE | ERROR from last model rebuild. */
        public String modelState;
    }
