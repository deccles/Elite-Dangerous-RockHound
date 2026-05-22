package org.dce.ed.systemmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        OTHER,
        /** Synthetic child shown when a parent is collapsed (e.g. {@code BCD 5 a-g}). */
        COLLAPSED_PLACEHOLDER
    }

    /** Map keys for collapse-summary placeholder nodes (distinct from body and Null hub keys). */
    public static final int COLLAPSED_PLACEHOLDER_MAP_KEY_BASE = -60_000;

    public static final class Node {
        public final int mapKey;
        public final String label;
        public final String subtitle;
        /** Journal {@code Parents[]} with ids resolved to names when known; may be null. */
        public final String parentsLine;
        public final NodeKind kind;
        public final List<Node> children = new ArrayList<>();
        /** Stable collapse-summary nodes; synced in {@link #syncCollapsePlaceholders}. */
        public final List<Node> collapsePlaceholders = new ArrayList<>();
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
                if (SystemOrbitGeometry.isPlanetBinaryNullParentId(id, bodies)
                        || SystemOrbitGeometry.isSharedNullBarycentreId(id, bodies)) {
                    planetBinaryNullIds.add(Integer.valueOf(id));
                    continue;
                }
                children.computeIfAbsent(Integer.valueOf(ROOT_KEY), k -> new ArrayList<>()).add(Integer.valueOf(id));
                continue;
            }
            int p = hierarchyParentKey(id, e.getValue(), model, bodies);
            children.computeIfAbsent(Integer.valueOf(p), k -> new ArrayList<>()).add(Integer.valueOf(id));
            int ip = e.getValue().getImmediateParentBodyId();
            if (ip > 0 && (SystemOrbitGeometry.isPlanetBinaryNullParentId(ip, bodies)
                    || SystemOrbitGeometry.isSharedNullBarycentreId(ip, bodies))) {
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
        finalizePlanetBinaryHubParents(children, bodies);
        for (List<Integer> list : children.values()) {
            list.sort(bodySiblingComparator(bodies));
        }
        return children;
    }

    /** Re-seat hubs after all attach passes (map iteration order can leave inner Null hubs on {@code Null:0}). */
    private static void finalizePlanetBinaryHubParents(Map<Integer, List<Integer>> children,
            Map<Integer, BodyInfo> bodies) {
        List<Integer> hubKeys = new ArrayList<>();
        for (Integer parentKey : children.keySet()) {
            List<Integer> kids = children.get(parentKey);
            if (kids == null) {
                continue;
            }
            for (Integer kid : kids) {
                if (SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(kid.intValue())) {
                    hubKeys.add(kid);
                }
            }
        }
        for (Integer hubKey : hubKeys) {
            attachPlanetBinaryHub(children, hubKey.intValue(), bodies);
        }
    }

    private static void attachPlanetBinaryHub(Map<Integer, List<Integer>> children, int hubKey,
            Map<Integer, BodyInfo> bodies) {
        int hubParent = SystemOrbitGeometry.planetBinaryBarycentreHierarchyParentMapKey(hubKey, bodies);
        if (hubParent < 0 && !SystemOrbitGeometry.isPlanetBinaryBarycentreMapKey(hubParent)) {
            hubParent = ROOT_KEY;
        }
        Integer boxed = Integer.valueOf(hubKey);
        Integer scanRowKey = Integer.valueOf(
                SystemOrbitGeometry.journalNullIdFromPlanetBinaryBarycentreMapKey(hubKey));
        for (List<Integer> list : children.values()) {
            list.remove(boxed);
            list.remove(scanRowKey);
        }
        List<Integer> list = children.computeIfAbsent(Integer.valueOf(hubParent), k -> new ArrayList<>());
        if (!list.contains(boxed)) {
            list.add(boxed);
        }
    }

    /** Journal tree edges: co-orbit majors under {@code Null:N} hub unless map resolution keeps them on branch star A. */
    static int hierarchyParentKey(int bodyId, BodyInfo body, SystemMapModel model,
            Map<Integer, BodyInfo> bodies) {
        return SystemOrbitGeometry.hierarchyTreeParentKey(body, bodyId, model.resolveParentBodyId(bodyId), bodies);
    }

    private static void buildSubtree(Node parentNode, int parentKey, Map<Integer, List<Integer>> childKeys,
            Map<Integer, BodyInfo> bodies, SystemMapModel model, Map<Integer, Node> built) {
        List<Integer> kids = childKeys.get(Integer.valueOf(parentKey));
        if (kids == null) {
            return;
        }
        for (Integer kidKey : kids) {
            int id = kidKey.intValue();
            Node existing = built.get(Integer.valueOf(id));
            if (existing != null) {
                if (existing.parentKey != parentKey) {
                    SystemMapHierarchyBuilder.Node oldParent = built.get(Integer.valueOf(existing.parentKey));
                    if (oldParent != null) {
                        oldParent.children.remove(existing);
                    }
                    existing.parentKey = parentKey;
                    parentNode.children.add(existing);
                }
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
            if (SystemOrbitGeometry.isPlanetBinaryNullParentId(id, bodies)
                    || SystemOrbitGeometry.isSharedNullBarycentreId(id, bodies)) {
                return nodeForKey(SystemOrbitGeometry.planetBinaryBarycentreMapKey(id), bodies, model);
            }
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
        applyLayout(graph, fm, padX, minW, minH, siblingGap, null);
    }

    /**
     * Layout with optional collapsed nodes (real children hidden; {@link #collapsedSummaryLabels} appear as child
     * placeholder nodes).
     */
    public static void applyLayout(Graph graph, java.awt.FontMetrics fm, int padX, int minW, int minH, int siblingGap,
            Set<Integer> collapsedKeys) {
        if (graph == null || graph.root == null || fm == null) {
            return;
        }
        syncCollapsePlaceholders(graph, collapsedKeys);
        measureTree(graph.root, fm, padX, minW, minH, collapsedKeys);
        layoutSubtree(graph.root, 0.0, 0.0, siblingGap, collapsedKeys);
        separateSiblingSubtrees(graph.root, siblingGap, collapsedKeys);
        recenterParents(graph.root, collapsedKeys);
    }

    public static int collapsedPlaceholderMapKey(int parentMapKey, int segmentIndex) {
        int mix = parentMapKey ^ (parentMapKey >>> 16);
        return COLLAPSED_PLACEHOLDER_MAP_KEY_BASE - (mix & 0x7FFF) * 16 - segmentIndex;
    }

    public static boolean isCollapsedPlaceholderMapKey(int mapKey) {
        return mapKey <= COLLAPSED_PLACEHOLDER_MAP_KEY_BASE
                && mapKey > COLLAPSED_PLACEHOLDER_MAP_KEY_BASE - 0x100000;
    }

    /**
     * Labels for placeholder child nodes when a parent is collapsed (one per shared map-parent group).
     */
    public static List<String> collapsedSummaryLabels(Node node) {
        if (node == null || node.children.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> byMapParent = new LinkedHashMap<>();
        for (Node child : node.children) {
            String mapParent = mapParentFromSubtitle(child.subtitle);
            byMapParent.computeIfAbsent(mapParent, k -> new ArrayList<>()).add(child.label);
        }
        List<String> segments = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byMapParent.entrySet()) {
            String segment = formatLabelsUnderSharedParent(e.getKey(), e.getValue());
            if (segment != null && !segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * Short label for direct children hidden by collapse (e.g. {@code A 1-6} when map parent and numeric suffixes align).
     */
    public static String summarizeCollapsedChildren(Node node) {
        return String.join(", ", collapsedSummaryLabels(node));
    }

    /** Placeholder nodes drawn under a collapsed parent instead of inlining summary text in the parent box. */
    public static List<Node> collapsedPlaceholderChildren(Node parent) {
        List<String> labels = collapsedSummaryLabels(parent);
        if (labels.isEmpty()) {
            return List.of();
        }
        List<Node> placeholders = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            int key = collapsedPlaceholderMapKey(parent.mapKey, i);
            Node ph = new Node(key, labels.get(i), null, null, NodeKind.COLLAPSED_PLACEHOLDER);
            ph.parentKey = parent.mapKey;
            placeholders.add(ph);
        }
        return placeholders;
    }

    /**
     * Refreshes stable placeholder nodes on collapsed parents and registers them in {@link Graph#nodeByKey}
     * so layout coordinates persist across measure/layout/paint passes.
     */
    public static void syncCollapsePlaceholders(Graph graph, Set<Integer> collapsedKeys) {
        if (graph == null) {
            return;
        }
        List<Integer> removeKeys = new ArrayList<>();
        for (Integer key : graph.nodeByKey.keySet()) {
            if (isCollapsedPlaceholderMapKey(key.intValue())) {
                removeKeys.add(key);
            }
        }
        for (Integer key : removeKeys) {
            graph.nodeByKey.remove(key);
        }
        List<Node> graphNodes = new ArrayList<>();
        for (Node node : graph.nodeByKey.values()) {
            if (node.kind != NodeKind.COLLAPSED_PLACEHOLDER) {
                node.collapsePlaceholders.clear();
                graphNodes.add(node);
            }
        }
        if (collapsedKeys == null || collapsedKeys.isEmpty()) {
            return;
        }
        for (Node node : graphNodes) {
            if (!isCollapsed(node, collapsedKeys) || node.children.isEmpty()) {
                continue;
            }
            List<String> labels = collapsedSummaryLabels(node);
            for (int i = 0; i < labels.size(); i++) {
                int key = collapsedPlaceholderMapKey(node.mapKey, i);
                Node ph = new Node(key, labels.get(i), null, null, NodeKind.COLLAPSED_PLACEHOLDER);
                ph.parentKey = node.mapKey;
                node.collapsePlaceholders.add(ph);
                graph.nodeByKey.put(Integer.valueOf(key), ph);
            }
        }
    }

    /** Children used for layout and painting; collapsed parents show synced {@link Node#collapsePlaceholders} only. */
    public static List<Node> visibleChildren(Node node, Set<Integer> collapsedKeys) {
        if (isCollapsed(node, collapsedKeys)) {
            return node.collapsePlaceholders;
        }
        return node.children;
    }

    static String mapParentFromSubtitle(String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) {
            return "";
        }
        String marker = "map: ";
        int idx = subtitle.lastIndexOf(marker);
        if (idx < 0) {
            return "";
        }
        String tail = subtitle.substring(idx + marker.length()).trim();
        int dot = tail.indexOf(" · ");
        if (dot >= 0) {
            tail = tail.substring(0, dot).trim();
        }
        return tail;
    }

    private static String formatLabelsUnderSharedParent(String mapParent, List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        List<String> sorted = new ArrayList<>(labels);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        if (!mapParent.isEmpty() && sorted.stream().allMatch(l -> l.equals(mapParent) || l.startsWith(mapParent + " "))) {
            List<String> suffixes = new ArrayList<>();
            for (String label : sorted) {
                suffixes.add(label.equals(mapParent) ? "" : label.substring(mapParent.length() + 1));
            }
            String ranged = formatDesignationRange(mapParent, suffixes);
            if (ranged != null) {
                return ranged;
            }
        }
        String prefix = longestCommonPrefix(sorted);
        if (prefix.length() > 0 && prefix.charAt(prefix.length() - 1) == ' ') {
            List<String> suffixes = new ArrayList<>();
            for (String label : sorted) {
                suffixes.add(label.startsWith(prefix) ? label.substring(prefix.length()) : label);
            }
            String head = prefix.substring(0, prefix.length() - 1);
            String ranged = formatDesignationRange(head, suffixes);
            if (ranged != null) {
                return ranged;
            }
        }
        return String.join(", ", sorted);
    }

    private static String formatDesignationRange(String head, List<String> suffixes) {
        if (suffixes.isEmpty()) {
            return head;
        }
        List<Integer> nums = new ArrayList<>();
        boolean allNumeric = true;
        for (String suffix : suffixes) {
            if (suffix.isEmpty()) {
                continue;
            }
            try {
                nums.add(Integer.valueOf(suffix.trim()));
            } catch (NumberFormatException e) {
                allNumeric = false;
                break;
            }
        }
        if (allNumeric && nums.size() == suffixes.size()) {
            nums.sort(Integer::compareTo);
            if (isConsecutiveIntegers(nums)) {
                String prefix = head.isEmpty() ? "" : head + " ";
                if (nums.size() == 1) {
                    return prefix + nums.get(0);
                }
                return prefix + nums.get(0) + "-" + nums.get(nums.size() - 1);
            }
        }
        List<Character> letters = new ArrayList<>();
        boolean allLetters = true;
        for (String suffix : suffixes) {
            if (suffix.length() != 1 || !Character.isLetter(suffix.charAt(0))) {
                allLetters = false;
                break;
            }
            letters.add(Character.valueOf(Character.toLowerCase(suffix.charAt(0))));
        }
        if (allLetters && letters.size() == suffixes.size()) {
            letters.sort(Character::compareTo);
            if (isConsecutiveLetters(letters)) {
                String prefix = head.isEmpty() ? "" : head + " ";
                char first = letters.get(0).charValue();
                char last = letters.get(letters.size() - 1).charValue();
                if (letters.size() == 1) {
                    return prefix + first;
                }
                return prefix + first + "-" + last;
            }
        }
        return null;
    }

    private static boolean isConsecutiveIntegers(List<Integer> sorted) {
        if (sorted.size() < 2) {
            return true;
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).intValue() != sorted.get(i - 1).intValue() + 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isConsecutiveLetters(List<Character> sorted) {
        if (sorted.size() < 2) {
            return true;
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).charValue() != sorted.get(i - 1).charValue() + 1) {
                return false;
            }
        }
        return true;
    }

    private static String longestCommonPrefix(List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        String prefix = labels.get(0);
        for (int i = 1; i < labels.size(); i++) {
            String label = labels.get(i);
            int max = Math.min(prefix.length(), label.length());
            int j = 0;
            while (j < max && prefix.charAt(j) == label.charAt(j)) {
                j++;
            }
            prefix = prefix.substring(0, j);
            if (prefix.isEmpty()) {
                return "";
            }
        }
        return prefix;
    }

    public static boolean isCollapsed(Node node, Set<Integer> collapsedKeys) {
        return collapsedKeys != null && collapsedKeys.contains(Integer.valueOf(node.mapKey));
    }

    /** Rough layout when no font metrics are available (tests, first build). */
    private static void applyLayoutEstimate(Node root) {
        measureTreeEstimate(root);
        layoutSubtree(root, 0.0, 0.0, DEFAULT_SIBLING_GAP);
    }

    private static void measureTree(Node node, java.awt.FontMetrics fm, int padX, int minW, int minH) {
        measureTree(node, fm, padX, minW, minH, null);
    }

    private static void measureTree(Node node, java.awt.FontMetrics fm, int padX, int minW, int minH,
            Set<Integer> collapsedKeys) {
        int labelW = fm.stringWidth(node.label);
        int subW = node.subtitle != null && !node.subtitle.isEmpty() ? fm.stringWidth(node.subtitle) : 0;
        int parW = node.parentsLine != null && !node.parentsLine.isEmpty() ? fm.stringWidth(node.parentsLine) : 0;
        node.layoutW = Math.max(minW, Math.max(labelW, Math.max(subW, parW))) + 2 * padX;
        int extraLines = 0;
        if (node.subtitle != null && !node.subtitle.isEmpty()) {
            extraLines++;
        }
        if (node.parentsLine != null && !node.parentsLine.isEmpty()) {
            extraLines++;
        }
        int lineStep = fm.getHeight() + 2;
        node.layoutH = minH + extraLines * lineStep;
        for (Node child : visibleChildren(node, collapsedKeys)) {
            measureTree(child, fm, padX, minW, minH, collapsedKeys);
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

    private static double layoutSubtree(Node node, double topY, double left, int siblingGap) {
        return layoutSubtree(node, topY, left, siblingGap, null);
    }

    /**
     * Top-down layout: each row's {@code topY} stacks below the parent box plus a vertical gap about one parent
     * box tall so connector lines stay visible between rows.
     */
    private static double layoutSubtree(Node node, double topY, double left, int siblingGap,
            Set<Integer> collapsedKeys) {
        node.layoutY = topY + node.layoutH / 2.0;
        List<Node> children = visibleChildren(node, collapsedKeys);
        if (children.isEmpty()) {
            node.layoutX = left + node.layoutW / 2.0;
            return left + node.layoutW;
        }
        double childRowTop = topY + node.layoutH + node.layoutH;
        double cursor = left;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                cursor += siblingGap;
            }
            cursor = layoutSubtree(children.get(i), childRowTop, cursor, siblingGap, collapsedKeys);
        }
        Node first = children.get(0);
        Node last = children.get(children.size() - 1);
        double spanLeft = first.layoutX - first.layoutW / 2.0;
        double spanRight = last.layoutX + last.layoutW / 2.0;
        node.layoutX = (spanLeft + spanRight) / 2.0;
        double ownRight = node.layoutX + node.layoutW / 2.0;
        return Math.max(cursor, ownRight);
    }

    /** Push later siblings right so measured box widths never overlap (e.g. planet + moon beside a peer). */
    private static void separateSiblingSubtrees(Node parent, int siblingGap) {
        separateSiblingSubtrees(parent, siblingGap, null);
    }

    private static void separateSiblingSubtrees(Node parent, int siblingGap, Set<Integer> collapsedKeys) {
        if (parent == null) {
            return;
        }
        List<Node> children = visibleChildren(parent, collapsedKeys);
        if (children.size() < 2) {
            for (Node child : children) {
                separateSiblingSubtrees(child, siblingGap, collapsedKeys);
            }
            return;
        }
        for (int i = 1; i < children.size(); i++) {
            Node prev = children.get(i - 1);
            Node cur = children.get(i);
            double prevRight = subtreeRightEdge(prev, collapsedKeys);
            double curLeft = subtreeLeftEdge(cur, collapsedKeys);
            double shift = prevRight + siblingGap - curLeft;
            if (shift > 0.0) {
                shiftSubtreeX(cur, shift, collapsedKeys);
            }
        }
        for (Node child : children) {
            separateSiblingSubtrees(child, siblingGap, collapsedKeys);
        }
    }

    private static void recenterParents(Node node) {
        recenterParents(node, null);
    }

    private static void recenterParents(Node node, Set<Integer> collapsedKeys) {
        if (node == null) {
            return;
        }
        List<Node> children = visibleChildren(node, collapsedKeys);
        if (children.isEmpty()) {
            return;
        }
        for (Node child : children) {
            recenterParents(child, collapsedKeys);
        }
        Node first = children.get(0);
        Node last = children.get(children.size() - 1);
        double spanLeft = subtreeLeftEdge(first, collapsedKeys);
        double spanRight = subtreeRightEdge(last, collapsedKeys);
        node.layoutX = (spanLeft + spanRight) / 2.0;
    }

    private static double subtreeLeftEdge(Node node) {
        return subtreeLeftEdge(node, null);
    }

    private static double subtreeLeftEdge(Node node, Set<Integer> collapsedKeys) {
        double left = node.layoutX - node.layoutW / 2.0;
        for (Node child : visibleChildren(node, collapsedKeys)) {
            left = Math.min(left, subtreeLeftEdge(child, collapsedKeys));
        }
        return left;
    }

    private static double subtreeRightEdge(Node node) {
        return subtreeRightEdge(node, null);
    }

    private static double subtreeRightEdge(Node node, Set<Integer> collapsedKeys) {
        double right = node.layoutX + node.layoutW / 2.0;
        for (Node child : visibleChildren(node, collapsedKeys)) {
            right = Math.max(right, subtreeRightEdge(child, collapsedKeys));
        }
        return right;
    }

    private static void shiftSubtreeX(Node node, double dx) {
        shiftSubtreeX(node, dx, null);
    }

    private static void shiftSubtreeX(Node node, double dx, Set<Integer> collapsedKeys) {
        node.layoutX += dx;
        for (Node child : visibleChildren(node, collapsedKeys)) {
            shiftSubtreeX(child, dx, collapsedKeys);
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
