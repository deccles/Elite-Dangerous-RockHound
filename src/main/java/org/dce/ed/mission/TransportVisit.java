package org.dce.ed.mission;

/** A cargo-free courier or passenger destination. */
public record TransportVisit(long missionId, String label, TransportLocation destination) {
    public TransportVisit {
        label = label == null || label.isBlank() ? "Mission" : label.trim();
        if (destination == null) throw new IllegalArgumentException("Transport visit requires a destination");
    }
}
