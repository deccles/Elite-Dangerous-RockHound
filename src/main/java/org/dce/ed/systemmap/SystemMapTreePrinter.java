package org.dce.ed.systemmap;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * ASCII system-map tree for journal vs pipeline comparison (debug / validation).
 */
public final class SystemMapTreePrinter {

    private static final double LS = SystemOrbitGeometry.LIGHT_SECOND_METRES;

    private SystemMapTreePrinter() {
    }

    public static void printTree(String systemName) {
        printTree(systemName, System.out);
    }

    public static void printTree(String systemName, PrintStream out) {
        String source = System.getProperty("edo.tree.source", "both").trim().toLowerCase(Locale.ROOT);
        Path journalDir = journalDirectoryFromEnv();
        boolean journalOk = journalDir != null && java.nio.file.Files.isDirectory(journalDir);
        boolean printJournal = "journal".equals(source) || ("both".equals(source) && journalOk);
        boolean printCache = "cache".equals(source) || "both".equals(source);

        if (printJournal && journalOk) {
            try {
                out.println("=== Journal tree (Scan parents on log) ===");
                out.println(formatTreeFromJournal(journalDir, systemName));
            } catch (IOException e) {
                out.println("Journal tree unavailable: " + e.getMessage());
            }
        } else if ("journal".equals(source)) {
            out.println("Journal tree unavailable. Set EDO_JOURNAL_DIR or run from Saved Games folder.");
        }

        if (printCache) {
            String modelTree = formatTreeFromCache(systemName);
            if (modelTree == null && journalOk) {
                try {
                    modelTree = formatTreeFromJournal(journalDir, systemName);
                } catch (IOException ignored) {
                    // fall through
                }
            }
            out.println("=== Model tree (after SystemMapPipeline) ===");
            if (modelTree != null) {
                out.println(modelTree);
            } else {
                out.println("No cache entry for " + systemName
                        + ". Scan in-game or set EDO_JOURNAL_DIR for journal-built model.");
            }
        }
    }

    public static String formatTreeFromJournal(Path journalDir, String systemName) throws IOException {
        SystemState state = JournalSystemMapLoader.loadFromJournal(journalDir, systemName);
        SystemMapModel model = SystemMapPipeline.build(systemName, state.getBodies(), Instant.EPOCH, true);
        return formatTree(model, state.getBodies(), true);
    }

    public static String formatTreeFromCache(String systemName) {
        long address = systemAddressFromEnv();
        if (address == 0L) {
            return null;
        }
        SystemCache cache = SystemCache.getInstance();
        CachedSystem cs = cache.get(address, systemName);
        if (cs == null || cs.bodies == null || cs.bodies.isEmpty()) {
            return null;
        }
        SystemState loaded = new SystemState();
        cache.loadInto(loaded, cs);
        if (loaded.getBodies().isEmpty()) {
            return null;
        }
        SystemMapModel model = SystemMapPipeline.build(cs.systemName, loaded.getBodies(), Instant.EPOCH, true);
        return formatTree(model, loaded.getBodies(), false);
    }

