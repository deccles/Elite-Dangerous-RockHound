package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HierarchySiblingOrderTest {

    @Test
    void designationKeyFromLabel_parsesMajorIndex() {
        assertEquals(3, HierarchySiblingOrder.designationKeyFromLabel("3"));
        assertEquals(5, HierarchySiblingOrder.designationKeyFromLabel("5, 6, a, b"));
    }

    @Test
    void sortTree_starChildren_eliteMajorOrder() {
        SystemMapHierarchyBuilder.Node root = new SystemMapHierarchyBuilder.Node(
                -1, "Null:0", "", null, SystemMapHierarchyBuilder.NodeKind.SYSTEM_BARYCENTRE);
        SystemMapHierarchyBuilder.Node star = new SystemMapHierarchyBuilder.Node(
                0, "b31-0", "", null, SystemMapHierarchyBuilder.NodeKind.STAR);
        root.children.add(star);

        SystemMapHierarchyBuilder.Node null5 = bary("Null:5");
        null5.children.add(body("1"));
        null5.children.add(body("2"));
        SystemMapHierarchyBuilder.Node null20 = bary("Null:20");
        null20.children.add(body("5"));
        null20.children.add(body("6"));
        star.children.add(null20);
        star.children.add(body("8"));
        star.children.add(body("3"));
        star.children.add(null5);
        star.children.add(body("4"));
        star.children.add(body("7"));

        HierarchySiblingOrder.sortTree(root);

        assertEquals(
                java.util.List.of("Null:5", "3", "4", "Null:20", "7", "8"),
                star.children.stream().map(n -> n.label).toList());
    }

    @Test
    void sortKey_nullHub_usesLowestHostedMajor() {
        SystemMapHierarchyBuilder.Node null5 = bary("Null:5");
        null5.children.add(body("1"));
        null5.children.add(body("2"));
        SystemMapHierarchyBuilder.Node null20 = bary("Null:20");
        null20.children.add(body("5"));
        null20.children.add(body("6"));

        assertEquals(1, HierarchySiblingOrder.sortKey(null5));
        assertEquals(5, HierarchySiblingOrder.sortKey(null20));
        assertEquals(3, HierarchySiblingOrder.sortKey(body("3")));
    }

    private static SystemMapHierarchyBuilder.Node bary(String label) {
        return new SystemMapHierarchyBuilder.Node(
                -50_000, label, "", null, SystemMapHierarchyBuilder.NodeKind.SCAN_BARYCENTRE);
    }

    private static SystemMapHierarchyBuilder.Node body(String label) {
        return new SystemMapHierarchyBuilder.Node(
                label.hashCode(), label, "", null, SystemMapHierarchyBuilder.NodeKind.PLANET);
    }
}
