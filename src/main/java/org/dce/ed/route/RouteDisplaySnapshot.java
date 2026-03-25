package org.dce.ed.route;

import java.util.List;

/**
 * Immutable read model for route table rendering after a layout pass.
 */
public record RouteDisplaySnapshot(long revision, List<RouteEntry> displayedEntries) {

    public RouteDisplaySnapshot {
        displayedEntries = displayedEntries == null ? List.of() : List.copyOf(displayedEntries);
    }
}
