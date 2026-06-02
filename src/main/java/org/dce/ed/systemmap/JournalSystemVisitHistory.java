package org.dce.ed.systemmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.event.CarrierJumpEvent;
import org.dce.ed.logreader.event.FsdJumpEvent;
import org.dce.ed.logreader.event.LocationEvent;
import org.dce.ed.logreader.event.ScanBaryCentreEvent;
import org.dce.ed.logreader.event.ScanEvent;

/** Ordered system names from journal Location / FSDJump / CarrierJump events. */
public final class JournalSystemVisitHistory {

    private JournalSystemVisitHistory() {
    }

    public static List<String> loadTransitionSystemNames(Path journalDirectory) throws IOException {
        if (journalDirectory == null) {
            return List.of();
        }
        EliteJournalReader reader = new EliteJournalReader(journalDirectory);
        return transitionSystemNamesFromEvents(reader.readAllEvents());
    }

    /** Visit history limited to systems with Scan / ScanBaryCentre in journal or cached scan rows. */
    public static List<String> loadViewableTransitionSystemNames(Path journalDirectory) throws IOException {
        if (journalDirectory == null) {
            return List.of();
        }
        EliteJournalReader reader = new EliteJournalReader(journalDirectory);
        List<EliteLogEvent> events = reader.readAllEvents();
        Set<String> scannedSystems = scannedSystemNamesFromEvents(events);
        List<String> transitions = transitionSystemNamesFromEvents(events);
        List<String> viewable = new ArrayList<>();
        for (String name : transitions) {
            if (scannedSystems.contains(name.toUpperCase(Locale.ROOT))
                    || SystemHierarchyAvailability.hasCachedHierarchyData(name)) {
                viewable.add(name);
            }
        }
        return viewable;
    }

    static Set<String> scannedSystemNamesFromEvents(List<EliteLogEvent> events) {
        Set<String> out = new HashSet<>();
        if (events == null) {
            return out;
        }
        for (EliteLogEvent event : events) {
            String sys = null;
            if (event instanceof ScanEvent scan) {
                sys = scan.getStarSystem();
            } else if (event instanceof ScanBaryCentreEvent bary) {
                sys = bary.getStarSystem();
            }
            if (sys != null && !sys.isBlank()) {
                out.add(sys.trim().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    static List<String> transitionSystemNamesFromEvents(List<EliteLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String last = null;
        for (EliteLogEvent event : events) {
            String sys = systemNameFromTransition(event);
            if (sys == null || sys.isBlank()) {
                continue;
            }
            String trimmed = sys.trim();
            if (last != null && last.equalsIgnoreCase(trimmed)) {
                continue;
            }
            out.add(trimmed);
            last = trimmed;
        }
        return out;
    }

    private static String systemNameFromTransition(EliteLogEvent event) {
        if (event instanceof LocationEvent le) {
            return le.getStarSystem();
        }
        if (event instanceof FsdJumpEvent fj) {
            return fj.getStarSystem();
        }
        if (event instanceof CarrierJumpEvent cj) {
            return cj.getStarSystem();
        }
        return null;
    }
}
