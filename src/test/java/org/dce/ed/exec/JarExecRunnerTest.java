package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void buildCommand_usesExeDirectlyWithoutJava() {
        List<String> command = JarExecRunner.buildCommand(
                Path.of("C:/Program Files/RoboHound/RoboHound.exe"), "--play fleet-map");
        assertEquals(3, command.size());
        assertTrue(command.get(0).replace('\\', '/').endsWith("RoboHound.exe"));
        assertEquals("--play", command.get(1));
        assertEquals("fleet-map", command.get(2));
    }

    @Test
    void buildCommand_usesPackagedRoboHoundExeWhenJarInAppFolder(@TempDir Path temp) throws Exception {
        Path install = temp.resolve("RoboHound");
        Path app = install.resolve("app");
        Files.createDirectories(app);
        Path jar = app.resolve("RoboHound.jar");
        Files.createFile(jar);
        Path exe = install.resolve("RoboHound.exe");
        Files.createFile(exe);

        List<String> command = JarExecRunner.buildCommand(jar, "--play fleet-map");

        assertEquals(3, command.size());
        assertTrue(command.get(0).replace('\\', '/').endsWith("RoboHound.exe"));
        assertFalse(command.contains("-jar"));
        assertEquals("--play", command.get(1));
        assertEquals("fleet-map", command.get(2));
    }
}
