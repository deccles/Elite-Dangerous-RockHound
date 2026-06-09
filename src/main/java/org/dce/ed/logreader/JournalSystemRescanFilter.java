package org.dce.ed.logreader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.dce.ed.logreader.event.FssAllBodiesFoundEvent;
import org.dce.ed.logreader.event.FssBodySignalsEvent;
import org.dce.ed.logreader.event.FssDiscoveryScanEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.SaasignalsFoundEvent;
import org.dce.ed.logreader.event.ScanBaryCentreEvent;
import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanOrganicEvent;

/**
 * Selects journal events belonging to one system for a targeted replay through
 * {@link org.dce.ed.state.SystemEventProcessor}. Parsing stays in {@link EliteJournalReader} /
 * {@link EliteLogParser}; this only filters already-parsed events.
 */
public final class JournalSystemRescanFilter {

    private JournalSystemRescanFilter() {
    }

    public static List<EliteLogEvent> filterForSystemRescan(List<EliteLogEvent> events,
            String targetSystemName, long targetSystemAddress) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        if (targetSystemName == null || targetSystemName.isBlank()) {
            return List.of();
        }
        List<EliteLogEvent> out = new ArrayList<>();
        for (EliteLogEvent event : events) {
            if (event != null && isReplayableSystemEvent(event)
                    && belongsToSystem(event, targetSystemName, targetSystemAddress)) {
                out.add(event);
            }
        }
        return out;
    }

    /** Body / FSS / exo events for one system, plus transitions that seed {@code StarPos}. */
    public static boolean isReplayableSystemEvent(EliteLogEvent event) {
        return event instanceof ScanEvent
                || event instanceof ScanBaryCentreEvent
                || event instanceof FssDiscoveryScanEvent
                || event instanceof FssAllBodiesFoundEvent
                || event instanceof FssBodySignalsEvent
                || event instanceof SaasignalsFoundEvent
                || event instanceof ScanOrganicEvent
                || event instanceof LocationEvent
                || event instanceof FsdJumpEvent;
    }

    public static boolean belongsToSystem(EliteLogEvent event, String targetSystemName, long targetSystemAddress) {
        if (event instanceof ScanEvent e) {
            return matchesSystem(e.getStarSystem(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        if (event instanceof ScanBaryCentreEvent e) {
            return matchesSystem(e.getStarSystem(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        if (event instanceof FssDiscoveryScanEvent e) {
            return matchesSystem(e.getSystemName(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        if (event instanceof FssAllBodiesFoundEvent e) {
            return matchesSystem(e.getSystemName(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        if (event instanceof FssBodySignalsEvent e) {
            return matchesSystemAddressOnly(e.getSystemAddress(), targetSystemAddress);
        }
        if (event instanceof SaasignalsFoundEvent e) {
            return matchesSystemAddressOnly(e.getSystemAddress(), targetSystemAddress);
        }
        if (event instanceof ScanOrganicEvent e) {
            return matchesSystemAddressOnly(e.getSystemAddress(), targetSystemAddress);
        }
        if (event instanceof LocationEvent e) {
            return matchesSystem(e.getStarSystem(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        if (event instanceof FsdJumpEvent e) {
            return matchesSystem(e.getStarSystem(), e.getSystemAddress(), targetSystemName, targetSystemAddress);
        }
        return false;
    }

    private static boolean matchesSystem(String eventSystemName, long eventSystemAddress,
            String targetSystemName, long targetSystemAddress) {
        if (matchesSystemAddressOnly(eventSystemAddress, targetSystemAddress)) {
            return true;
        }
        return nameMatches(eventSystemName, targetSystemName);
    }

    private static boolean matchesSystemAddressOnly(long eventSystemAddress, long targetSystemAddress) {
        return targetSystemAddress != 0L && eventSystemAddress != 0L && eventSystemAddress == targetSystemAddress;
    }

    private static boolean nameMatches(String candidate, String targetName) {
        if (candidate == null || targetName == null) {
            return false;
        }
        return candidate.trim().equalsIgnoreCase(targetName.trim());
    }
}