    private static long systemAddressFromEnv() {
        String env = System.getenv("EDO_SYSTEM_ADDRESS");
        if (env == null || env.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(env.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String formatComparison(String systemName, Path journalDir) throws IOException {
        SystemState journalState = JournalSystemMapLoader.loadFromJournal(journalDir, systemName);
        SystemMapModel journalModel = SystemMapPipeline.build(systemName, journalState.getBodies(), Instant.EPOCH, true);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Parent comparison (journal immediate vs model resolved) ===\n");
        List<Integer> ids = sortedBodyIds(journalState.getBodies());
        int arrivalStar = SystemOrbitGeometry.primaryAnchorBodyMapKey(journalState.getBodies());
        for (int id : ids) {
            BodyInfo b = journalState.getBodies().get(Integer.valueOf(id));
            if (b == null || b.isScanBarycentreRow()) {
                continue;
            }
            String label = b.getShortName() != null ? b.getShortName() : String.valueOf(id);
            String journalParent = formatJournalParent(b);
            String resolved = formatResolvedParent(journalModel, journalState.getBodies(), id, arrivalStar);
            String flag = journalParent.equals(resolved) ? "" : "  *** MISMATCH ***";
            sb.append(String.format("  %-10s journal=%-16s model=%-20s%s%n", label, journalParent, resolved, flag));
        }
        return sb.toString();
    }

    public static String formatTree(SystemMapModel model, Map<Integer, BodyInfo> bodies, boolean journalImmediateLabels) {
        if (model == null || bodies == null || bodies.isEmpty()) {
            return "(empty)";
        }
        int arrivalStar = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        Map<Integer, List<Integer>> children = new HashMap<>();
        Set<Integer> allNodes = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (e.getValue().isScanBarycentreRow()) {
                allNodes.add(Integer.valueOf(id));
                children.computeIfAbsent(Integer.valueOf(-1), k -> new ArrayList<>()).add(Integer.valueOf(id));
                continue;
            }
            int p = model.resolveParentBodyId(id);
            children.computeIfAbsent(Integer.valueOf(p), k -> new ArrayList<>()).add(Integer.valueOf(id));
            allNodes.add(Integer.valueOf(id));
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p)) {
                allNodes.add(Integer.valueOf(p));
            }
        }
        for (Integer p : new ArrayList<>(children.keySet())) {
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p.intValue())) {
                children.computeIfAbsent(Integer.valueOf(-1), k -> new ArrayList<>()).add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Null:0 (system barycentre)\n");
        appendChildren(sb, "", true, -1, children, bodies, model, arrivalStar, journalImmediateLabels);
        appendFooter(sb, model, bodies, arrivalStar);
        return sb.toString();
    }

