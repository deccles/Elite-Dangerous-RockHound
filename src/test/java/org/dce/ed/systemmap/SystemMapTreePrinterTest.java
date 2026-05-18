package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * {@link SystemMapTreePrinter} contract for Eor Aowsy RI-K c8-3670.
 */
class SystemMapTreePrinterTest {

    private static final String SYSTEM = "Eor Aowsy RI-K c8-3670";

    private static SystemMapFixture fixture;
    private static Map<Integer, BodyInfo> bodies;
    private static SystemMapModel model;

    @BeforeAll
    static void load() throws IOException {
        fixture = SystemMapFixtureLoader.loadClasspath("eor-aowsy-ri-k-c8-3670.json");
        bodies = fixture.toBodies();
        model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, true);
    }

    @Test
    void eorAowsy_treeContainsBcd2Moon() {
        String tree = SystemMapTreePrinter.formatTree(model, bodies, false);
        assertTrue(tree.contains("BCD 2"));
        assertTrue(tree.contains("BCD 2 a"));
        int i2 = tree.indexOf("BCD 2");
        int i2a = tree.indexOf("BCD 2 a", i2);
        assertTrue(i2a > i2, "BCD 2 a should appear after BCD 2 in tree output");
    }

    @Test
    void eorAowsy_treeShowsAbranchPlanets() {
        String tree = SystemMapTreePrinter.formatTree(model, bodies, false);
        assertTrue(tree.contains("A 3 a"));
        assertTrue(tree.contains("A 3 a a"));
    }

    @Test
    void eorAowsy_treeShowsBunderNull3notA() {
        String tree = SystemMapTreePrinter.formatTree(model, bodies, false);
        assertTrue(tree.contains("Null:3") || tree.contains("planetBinary:3"));
        int bLine = tree.indexOf("B (");
        assertTrue(bLine >= 0);
        String line = tree.substring(bLine, Math.min(tree.length(), bLine + 120));
        assertFalse(line.contains("parent A"), "B must not show parent A: " + line);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "EDO_PRINT_SYSTEM", matches = ".+")
    void printSystemTree() {
        String name = System.getenv("EDO_PRINT_SYSTEM").trim();
        SystemMapTreePrinter.printTree(name);
    }
}
