package org.dce.ed.systemmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.cache.CachedSystem;
import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.SystemState;
import org.dce.ed.systemmap.SystemMapSystemLoader.Loaded;
import org.dce.ed.systemmodel.SystemModelService;
import org.dce.systemmodel.journal.JournalRecord;
import org.dce.systemmodel.journal.ScanRecord;
import org.dce.systemmodel.journal.ParentRef;
import org.dce.systemmodel.hierarchy.BarycentreHubPolicy;
import org.dce.systemmodel.journal.JournalEventLogUtil;
import org.dce.systemmodel.model.BarycentreNode;
import org.dce.systemmodel.model.BodyKind;
import org.dce.systemmodel.model.BodyNode;
import org.dce.systemmodel.model.HierarchyGraph;
import org.dce.systemmodel.model.HierarchyKeys;
import org.dce.systemmodel.model.SystemModel;
import org.dce.systemmodel.snapshot.SystemSnapshot;

/**
 * Builds {@link SystemMapHierarchyBuilder.Graph} from journal-authoritative {@link SystemModel} only.
 * UI must not call {@link SystemMapHierarchyBuilder#build} (map-pipeline topology).
 */
public final class SystemModelHierarchyBuilder {

    private static final int ROOT_KEY = -1;
    /** More than this many {@code ScanBaryCentre} nodes directly under {@code Null:0} means broken topology. */
    private static final int MAX_BARYCENTRES_AT_ROOT = 2;

    private SystemModelHierarchyBuilder() {
    }

    /**
     * Builds from bodies already loaded for the map (cache and/or journal union). Prefer this over
     * {@link #buildForCachedSystem(String)} so SQLite body rows are not ignored.
     */
    public static SystemMapHierarchyBuilder.Graph buildForLoaded(Loaded loaded) {
        if (loaded == null || loaded.bodies == null || loaded.bodies.isEmpty()) {
            return null;
        }
        String name = loaded.systemName != null ? loaded.systemName.trim() : "";
        if (name.isEmpty()) {
            return null;
        }
        CachedSystem cs = SystemHierarchyAvailability.resolveRichestCachedSystem(name);
        SystemState state = new SystemState();
        state.setSystemName(name);
        if (cs != null && cs.systemAddress != 0L) {
            state.setSystemAddress(cs.systemAddress);
        }
        List<JournalRecord> normalized = List.of();
        if (cs != null) {
            SystemState fromCache = stateWithJournalLogFromCache(name, cs);
            if (fromCache != null && !fromCache.getJournalEventLog().isEmpty()) {
                normalized = JournalEventLogUtil.normalizeForSystemBuild(name, fromCache.getJournalEventLog());
                if (fromCache.getSystemAddress() != 0L) {
                    state.setSystemAddress(fromCache.getSystemAddress());
                }
            }
        }
        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromBodyInfo(name, normalized, loaded.bodies);
        merged = JournalEventLogUtil.dedupeScansByDesignation(name, merged);
        if (merged.isEmpty()) {
            return null;
        }
        state.setJournalEventLog(merged);
        return buildFromState(state);
    }

