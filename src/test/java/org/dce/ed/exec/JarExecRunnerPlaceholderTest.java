package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;
import org.junit.jupiter.api.Test;

class JarExecRunnerPlaceholderTest {

    @Test
    void buildCommand_preservesPositionalCountForSpacedValues() {
        ExecPlaceholderContext ctx = new ExecPlaceholderContext();
        ctx.setCarrierJumpTargetSupplier(() -> "Col 285 Sector IX-T b3-3");
        ctx.setShipRouteSessionSupplier(() -> null);
        ExecLaunchContext launch = ExecLaunchContext.builder(ExecTriggerId.MANUAL).build();
        Map<String, String> resolved = ExecPlaceholderResolver.resolveAll(ctx, launch);

        List<String> command = JarExecRunner.buildCommand(
                Path.of("C:/Program Files/RoboHound/RoboHound.exe"),
                "--play fleet-map $CARRIER_JUMP_TARGET $CARRIER_SYSTEM",
                ctx, launch, resolved);

        assertTrue(command.get(0).replace('\\', '/').endsWith("RoboHound.exe"));
        assertEquals("--play", command.get(1));
        assertEquals("fleet-map", command.get(2));
        assertEquals("Col 285 Sector IX-T b3-3", command.get(3));
        assertEquals("Unknown", command.get(4));
        assertEquals(5, command.size());
    }

    @Test
    void resolvedShipValuesAreExportedToChildEnvironment() {
        Map<String, String> env = new HashMap<>();

        JarExecRunner.putResolvedEnvironment(env, Map.of(
                "SHIP_TYPE", "Anaconda",
                "SHIP_ID", "42",
                "SHIP_NAME", "Endurance",
                "SHIP_IDENT", "EDO-42"));

        assertEquals("Anaconda", env.get("EDO_SHIP_TYPE"));
        assertEquals("42", env.get("EDO_SHIP_ID"));
        assertEquals("Endurance", env.get("EDO_SHIP_NAME"));
        assertEquals("EDO-42", env.get("EDO_SHIP_IDENT"));
    }

    @Test
    void unknownShipValuesAreNotExported() {
        Map<String, String> env = new HashMap<>();

        JarExecRunner.putResolvedEnvironment(env, Map.of("SHIP_TYPE", "Unknown"));

        assertTrue(env.isEmpty());
    }
}
