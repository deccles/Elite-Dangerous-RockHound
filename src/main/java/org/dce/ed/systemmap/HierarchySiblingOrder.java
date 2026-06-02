package org.dce.ed.systemmap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Left-to-right sibling order for hierarchy graph layout: Elite major designations (1, 2, 3…),
 * with {@code Null:N} hubs sorted by the lowest hosted designation (e.g. {@code Null:5} before 3, {@code Null:20} before 7).
 */
public final class HierarchySiblingOrder {

    private static final Pattern FIRST_INTEGER = Pattern.compile("(\\d+)");
    private static final int NON_NUMERIC_KEY = 300_000;
    private static final int NULL_HUB_FALLBACK_BASE = 100_000;

    private HierarchySiblingOrder() {
    }

    public static void sortTree(SystemMapHierarchyBuilder.Node node) {
        if (node == null) {
            return;
        }
        node.children.sort(HierarchySiblingOrder::compareNodes);
        for (SystemMapHierarchyBuilder.Node child : node.children) {
            sortTree(child);
        }
        node.collapsePlaceholders.sort(HierarchySiblingOrder::compareNodes);
        for (SystemMapHierarchyBuilder.Node placeholder : node.collapsePlaceholders) {
            sortTree(placeholder);
        }
    }

    private static int compareNodes(SystemMapHierarchyBuilder.Node a, SystemMapHierarchyBuilder.Node b) {
        int cmp = Integer.compare(sortKey(a), sortKey(b));
        if (cmp != 0) {
            return cmp;
        }
        String la = sortableLabel(a);
        String lb = sortableLabel(b);
        return la.compareToIgnoreCase(lb);
    }

    private static String sortableLabel(SystemMapHierarchyBuilder.Node node) {
        return node != null && node.label != null ? node.label : "";
    }

    static int sortKey(SystemMapHierarchyBuilder.Node node) {
        if (node == null) {
            return NON_NUMERIC_KEY;
        }
        if (isBaryKind(node.kind)) {
            int minChild = Integer.MAX_VALUE;
            boolean hasBareMajorChild = false;
            for (SystemMapHierarchyBuilder.Node child : node.children) {
                minChild = Math.min(minChild, sortKey(child));
                if (child != null && child.kind == SystemMapHierarchyBuilder.NodeKind.PLANET
                        && isBareMajorLabelAtNullHub(child.label, node.label)) {
                    hasBareMajorChild = true;
                }
            }
            if (minChild != Integer.MAX_VALUE && hasBareMajorChild) {
                return minChild;
            }
            return nullHubFallbackKey(node.label);
        }
        if (node.kind == SystemMapHierarchyBuilder.NodeKind.STAR) {
            return 0;
        }
        if (node.kind == SystemMapHierarchyBuilder.NodeKind.MOON) {
            return moonSortKey(node.label);
        }
        return designationKeyFromLabel(node.label);
    }

    private static boolean isBaryKind(SystemMapHierarchyBuilder.NodeKind kind) {
        return kind == SystemMapHierarchyBuilder.NodeKind.SCAN_BARYCENTRE
                || kind == SystemMapHierarchyBuilder.NodeKind.PLANET_BINARY_BARYCENTRE
                || kind == SystemMapHierarchyBuilder.NodeKind.SYSTEM_BARYCENTRE;
    }

    /** {@code Null:5} hosting majors {@code 1} and {@code 2}; not {@code Null:14} hosting only {@code A 2}/{@code A 3}. */
    private static boolean isBareMajorLabelAtNullHub(String childLabel, String nullHubLabel) {
        if (childLabel == null || nullHubLabel == null || !nullHubLabel.startsWith("Null:")) {
            return false;
        }
        String trimmed = childLabel.trim();
        Matcher m = FIRST_INTEGER.matcher(trimmed);
        if (!m.matches()) {
            return false;
        }
        try {
            int nullId = Integer.parseInt(nullHubLabel.substring(5).trim());
            return Integer.parseInt(m.group(1)) != nullId;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static int nullHubFallbackKey(String label) {
        if (label != null && label.startsWith("Null:")) {
            try {
                return NULL_HUB_FALLBACK_BASE + Integer.parseInt(label.substring(5).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return NON_NUMERIC_KEY;
    }

    /** First major index in a label ({@code 7}, {@code 5, 6, a}, collapsed groups). */
    static int designationKeyFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return NON_NUMERIC_KEY;
        }
        Matcher m = FIRST_INTEGER.matcher(label.trim());
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return NON_NUMERIC_KEY;
    }

    /** Single-letter moon labels sort after majors at the same depth. */
    private static int moonSortKey(String label) {
        if (label != null && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            return 50_000 + Character.toLowerCase(label.charAt(0));
        }
        return designationKeyFromLabel(label);
    }
}
