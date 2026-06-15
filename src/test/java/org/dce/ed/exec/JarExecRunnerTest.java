package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class JarExecRunnerTest {

    @Test
    void buildCommand_usesJavaJarAndArgs() {
        List<String> command = JarExecRunner.buildCommand(Path.of("C:/demo/my-tool.jar"), "--play fleet-map");
        assertEquals(5, command.size());
        assertTrue(command.get(0).endsWith("java") || command.get(0).endsWith("java.exe"));
        assertEquals("-jar", command.get(1));
        assertTrue(command.get(2).replace('\\', '/').endsWith("my-tool.jar"));
        assertEquals("--play", command.get(3));
        assertEquals("fleet-map", command.get(4));
    }
}
