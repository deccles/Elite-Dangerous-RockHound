package org.dce.ed.logreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.dce.ed.logreader.event.ProspectedAsteroidEvent;
import org.dce.ed.logreader.event.ProspectedAsteroidEvent.MaterialProportion;
import org.junit.jupiter.api.Test;

class ProspectedAsteroidEventParsingTest {

    private static ProspectedAsteroidEvent parse(String jsonLine) {
        EliteLogParser parser = new EliteLogParser();
        return (ProspectedAsteroidEvent) parser.parseRecord(jsonLine);
    }

    @Test
    void parse_missingMaterials_returnsEmptyMaterialsList() {
        String json = """
                {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High","MotherlodeMaterial":"platinum"}
                """;
        ProspectedAsteroidEvent e = parse(json);
        assertNotNull(e);
        assertTrue(e.getMaterials().isEmpty());
    }

    @Test
    void parse_materialsNull_returnsEmptyMaterialsList() {
        String json = """
                {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High","MotherlodeMaterial":"platinum","Materials":null}
                """;
        ProspectedAsteroidEvent e = parse(json);
        assertNotNull(e);
        assertTrue(e.getMaterials().isEmpty());
    }

    @Test
    void parse_materialWithNullProportion_setsProportionToZero() {
        String json = """
                {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High",
                 "Materials":[{"Name":"platinum","Proportion":null}]}
                """;
        ProspectedAsteroidEvent e = parse(json);
        assertNotNull(e);

        assertEquals(1, e.getMaterials().size());
        MaterialProportion mp = e.getMaterials().get(0);
        assertEquals("platinum", mp.getName());
        assertEquals(0.0, mp.getProportion(), 1e-9);
    }

    @Test
    void parse_materialMissingName_skipsMaterial() {
        String json = """
                {"timestamp":"2026-03-25T10:15:30Z","event":"ProspectedAsteroid","Content":"High",
                 "Materials":[{"Proportion":50.0}]}
                """;
        ProspectedAsteroidEvent e = parse(json);
        assertNotNull(e);
        assertTrue(e.getMaterials().isEmpty());
    }
}

