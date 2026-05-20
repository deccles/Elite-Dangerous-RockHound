package org.dce.ed.systemmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dce.ed.state.BodyInfo;
import org.dce.ed.state.JournalParentRefs;
import org.dce.ed.util.SystemOrbitGeometry;

/**
 * Builds a top-down parent/child graph from {@link SystemMapModel} resolved parents (same topology as
 * {@link SystemMapTreePrinter}).
 * <p>
 * Each node shows three independent facts (no guessing which line means what):
 * <ul>
 *   <li><b>Label</b> — body short name (or {@code Null:N} for barycentre rows / planet-binary hubs).</li>
 *   <li><b>Subtitle</b> — physical type plus <em>map</em> orbit parent ({@link JournalParentRefs#formatMapParentLabel});
 *       adds {@code journal: …} when journal innermost parent disagrees with map.</li>
 *   <li><b>Parents line</b> — full journal {@code Scan.Parents[]} chain inner→outer
 *       ({@link JournalParentRefs#formatParentsLineForMapBody} / hub variant); tree edges still follow map topology.</li>
 * </ul>
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
        /** Journal {@code Parents[]} with ids resolved to names when known; may be null. */
        public final String parentsLine;
        public final NodeKind kind;
        public final List<Node> children = new ArrayList<>();
        public int parentKey = Integer.MIN_VALUE;
        public double layoutX;
        public double layoutY;
        /** Measured box width for layout (pixels); set by {@link #applyLayout}. */
        public int layoutW;
        /** Measured box height for layout (pixels); set by {@link #applyLayout}. */
        public int layoutH;

        Node(int mapKey, String label, String subtitle, String parentsLine, NodeKind kind) {
            this.mapKey = mapKey;
            this.label = label;
            this.subtitle = subtitle;
            this.parentsLine = parentsLine;
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
    private static final double V_SPACING = 100.0;
    private static final int DEFAULT_SIBLING_GAP = 28;
    private static final int ESTIMATE_MIN_NODE_W = 84;
    private static final int ESTIMATE_MIN_NODE_H = 44;
    private static final int ESTIMATE_EXTRA_LINE_PX = 14;

    private SystemMapHierarchyBuilder() {
    }

    public static Graph build(String systemName, SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, List<Integer>> childKeys = buildChildKeys(model, bodies);
        Node root = new Node(ROOT_KEY, "Null:0", "system barycentre", null, NodeKind.SYSTEM_BARYCENTRE);
        Map<Integer, Node> built = new HashMap<>();
        built.put(Integer.valueOf(ROOT_KEY), root);
        buildSubtree(root, ROOT_KEY, childKeys, bodies, model, built);
        List<Edge> edges = new ArrayList<>();
        collectEdges(root, edges);
        applyLayoutEstimate(root);
        Graph graph = new Graph(systemName, root, edges);
        graph.nodeByKey.putAll(built);
        return graph;
    }

    private static Map<Integer, List<Integer>> buildChildKeys(SystemMapModel model, Map<Integer, BodyInfo> bodies) {
        Map<Integer, List<Integer>> children = new HashMap<>();
        Set<Integer> planetBinaryNullIds = new HashSet<>();
        for (Map.Entry<Integer, BodyInfo> e : bodies.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            int id = e.getKey().intValue();
            if (e.getValue().isScanBarycentreRow()) {
                if (SystemOrbitGeometry.isPlanetBinaryNullParentId(id, bodies)) {
                    planetBinaryNullIds.add(Integer.valueOf(id));
                    continue;
                }
                children.computeIfAbsent(Integer.valueOf(ROOT_KEY), k -> new ArrayList<>()).add(Integer.valueOf(id));
                continue;
            }
            int p = hierarchyParentKey(id, e.getValue(), model, bodies);
            children.computeIfAbsent(Integer.valueOf(p), k -> new ArrayList<>()).add(Integer.valueOf(id));
            int ip = e.getValue().getImmediateParentBodyId();
            if (ip > 0 && SystemOrbitGeometry.isPlanetBinaryNullParentId(ip, bodies)) {
                planetBinaryNullIds.add(Integer.valueOf(ip));
            }
        }
        for (Integer nullId : planetBinaryNullIds) {
            int hubKey = SystemOrbitGeometry.planetBinaryBarycentreMapKey(nullId.intValue());
            attachPlanetBinaryHub(children, hubKey, bodies);
        }
        for (Integer p : new ArrayList<>(children.keySet())) {
            if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(p.intValue())) {
                attachPlanetBinaryHub(children, p.intValue(), bodies);
            }
        }
        for (List<Integer> list : children.values()) {
            list.sort(bodySiblingComparator(bodies));
        }
        return children;
    }

    private static void attachPlanetBinaryHub(Map<Integer, List<Integer>> children, int hubKey,
            Map<Integer, BodyInfo> bodies) {
        int hubParent = SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(hubKey, bodies);
        if (hubParent < 0) {
            hubParent = ROOT_KEY;
        }
        List<Integer> list = children.computeIfAbsent(Integer.valueOf(hubParent), k -> new ArrayList<>());
        Integer boxed = Integer.valueOf(hubKey);
        if (!list.contains(boxed)) {
            list.add(boxed);
        }
    }

    /** Journal tree edges: co-orbit majors stay under {@code Null:N} hub; map orbit parent may differ. */
    static int hierarchyParentKey(int bodyId, BodyInfo body, SystemMapModel model,
            Map<Integer, BodyInfo> bodies) {
        int ip = body.getImmediateParentBodyId();
        if (ip > 0 && SystemOrbitGeometry.isPlanetBinaryNullParentId(ip, bodies)
                && !SystemOrbitGeometry.isMoonSatelliteBody(body, bodies)) {
            return SystemOrbitGeometry.planetBinaryBarycentreMapKey(ip);
        }
        return model.resolveParentBodyId(bodyId);
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
            String parentsLine = JournalParentRefs.formatPlanetBinaryHubParentsLine(nullId, bodies, model);
            return new Node(id, "Null:" + nullId, "planet-binary barycentre", parentsLine,
                    NodeKind.PLANET_BINARY_BARYCENTRE);
        }
        BodyInfo b = bodies.get(Integer.valueOf(id));
        if (b == null) {
            return null;
        }
        if (b.isScanBarycentreRow()) {
            String parentsLine = parentsLineForBody(b, id, bodies, model);
            return new Node(id, "Null:" + id, "ScanBaryCentre", parentsLine, NodeKind.SCAN_BARYCENTRE);
        }
        String label = b.getShortName() != null ? b.getShortName() : ("id " + id);
        String subtitle = subtitleFor(b, model, bodies, id);
        String parentsLine = parentsLineForBody(b, id, bodies, model);
        return new Node(id, label, subtitle, parentsLine, kindFor(b, bodies, id));
    }

    private static String parentsLineForBody(BodyInfo b, int mapBodyId, Map<Integer, BodyInfo> bodies,
            SystemMapModel model) {
        return JournalParentRefs.formatParentsLineForMapBody(b, mapBodyId, bodies, model);
    }

    private static String subtitleFor(BodyInfo b, SystemMapModel model, Map<Integer, BodyInfo> bodies, int id) {
        if (SystemMapRules.isMapStellarBody(b)) {
            String st = b.getStarType();
            if (st != null && !st.isEmpty()) {
                return "★ " + st;
            }
        }
        int arrivalStar = SystemOrbitGeometry.primaryAnchorBodyMapKey(bodies);
        String mapParent = JournalParentRefs.formatMapParentLabel(model, bodies, id, arrivalStar);
        StringBuilder sb = new StringBuilder();
        if (b.getPlanetClass() != null && !b.getPlanetClass().isEmpty()) {
            sb.append(b.getPlanetClass());
        }
        if (JournalParentRefs.journalInnermostDiffersFromMap(b, bodies, id, model, arrivalStar)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("journal: ").append(JournalParentRefs.formatInnermostJournalParent(b, bodies));
            sb.append(" · map: ").append(mapParent);
        } else {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("map: ").append(mapParent);
        }
        return sb.toString();
    }

    private static NodeKind kindFor(BodyInfo b, Map<Integer, BodyInfo> bodies, int id) {
        if (SystemMapRules.isMapStellarBody(b)) {
            return NodeKind.STAR;
        }
        if (SystemOrbitGeometry.isMoonSatelliteBody(b, bodies)) {
            return NodeKind.MOON;
        }
        if (b.getPlanetClass() != null && !b.getPlanetClass().isEmpty()) {
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

    /**
     * Width-aware top-down layout using real font metrics (call from {@link org.dce.ed.ui.SystemHierarchyGraphPanel}).
     */
    public static void applyLayout(Graph graph, java.awt.FontMetrics fm, int padX, int minW, int minH, int siblingGap) {
        if (graph == null || graph.root == null || fm == null) {
            return;
        }
        measureTree(graph.root, fm, padX, minW, minH);
        layoutSubtree(graph.root, 0, 0.0, siblingGap);
        separateSiblingSubtrees(graph.root, siblingGap);
        recenterParents(graph.root);
    }

    /** Rough layout when no font metrics are available (tests, first build). */
    private static void applyLayoutEstimate(Node root) {
        measureTreeEstimate(root);
        layoutSubtree(root, 0, 0.0, DEFAULT_SIBLING_GAP);
    }

    private static void measureTree(Node node, java.awt.FontMetrics fm, int padX, int minW, int minH) {
        int labelW = fm.stringWidth(node.label);
        int subW = node.subtitle != null && !node.subtitle.isEmpty() ? fm.stringWidth(node.subtitle) : 0;
        int parW = node.parentsLine != null && !node.parentsLine.isEmpty() ? fm.stringWidth(node.parentsLine) : 0;
        node.layoutW = Math.max(minW, Math.max(labelW, Math.max(subW, parW)) + 2 * padX);
        int extraLines = 0;
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            extraLines++;
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            extraLines++;
        }
        int lineStep = fm.getHeight() + 2;
        node.layoutH = minH + extraLines * lineStep;
        for (Node child : node.children) {
            measureTree(child, fm, padX, minW, minH);
        }
    }

    private static void measureTreeEstimate(Node node) {
        int labelChars = node.label != null ? node.label.length() : 0;
        int subChars = node.subtitle != null ? node.subtitle.length() : 0;
        int parChars = node.parentsLine != null ? node.parentsLine.length() : 0;
        node.layoutW = Math.max(ESTIMATE_MIN_NODE_W,
                Math.max(labelChars, Math.max(subChars, parChars)) * 8 + 24);
        int extraLines = 0;
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            extraLines++;
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            extraLines++;
        }
        node.layoutH = extraLines > 0
                ? ESTIMATE_MIN_NODE_H + extraLines * ESTIMATE_EXTRA_LINE_PX
                : ESTIMATE_MIN_NODE_H;
        for (Node child : node.children) {
            measureTreeEstimate(child);
        }
    }

    private static double layoutSubtree(Node node, int depth, double left, int siblingGap) {
        node.layoutY = depth * V_SPACING;
        if (node.children.isEmpty()) {
            node.layoutX = left + node.layoutW / 2.0;
            return left + node.layoutW;
        }
        double cursor = left;
        for (int i = 0; i < node.children.size(); i++) {
            if (i > 0) {
                cursor += siblingGap;
            }
            cursor = layoutSubtree(node.children.get(i), depth + 1, cursor, siblingGap);
        }
        Node first = node.children.get(0);
        Node last = node.children.get(node.children.size() - 1);
        double spanLeft = first.layoutX - first.layoutW / 2.0;
        double spanRight = last.layoutX + last.layoutW / 2.0;
        node.layoutX = (spanLeft + spanRight) / 2.0;
        double ownRight = node.layoutX + node.layoutW / 2.0;
        return Math.max(cursor, ownRight);
    }

    /** Push later siblings right so measured box widths never overlap (e.g. planet + moon beside a peer). */
    private static void separateSiblingSubtrees(Node parent, int siblingGap) {
        if (parent == null || parent.children.size() < 2) {
            if (parent != null) {
                for (Node child : parent.children) {
                    separateSiblingSubtrees(child, siblingGap);
                }
            }
            return;
        }
        for (int i = 1; i < parent.children.size(); i++) {
            Node prev = parent.children.get(i - 1);
            Node cur = parent.children.get(i);
            double prevRight = subtreeRightEdge(prev);
            double curLeft = subtreeLeftEdge(cur);
            double shift = prevRight + siblingGap - curLeft;
            if (shift > 0.0) {
                shiftSubtreeX(cur, shift);
            }
        }
        for (Node child : parent.children) {
            separateSiblingSubtrees(child, siblingGap);
        }
    }

    private static void recenterParents(Node node) {
        if (node == null || node.children.isEmpty()) {
            return;
        }
        for (Node child : node.children) {
            recenterParents(child);
        }
        Node first = node.children.get(0);
        Node last = node.children.get(node.children.size() - 1);
        double spanLeft = subtreeLeftEdge(first);
        double spanRight = subtreeRightEdge(last);
        node.layoutX = (spanLeft + spanRight) / 2.0;
    }

    private static double subtreeLeftEdge(Node node) {
        double left = node.layoutX - node.layoutW / 2.0;
        for (Node child : node.children) {
            left = Math.min(left, subtreeLeftEdge(child));
        }
        return left;
    }

    private static double subtreeRightEdge(Node node) {
        double right = node.layoutX + node.layoutW / 2.0;
        for (Node child : node.children) {
            right = Math.max(right, subtreeRightEdge(child));
        }
        return right;
    }

    private static void shiftSubtreeX(Node node, double dx) {
        node.layoutX += dx;
        for (Node child : node.children) {
            shiftSubtreeX(child, dx);
        }
    }

    /** Left-to-right sibling order in the graph matches {@link #siblingSortLabel} (case-insensitive A–Z). */
    private static Comparator<Integer> bodySiblingComparator(Map<Integer, BodyInfo> bodies) {
        return (a, b) -> siblingSortLabel(a, bodies).compareToIgnoreCase(siblingSortLabel(b, bodies));
    }

    static String siblingSortLabel(Integer mapKey, Map<Integer, BodyInfo> bodies) {
        if (mapKey == null) {
            return "";
        }
        int id = mapKey.intValue();
        if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(id)) {
            return "Null:" + SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(id);
        }
        BodyInfo b = bodies != null ? bodies.get(mapKey) : null;
        if (b != null && b.isScanBarycentreRow()) {
            return "Null:" + id;
        }
        if (b != null && b.getShortName() != null && !b.getShortName().isEmpty()) {
            return b.getShortName();
        }
        return "id " + id;
    }
}