    private static void appendChildren(StringBuilder sb, String prefix, boolean last, int parentKey,
            Map<Integer, List<Integer>> children, Map<Integer, BodyInfo> bodies, SystemMapModel model,
            int arrivalStar, boolean journalImmediateLabels) {
        List<Integer> kids = children.get(Integer.valueOf(parentKey));
        if (kids == null) {
            return;
        }
        kids.sort(bodySiblingComparator(bodies));
        for (int i = 0; i < kids.size(); i++) {
            boolean childLast = i == kids.size() - 1;
            String branch = prefix + (last ? "    " : "│   ");
            String connector = prefix + (last ? "└── " : "├── ");
            int id = kids.get(i).intValue();
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(id)) {
                int nullId = SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(id);
                String resolved = formatResolvedParent(model, bodies, id, arrivalStar);
                sb.append(connector).append("Null:").append(nullId).append(" (subsystem barycentre)");
                if (!journalImmediateLabels) {
                    sb.append(" [resolved: ").append(resolved).append(']');
                }
                sb.append('\n');
                appendChildren(sb, branch, childLast, id, children, bodies, model, arrivalStar, journalImmediateLabels);
                continue;
            }
            BodyInfo b = bodies.get(Integer.valueOf(id));
            if (b == null) {
                continue;
            }
            if (b.isScanBarycentreRow()) {
                sb.append(connector).append("Null:").append(id).append(" (ScanBaryCentre row)\n");
                appendChildren(sb, branch, childLast, id, children, bodies, model, arrivalStar, journalImmediateLabels);
                continue;
            }
            String type = bodyTypeSuffix(b);
            String resolved = formatResolvedParent(model, bodies, id, arrivalStar);
            String parentHint = journalImmediateLabels ? formatJournalParent(b) : ("→ parent " + resolved);
            double distFromA = arrivalStar >= 0
                    ? Math.hypot(model.mapPlaneX(id) - model.mapPlaneX(arrivalStar),
                            model.mapPlaneY(id) - model.mapPlaneY(arrivalStar)) / LS
                    : Double.NaN;
            sb.append(connector).append(b.getShortName()).append(type);
            sb.append(" [").append(parentHint).append(']');
            if (Double.isFinite(distFromA) && arrivalStar >= 0 && id != arrivalStar) {
                sb.append(String.format(" [%.1f Ls from A]", distFromA));
            }
            sb.append('\n');
            appendChildren(sb, branch, childLast, id, children, bodies, model, arrivalStar, journalImmediateLabels);
        }
    }

    private static void appendFooter(StringBuilder sb, SystemMapModel model, Map<Integer, BodyInfo> bodies,
            int arrivalStar) {
        sb.append(String.format("%n--- mapPlane footer ---%n"));
        sb.append("hasBarycentreMutualRing=").append(model.hasBarycentreMutualRing()).append('\n');
        sb.append("orbitPolylines=").append(model.orbitPolylines().size()).append('\n');
        sb.append("schematicBranchRings=").append(model.schematicBranchRingCount()).append('\n');
        if (arrivalStar >= 0) {
            int bId = findShortName(bodies, "B");
            if (bId >= 0) {
                double d = Math.hypot(model.mapPlaneX(bId) - model.mapPlaneX(arrivalStar),
                        model.mapPlaneY(bId) - model.mapPlaneY(arrivalStar)) / LS;
                sb.append(String.format("dist(B,A)=%.1f Ls%n", d));
            }
        }
    }

    private static Comparator<Integer> bodySiblingComparator(Map<Integer, BodyInfo> bodies) {
        return (a, b) -> {
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(a.intValue())) {
                return -1;
            }
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(b.intValue())) {
                return 1;
            }
            BodyInfo ba = bodies.get(a);
            BodyInfo bb = bodies.get(b);
            if (ba != null && ba.isScanBarycentreRow() && (bb == null || !bb.isScanBarycentreRow())) {
                return -1;
            }
            if (bb != null && bb.isScanBarycentreRow() && (ba == null || !ba.isScanBarycentreRow())) {
                return 1;
            }
            boolean starA = ba != null && ba.getStarType() != null;
            boolean starB = bb != null && bb.getStarType() != null;
            if (starA != starB) {
                return starA ? -1 : 1;
            }
            double da = ba != null ? ba.getDistanceLs() : 0.0;
            double db = bb != null ? bb.getDistanceLs() : 0.0;
            int c = Double.compare(da, db);
            if (c != 0) {
                return c;
            }
            String sa = ba != null && ba.getShortName() != null ? ba.getShortName() : "";
            String sb = bb != null && bb.getShortName() != null ? bb.getShortName() : "";
            return sa.compareTo(sb);
        };
    }

    static String formatResolvedParent(SystemMapModel model, Map<Integer, BodyInfo> bodies, int bodyId,
            int arrivalStar) {
        int p = model.resolveParentBodyId(bodyId);
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p)) {
            return "planetBinary:" + SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(p);
        }
        if (p < 0) {
            return "barycentre";
        }
        BodyInfo parent = bodies.get(Integer.valueOf(p));
        if (parent != null && parent.getShortName() != null) {
            return parent.getShortName();
        }
        if (p == arrivalStar) {
            return "A";
        }
        return "id:" + p;
    }

    private static String formatJournalParent(BodyInfo b) {
        if (b == null) {
            return "?";
        }
        int ip = b.getImmediateParentBodyId();
        if (ip == 0) {
            return "Null:0";
        }
        if (ip > 0) {
            return "id:" + ip;
        }
        return "unset";
    }

    private static String bodyTypeSuffix(BodyInfo b) {
        if (b.getStarType() != null && !b.getStarType().isEmpty()) {
            return " (" + b.getStarType() + ", id " + b.getBodyId() + ")";
        }
        String pc = b.getPlanetClass();
        if (pc != null && !pc.isEmpty()) {
            return " (" + pc + ")";
        }
        return " (id " + b.getBodyId() + ")";
    }

    private static List<Integer> sortedBodyIds(Map<Integer, BodyInfo> bodies) {
        List<Integer> ids = new ArrayList<>(bodies.keySet());
        ids.sort(bodySiblingComparator(bodies));
        return ids;
    }

    private static int findShortName(Map<Integer, BodyInfo> bodies, String shortName) {
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() != null && shortName.equals(e.getValue().getShortName())) {
                return e.getKey().intValue();
            }
        }
        return -1;
    }

    private static Path journalDirectoryFromEnv() {
        String env = System.getenv("EDO_JOURNAL_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        return JournalSystemMapLoader.defaultJournalDirectory();
    }
}
