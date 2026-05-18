package org.dce.ed.systemmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Builds a top-down parent/child graph from {@link SystemMapModel} resolved parents (same topology as
 * {@link SystemMapTreePrinter}).
 */
public final class SystemMapHierarchyBuilder {

    public enum NodeKind {
        SYSTEM_BARYCENTRE,
        SCAN_BARYCENTRE,
        PLANET_BINARY_BARYCENTRE,
        STAR,
        PLANET,
        MOON,
        OTHER
    }

    public static final class Node {
        public final int mapKey;
        public final String label;
        public final String subtitle;
        public final NodeKind kind;
        public final List<Node> children = new ArrayList<>();
        public int parentKey = Integer.MIN_VALUE;
        public double layoutX;
        public double layoutY;

        Node(int mapKey, String label, String subtitle, NodeKind kind) {
            this.mapKey = mapKey;
            this.label = label;
            this.subtitle = subtitle;
            this.kind = kind;
        }
    }

    public static final class Edge {
        public final int parentKey;
        public final int childKey;

        Edge(int parentKey, int childKey) {
            this.parentKey = parentKey;
            this.childKey = childKey;
        }
    }

    public static final class Graph {
        public final String systemName;
        public final Node root;
        public final List<Edge> edges;
        public final Map<Integer, Node> nodeByKey = new HashMap<>();

        Graph(String systemName, Node root, List<Edge> edges) {
            this.systemName = systemName;
            this.root = root;
            this.edges = edges;
        }
    }

    private static final int ROOT_KEY = -1;
    private static final double H_SPACING = 140.0;
    private static final double V_SPACING = 88.0;

    private SystemMapHierarchyBuilder() {
    }

    public static Graph build(String systemName, SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, List<Integer>> childKeys = buildChildKeys(model, bodies);
        Node root = new Node(ROOT_KEY, "Null:0", "system barycentre", NodeKind.SYSTEM_BARYCENTRE);
        Map<Integer, Node> built = new HashMap<>();
        built.put(Integer.valueOf(ROOT_KEY), root);
        buildSubtree(root, ROOT_KEY, childKeys, bodies, model, built);
        List<Edge> edges = new ArrayList<>();
        collectEdges(root, edges);
        layout(root, 0, 0.0);
        Graph graph = new Graph(systemName, root, edges);
        graph.nodeByKey.putAll(built);
        return graph;
    }

    private static Map<Integer, List<Integer>> buildChildKeys(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (e.getValue().isScanBarycentreRow()) {
                children.computeIfAbsent(Integer.valueOf(ROOT_KEY), k -> new ArrayList<>()).add(Integer.valueOf(id));
                continue;
            }
            int p = model.resolveParentBodyId(id);
            children.computeIfAbsent(Integer.valueOf(p), k -> new ArrayList<>()).add(Integer.valueOf(id));
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p)) {
                children.computeIfAbsent(Integer.valueOf(ROOT_KEY), k -> new ArrayList<>()).add(Integer.valueOf(p));
            }
        }
        for (Integer p : new ArrayList<>(children.keySet())) {
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p.intValue())) {
                children.computeIfAbsent(Integer.valueOf(ROOT_KEY), k -> new ArrayList<>()).add(p);
            }
        }
        for (List<Integer> list : children.values()) {
            list.sort(bodySiblingComparator(bodies));
        }
        return children;
    }

    private static void buildSubtree(Node parentNode, int parentKey, Map<Integer, List<Integer>> childKeys,
            Map<Integer, BodyInfo> bodies, SystemMapModel model, Map<Integer, Node> built) {
        List<Integer> kids = childKeys.get(Integer.valueOf(parentKey));
        if (kids == null) {
            return;
        }
        for (Integer kidKey : kids) {
            int id = kidKey.intValue();
            if (built.containsKey(Integer.valueOf(id))) {
                continue;
            }
            Node node = nodeForKey(id, bodies, model);
            if (node == null) {
                continue;
            }
            node.parentKey = parentKey;
            built.put(Integer.valueOf(id), node);
            parentNode.children.add(node);
            buildSubtree(node, id, childKeys, bodies, model, built);
        }
    }

    private static Node nodeForKey(int id, Map<Integer, BodyInfo> bodies, SystemMapModel model) {
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(id)) {
            int nullId = SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(id);
            return new Node(id, "Null:" + nullId, "planet-binary barycentre", NodeKind.PLANET_BINARY_BARYCENTRE);
        }
        BodyInfo b = bodies.get(Integer.valueOf(id));
        if (b == null) {
            return null;
        }
        if (b.isScanBarycentreRow()) {
            return new Node(id, "Null:" + id, "ScanBaryCentre", NodeKind.SCAN_BARYCENTRE);
        }
        String label = b.getShortName() != null ? b.getShortName() : ("id " + id);
        String subtitle = subtitleFor(b, model, bodies, id);
        return new Node(id, label, subtitle, kindFor(b, bodies, id));
    }

    private static String subtitleFor(BodyInfo b, SystemMapModel model, Map<Integer, BodyInfo> bodies, int id) {
        if (b.getStarType() != null && !b.getStarType().isEmpty()) {
            return "★ " + b.getStarType();
        }
        String resolved = SystemMapTreePrinter.formatResolvedParent(model, bodies, id,
                SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies));
        if (b.getPlanetClass() != null && !b.getPlanetClass().isEmpty()) {
            return b.getPlanetClass() + " → " + resolved;
        }
        return "→ " + resolved;
    }

    private static NodeKind kindFor(BodyInfo b, Map<Integer, BodyInfo> bodies, int id) {
        if (b.getStarType() != null && !b.getStarType().isEmpty()) {
            return NodeKind.STAR;
        }
        if (SystemOrbitGeometry.isMoonSatelliteBody(b, bodies)) {
            return NodeKind.MOON;
        }
        if (SystemMapRules.isMapStellarBody(b)) {
            return NodeKind.PLANET;
        }
        return NodeKind.OTHER;
    }

    private static void collectEdges(Node node, List<Edge> edges) {
        for (Node child : node.children) {
            edges.add(new Edge(node.mapKey, child.mapKey));
            collectEdges(child, edges);
        }
    }

    private static double layout(Node node, int depth, double nextX) {
        node.layoutY = depth * V_SPACING;
        if (node.children.isEmpty()) {
            node.layoutX = nextX;
            return nextX + H_SPACING;
        }
        double cursor = nextX;
        for (Node child : node.children) {
            cursor = layout(child, depth + 1, cursor);
        }
        node.layoutX = (node.children.get(0).layoutX + node.children.get(node.children.size() - 1).layoutX) / 2.0;
        return cursor;
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
}
