package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.dce.ed.state.BodyInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemMapOrbitStrokePrinterTest {

    @Test
    @DisplayName("Print dump lists bodies and includes header for true-scale fixture")
    void printDump_trueScaleSingleStar_listsBodies() throws IOException {
        SystemMapFixture fixture = SystemMapFixtureLoader.loadClasspath("c16-241-single-k-star.json");
        Map<Integer, BodyInfo> bodies = fixture.toBodies();
        SystemMapModel model = SystemMapPipeline.build(fixture.name, bodies, Instant.EPOCH, false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        SystemMapOrbitStrokePrinter.printToConsole(fixture.name, bodies, model, model.orbitPolylines(),
                false, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("[EDO][OrbitMap][Print]"), out);
        assertTrue(out.contains("system=" + fixture.name), out);
        assertTrue(out.contains("body "), out);
        assertTrue(out.contains("id="), out);
    }
}
