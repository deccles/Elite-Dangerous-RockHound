package org.dce.ed.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.systemmap.SystemMapModel;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Journal {@code Scan.Parents[]} as stored on {@link BodyInfo} and formatted for hierarchy / debug views.
 * Order is innermost parent first (Elite journal convention).
 * <p>
 * Hierarchy graph contract (see {@link org.dce.ed.systemmap.SystemMapHierarchyBuilder}):
 * <ul>
 *   <li><b>Tree edges</b> — map orbit parent from {@link SystemMapModel#resolveParentBodyId(int)}
 *       (co-orbit majors attach under planet-binary {@code Null:N} hub keys).</li>
 *   <li><b>Parents line</b> — journal chain only ({@link #formatParentsLineForMapBody} /
 *       {@link #formatPlanetBinaryHubParentsLine}); human names, never raw journal ids when resolvable.</li>
 *   <li><b>Subtitle</b> — map orbit parent via {@link #formatMapParentLabel}; when journal innermost parent
 *       differs from map, both are shown ({@code journal: … · map: …}).</li>
 * </ul>
 */
public final class JournalParentRefs {

    private JournalParentRefs() {
    }

    public static List<String> fromScanParents(List<ScanEvent.ParentRef> parents) {
        if (parents == null || parents.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(parents.size());
        for (ScanEvent.ParentRef p : parents) {
            if (p == null || p.getType() == null) {
                continue;
            }
            out.add(p.getType() + ":" + p.getBodyId());
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    /**
     * One-line label for hierarchy graph nodes, e.g.
     * {@code Parents: Null:14 → A → Null:0}.
     */
    public static String formatParentsLine(List<String> refs, Map<Integer, BodyInfo> bodies) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("Parents: ");
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) {
                sb.append(" → ");
            }
            sb.append(resolveRefLabel(refs.get(i), bodies));
        }
        return sb.toString();
    }

    /** Fallback when only {@link BodyInfo#getImmediateParentBodyId()} is known (older cache rows). */
    public static String formatImmediateParentOnly(int immediateParentBodyId, Map<Integer, BodyInfo> bodies) {
        if (immediateParentBodyId < 0) {
            return null;
        }
        String ref = immediateParentBodyId == 0 ? "Null:0" : "id:" + immediateParentBodyId;
        return "Parents: " + resolveRefLabel(ref, bodies);
    }

    /**
     * Parents line for a map body: journal {@code Parents[]} when present, else immediate parent, with
     * {@link SystemMapModel#resolveParentBodyId(int)} fallback when ids do not match cache map keys.
     */
    public static String formatParentsLineForMapBody(BodyInfo body, int mapBodyId, Map<Integer, BodyInfo> bodies,
            SystemMapModel model) {
        if (body == null || bodies == null) {
            return null;
        }
        if (!body.getJournalParentRefs().isEmpty()) {
            String line = formatParentsLine(body.getJournalParentRefs(), bodies);
            if (!isUnresolvedParentsLine(line)) {
                return line;
            }
        }
        int ip = body.getImmediateParentBodyId();
        if (ip >= 0) {
            String line = formatImmediateParentOnly(ip, bodies);
            if (!isUnresolvedParentsLine(line)) {
                return line;
            }
        }
        if (model != null && mapBodyId >= 0) {
            String resolved = formatResolvedOrbitParentOnly(mapBodyId, bodies, model);
            if (resolved != null) {
                return resolved;
            }
        }
        if (ip >= 0) {
            return formatImmediateParentOnly(ip, bodies);
        }
        return null;
    }

    static boolean isUnresolvedParentsLine(String line) {
        if (line == null || !line.startsWith("Parents: ")) {
            return true;
        }
        String tail = line.substring("Parents: ".length()).trim();
        if (tail.isEmpty()) {
            return true;
        }
        if (tail.contains("→")) {
            String[] parts = tail.split("→");
            String last = parts[parts.length - 1].trim();
            return last.matches("\\d+");
        }
        return tail.matches("\\d+");
    }

    static String formatResolvedOrbitParentOnly(int mapBodyId, Map<Integer, BodyInfo> bodies, SystemMapModel model) {
        int parentKey = model.resolveParentBodyId(mapBodyId);
        if (parentKey < 0) {
            return null;
        }
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parentKey)) {
            int nullId = SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(parentKey);
            return "Parents: Null:" + nullId;
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentKey));
        String label = displayLabelForBody(parent);
        if (label == null || label.isEmpty()) {
            label = String.valueOf(parentKey);
        }
        return "Parents: " + label;
    }

    static String resolveRefLabel(String ref, Map<Integer, BodyInfo> bodies) {
        if (ref == null || ref.isEmpty()) {
            return "?";
        }
        if (ref.startsWith("id:")) {
            try {
                int id = Integer.parseInt(ref.substring(3).trim());
                return labelForBodyId(id, "id", bodies);
            } catch (NumberFormatException e) {
                return ref;
            }
        }
        int colon = ref.indexOf(':');
        if (colon <= 0 || colon >= ref.length() - 1) {
            return ref;
        }
        String type = ref.substring(0, colon).trim();
        int id;
        try {
            id = Integer.parseInt(ref.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return ref;
        }
        if ("Null".equalsIgnoreCase(type) && id == 0) {
            return "Null:0";
        }
        return labelForBodyId(id, type, bodies);
    }

    private static String labelForBodyId(int id, String type, Map<Integer, BodyInfo> bodies) {
        if ("Null".equalsIgnoreCase(type)) {
            return "Null:" + id;
        }
        BodyInfo row = findBodyByJournalId(id, bodies);
        if (row != null) {
            if (row.isScanBarycentreRow()) {
                return "Null:" + id;
            }
            String label = displayLabelForBody(row);
            if (label != null && !label.isEmpty()) {
                return label;
            }
        }
        return String.valueOf(id);
    }

    /** Resolve journal {@code BodyID} even when the bodies map key differs from {@link BodyInfo#getBodyId()}. */
    private static BodyInfo findBodyByJournalId(int journalBodyId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || journalBodyId < 0) {
            return null;
        }
        BodyInfo direct = bodies.get(Integer.valueOf(journalBodyId));
        if (direct != null) {
            return direct;
        }
        for (BodyInfo row : bodies.values()) {
            if (row != null && row.getBodyId() == journalBodyId) {
                return row;
            }
        }
        return null;
    }

    private static String displayLabelForBody(BodyInfo row) {
        if (row == null) {
            return null;
        }
        String shortName = row.getShortName();
        if (shortName != null && !shortName.isEmpty()) {
            return shortName.trim();
        }
        return shortenBodyName(row.getBodyName());
    }

    private static String shortenBodyName(String bodyName) {
        if (bodyName == null) {
            return null;
        }
        String t = bodyName.trim();
        int sp = t.lastIndexOf(' ');
        if (sp >= 0 && sp < t.length() - 1) {
            return t.substring(sp + 1).trim();
        }
        return t;
    }

    /** Display line for synthetic planet-binary hub keys ({@code -50014} → journal null 14). */
    public static String formatPlanetBinaryHubParentsLine(int journalNullId, Map<Integer, BodyInfo> bodies) {
        return formatPlanetBinaryHubParentsLine(journalNullId, bodies, null);
    }

    /**
     * Outer parents of a planet-binary hub — never the hub's own {@code Null:N} alone.
     */
    public static String formatPlanetBinaryHubParentsLine(int journalNullId, Map<Integer, BodyInfo> bodies,
            SystemMapModel model) {
        List<String> refs = collectPlanetBinaryHubParentRefs(journalNullId, bodies);
        refs = stripLeadingHubNullRef(refs, journalNullId);
        if (refs != null && !refs.isEmpty()) {
            return formatParentsLine(refs, bodies);
        }
        if (bodies != null && model != null) {
            int hubKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(journalNullId);
            int starKey = SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(hubKey, bodies);
            if (starKey >= 0) {
                BodyInfo star = bodies.get(Integer.valueOf(starKey));
                String label = displayLabelForBody(star);
                if (label != null && !label.isEmpty()) {
                    return "Parents: " + label + " → Null:0";
                }
            }
        }
        return null;
    }

    static List<String> collectPlanetBinaryHubParentRefs(int journalNullId, Map<Integer, BodyInfo> bodies) {
        if (bodies == null || journalNullId <= 0) {
            return List.of();
        }
        BodyInfo scanRow = bodies.get(Integer.valueOf(journalNullId));
        if (scanRow != null && scanRow.isScanBarycentreRow()) {
            List<String> refs = scanRow.getJournalParentRefs();
            if (refs != null && !refs.isEmpty()) {
                return refs;
            }
        }
        for (BodyInfo b : bodies.values()) {
            if (b == null || b.isScanBarycentreRow() || SystemOrbitGeometry.isMoonSatelliteBody(b, bodies)) {
                continue;
            }
            if (b.getImmediateParentBodyId() == journalNullId) {
                List<String> refs = b.getJournalParentRefs();
                if (refs != null && !refs.isEmpty()) {
                    return refs;
                }
            }
        }
        return List.of();
    }

    /**
     * Map orbit parent label for hierarchy subtitles and debug trees (human names, {@code Null:N} hubs).
     */
    public static String formatMapParentLabel(SystemMapModel model, Map<Integer, BodyInfo> bodies, int mapBodyId,
            int arrivalStarMapKey) {
        if (model == null || bodies == null || mapBodyId < 0) {
            return "?";
        }
        int parentKey = model.resolveParentBodyId(mapBodyId);
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(parentKey)) {
            return "Null:" + SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(parentKey);
        }
        if (parentKey < 0) {
            return "Null:0";
        }
        BodyInfo parent = bodies.get(Integer.valueOf(parentKey));
        String label = displayLabelForBody(parent);
        if (label != null && !label.isEmpty()) {
            return label;
        }
        if (parentKey == arrivalStarMapKey) {
            return "A";
        }
        return "id:" + parentKey;
    }

    /** Innermost journal parent (first {@code Parents[]} entry or {@link BodyInfo#getImmediateParentBodyId()}). */
    public static String formatInnermostJournalParent(BodyInfo body, Map<Integer, BodyInfo> bodies) {
        if (body == null || bodies == null) {
            return "?";
        }
        if (!body.getJournalParentRefs().isEmpty()) {
            return resolveRefLabel(body.getJournalParentRefs().get(0), bodies);
        }
        int ip = body.getImmediateParentBodyId();
        if (ip < 0) {
            return "?";
        }
        String ref = ip == 0 ? "Null:0" : "id:" + ip;
        return resolveRefLabel(ref, bodies);
    }

    public static boolean journalInnermostDiffersFromMap(BodyInfo body, Map<Integer, BodyInfo> bodies, int mapBodyId,
            SystemMapModel model, int arrivalStarMapKey) {
        if (body == null || model == null) {
            return false;
        }
        String journal = formatInnermostJournalParent(body, bodies);
        String map = formatMapParentLabel(model, bodies, mapBodyId, arrivalStarMapKey);
        if ("?".equals(journal) || journal.matches("\\d+")) {
            return false;
        }
        return !journal.equals(map);
    }

    static List<String> stripLeadingHubNullRef(List<String> refs, int hubNullId) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        int start = 0;
        while (start < refs.size()) {
            String ref = refs.get(start);
            if (ref != null && ref.equalsIgnoreCase("Null:" + hubNullId)) {
                start++;
                continue;
            }
            break;
        }
        if (start == 0) {
            return refs;
        }
        if (start >= refs.size()) {
            return List.of();
        }
        return List.copyOf(refs.subList(start, refs.size()));
    }
}