    /**
     * Loads the journal event log from SQLite and builds the display graph. Returns {@code null} when no log exists.
     */
    public static SystemMapHierarchyBuilder.Graph buildForCachedSystem(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }
        Loaded loaded = SystemMapSystemLoader.loadFromCache(systemName.trim());
        if (loaded != null) {
            return buildForLoaded(loaded);
        }
        CachedSystem cs = SystemHierarchyAvailability.resolveRichestCachedSystem(systemName.trim());
        if (cs == null) {
            return null;
        }
        return buildFromJournalLogSnapshot(systemName.trim(), cs);
    }

    private static SystemMapHierarchyBuilder.Graph buildFromJournalLogSnapshot(String systemName, CachedSystem cs) {
        SystemState state = stateWithJournalLogFromCache(systemName, cs);
        if (state == null || state.getJournalEventLog().isEmpty()) {
            return null;
        }
        List<JournalRecord> normalized = JournalEventLogUtil.normalizeForSystemBuild(
                systemName, state.getJournalEventLog());
        List<JournalRecord> merged = CachedBodyJournalBridge.mergeMissingFromCache(systemName, normalized, cs);
        merged = JournalEventLogUtil.dedupeScansByDesignation(systemName, merged);
        if (merged.isEmpty()) {
            return null;
        }
        state.setJournalEventLog(merged);
        return buildFromState(state);
    }

    public static SystemMapHierarchyBuilder.Graph buildFromState(SystemState state) {
        SystemModelService.ModelHandle handle = SystemModelService.rebuild(state, false);
        if (handle.model() == null) {
            return null;
        }
        HierarchyGraph hg = handle.model().hierarchy();
        return buildGraph(state.getSystemName(), handle.model(), hg);
    }

    /** Rejects graphs where unresolved barycentres were parked on the synthetic root. */
    public static boolean isUsableHierarchy(SystemMapHierarchyBuilder.Graph graph) {
        if (graph == null || graph.root == null || graph.root.children.isEmpty()) {
            return false;
        }
        int baryAtRoot = 0;
        for (SystemMapHierarchyBuilder.Node child : graph.root.children) {
            if (child.kind == SystemMapHierarchyBuilder.NodeKind.SCAN_BARYCENTRE) {
                baryAtRoot++;
            }
        }
        return baryAtRoot <= MAX_BARYCENTRES_AT_ROOT;
    }

    private static SystemState stateWithJournalLogFromCache(String systemName, CachedSystem cs) {
        SystemState state = new SystemState();
        state.setSystemName(systemName);
        if (cs == null) {
            return state;
        }
        if (cs.systemAddress != 0L) {
            state.setSystemAddress(cs.systemAddress);
        }
        String json = cs.journalEventLogJson;
        if (json == null || json.isBlank()) {
            json = cs.modelSnapshotJson;
        }
        if (json == null || json.isBlank()) {
            return state;
        }
        try {
            SystemSnapshot snap = SystemSnapshot.fromJson(json);
            if (snap.eventLog() != null && !snap.eventLog().isEmpty()) {
                state.setJournalEventLog(snap.eventLog());
                if (snap.systemAddress() != 0L) {
                    state.setSystemAddress(snap.systemAddress());
                }
            }
        } catch (RuntimeException ignored) {
            // leave log empty
        }
        return state;
    }

    public static SystemMapHierarchyBuilder.Graph buildFromSnapshot(String json) {
        SystemSnapshot snap = SystemSnapshot.fromJson(json);
        SystemModel model = snap.toModel();
        return buildGraph(snap.systemName(), model, model.hierarchy());
    }

    static SystemMapHierarchyBuilder.Graph buildGraph(
            String systemName, SystemModel model, HierarchyGraph hg) {
        Map<Integer, SystemMapHierarchyBuilder.Node> built = new HashMap<>();
        SystemMapHierarchyBuilder.Node root = new SystemMapHierarchyBuilder.Node(
                ROOT_KEY, "Null:0", "system barycentre", null, SystemMapHierarchyBuilder.NodeKind.SYSTEM_BARYCENTRE);
        built.put(Integer.valueOf(ROOT_KEY), root);

        for (BodyNode b : model.bodies().values()) {
            built.put(Integer.valueOf(b.bodyId()), bodyNode(b));
        }
        for (int baryId : model.barycentres().keySet()) {
            int mapKey = HierarchyKeys.baryMapKey(baryId);
            model.barycentre(baryId).ifPresent(bc -> built.put(mapKey, baryNode(model, bc)));
        }

        attachHierarchyRootsToSystemBarycentre(model, hg, root, built);
        wireHierarchyChildrenBreadthFirst(hg, root, built);
        linkUnattachedHierarchyChildren(model, hg, built);

        List<SystemMapHierarchyBuilder.Edge> edges = new ArrayList<>();
        collectEdges(root, edges);
        SystemMapHierarchyBuilder.Graph graph = new SystemMapHierarchyBuilder.Graph(systemName, root, edges);
        graph.nodeByKey.putAll(built);
        pruneOrphanNodesFromIndex(graph, built, root);
        HierarchySiblingOrder.sortTree(root);
        return graph;
    }

    /**
     * Walk {@link HierarchyGraph#childrenOf(int)} from stars already under {@code Null:0} so nested barycentres
     * (e.g. {@code Null:32} under {@code Null:67}) are linked before their descendants.
     */
    private static void wireHierarchyChildrenBreadthFirst(
            HierarchyGraph hg,
            SystemMapHierarchyBuilder.Node root,
            Map<Integer, SystemMapHierarchyBuilder.Node> built) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (SystemMapHierarchyBuilder.Node top : root.children) {
            queue.addLast(top.mapKey);
        }
        while (!queue.isEmpty()) {
            int parentKey = queue.removeFirst();
            if (!built.containsKey(parentKey)) {
                continue;
            }
            for (int childKey : hg.childrenOf(parentKey)) {
                if (attachHierarchyChild(built, parentKey, childKey)) {
                    queue.addLast(childKey);
                }
            }
        }
    }

    private static boolean attachHierarchyChild(
            Map<Integer, SystemMapHierarchyBuilder.Node> built, int parentKey, int childKey) {
        SystemMapHierarchyBuilder.Node parent = built.get(parentKey);
        SystemMapHierarchyBuilder.Node child = built.get(childKey);
        if (parent == null || child == null) {
            return false;
        }
        if (child.parentKey == Integer.MIN_VALUE) {
            child.parentKey = parentKey;
            if (!parent.children.contains(child)) {
                parent.children.add(child);
            }
        }
        return true;
    }

    private static void linkUnattachedHierarchyChildren(
            SystemModel model, HierarchyGraph hg, Map<Integer, SystemMapHierarchyBuilder.Node> built) {
        for (BodyNode b : model.bodies().values()) {
            linkIfUnattached(model, hg, built, b.bodyId());
        }
        for (int journalNullId : model.barycentres().keySet()) {
            linkIfUnattached(model, hg, built, HierarchyKeys.baryMapKey(journalNullId));
        }
    }

    private static void linkIfUnattached(
            SystemModel model,
            HierarchyGraph hg,
            Map<Integer, SystemMapHierarchyBuilder.Node> built,
            int childKey) {
        SystemMapHierarchyBuilder.Node child = built.get(childKey);
        if (child == null || child.parentKey != Integer.MIN_VALUE) {
            return;
        }
        Integer parentId = hg.parentOf(childKey);
        if (parentId == null) {
            return;
        }
        int parentKey = hierarchyNodeKey(model, parentId.intValue());
        SystemMapHierarchyBuilder.Node parent = built.get(parentKey);
        if (parent == null) {
            return;
        }
        child.parentKey = parentKey;
        if (!parent.children.contains(child)) {
            parent.children.add(child);
        }
    }

    private static int hierarchyNodeKey(SystemModel model, int hierarchyId) {
        if (hierarchyId == HierarchyKeys.baryMapKey(0)) {
            return ROOT_KEY;
        }
        if (model.barycentres().containsKey(hierarchyId)) {
            return HierarchyKeys.baryMapKey(hierarchyId);
        }
        return hierarchyId;
    }

    /** Journal {@code Null:0} (system barycentre) maps to the synthetic root node. */
    private static boolean orbitsSystemBarycentre(Integer hierarchyParentId) {
        return hierarchyParentId != null && hierarchyParentId.intValue() == HierarchyKeys.baryMapKey(0);
    }

    /** Drop nodes not reachable from {@code Null:0} (filtered structural barycentres). */
    private static void pruneOrphanNodesFromIndex(
            SystemMapHierarchyBuilder.Graph graph,
            Map<Integer, SystemMapHierarchyBuilder.Node> built,
            SystemMapHierarchyBuilder.Node root) {
        java.util.Set<Integer> reachable = new java.util.HashSet<>();
        markReachable(root, reachable);
        built.entrySet().removeIf(e -> !reachable.contains(e.getKey()));
        graph.nodeByKey.keySet().retainAll(reachable);
    }

    private static void markReachable(SystemMapHierarchyBuilder.Node node, java.util.Set<Integer> reachable) {
        if (node == null || !reachable.add(node.mapKey)) {
            return;
        }
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            markReachable(child, reachable);
        }
        for (SystemMapHierarchyBuilder.Node ph : node.collapsePlaceholders) {
            markReachable(ph, reachable);
        }
    }

    /**
     * Only the primary star (and other true roots) attach under synthetic {@code Null:0}. Unresolved barycentres
     * must not be dumped here — that produced the flat {@code Null:67…Null:85} strip.
     */
    private static void attachHierarchyRootsToSystemBarycentre(
            SystemModel model,
            HierarchyGraph hg,
            SystemMapHierarchyBuilder.Node root,
            Map<Integer, SystemMapHierarchyBuilder.Node> built) {
        for (BodyNode b : model.bodies().values()) {
            if (b.kind() != BodyKind.STAR) {
                continue;
            }
            attachIfHierarchyRoot(hg, root, built, b.bodyId());
        }
    }

    private static void attachIfHierarchyRoot(
            HierarchyGraph hg,
            SystemMapHierarchyBuilder.Node root,
            Map<Integer, SystemMapHierarchyBuilder.Node> built,
            int bodyId) {
        Integer parent = hg.parentOf(bodyId);
        if (parent != null && !orbitsSystemBarycentre(parent)) {
            return;
        }
        SystemMapHierarchyBuilder.Node node = built.get(Integer.valueOf(bodyId));
        if (node == null || node.parentKey != Integer.MIN_VALUE) {
            return;
        }
        node.parentKey = ROOT_KEY;
        if (!root.children.contains(node)) {
            root.children.add(node);
        }
    }

    private static void collectEdges(SystemMapHierarchyBuilder.Node node, List<SystemMapHierarchyBuilder.Edge> edges) {
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            edges.add(new SystemMapHierarchyBuilder.Edge(node.mapKey, child.mapKey));
            collectEdges(child, edges);
        }
    }

    private static SystemMapHierarchyBuilder.Node bodyNode(BodyNode b) {
        SystemMapHierarchyBuilder.NodeKind kind = toKind(b.kind());
        String label = shortLabel(b.bodyName());
        String subtitle = b.subType() != null && !b.subType().isBlank() ? b.subType() : b.bodyType();
        if (b.orbitParent() != null) {
            subtitle = subtitle + " · orbit: " + b.orbitParent().format();
        }
        return new SystemMapHierarchyBuilder.Node(
                b.bodyId(), label, subtitle, formatParentsLine(b.journalParents()), kind);
    }

    private static SystemMapHierarchyBuilder.Node baryNode(SystemModel model, BarycentreNode bc) {
        int journalNullId = bc.bodyId();
        int mapKey = HierarchyKeys.baryMapKey(journalNullId);
        SystemMapHierarchyBuilder.NodeKind kind = BarycentreHubPolicy.isPlanetBinaryHub(model, journalNullId)
                ? SystemMapHierarchyBuilder.NodeKind.PLANET_BINARY_BARYCENTRE
                : SystemMapHierarchyBuilder.NodeKind.SCAN_BARYCENTRE;
        String subtitle = "ScanBaryCentre";
        if (kind == SystemMapHierarchyBuilder.NodeKind.PLANET_BINARY_BARYCENTRE) {
            String moons = BarycentreHubPolicy.collapsedMoonSummary(model, journalNullId);
            if (!moons.isEmpty()) {
                subtitle = "binary moons · " + moons;
            }
        }
        return new SystemMapHierarchyBuilder.Node(
                mapKey,
                "Null:" + journalNullId,
                subtitle,
                formatParentsLine(bc.journalParents()),
                kind);
    }

    private static SystemMapHierarchyBuilder.NodeKind toKind(BodyKind kind) {
        return switch (kind) {
            case STAR -> SystemMapHierarchyBuilder.NodeKind.STAR;
            case MOON -> SystemMapHierarchyBuilder.NodeKind.MOON;
            case PLANET -> SystemMapHierarchyBuilder.NodeKind.PLANET;
            case BARYCENTRE -> SystemMapHierarchyBuilder.NodeKind.SCAN_BARYCENTRE;
            default -> SystemMapHierarchyBuilder.NodeKind.OTHER;
        };
    }

    private static String shortLabel(String bodyName) {
        if (bodyName == null || bodyName.isBlank()) {
            return "?";
        }
        int sp = bodyName.lastIndexOf(' ');
        return sp >= 0 ? bodyName.substring(sp + 1).trim() : bodyName.trim();
    }

    private static String formatParentsLine(List<ParentRef> parents) {
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (ParentRef p : parents) {
            parts.add(p.format());
        }
        return String.join(" → ", parts);
    }
}
