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
    void buildCommand_usesUnixBinaryDirectlyWithoutJava() {
        List<String> command = JarExecRunner.buildCommand(Path.of("/usr/bin/sleep"), "60");
        assertEquals(2, command.size());
        assertTrue(command.get(0).replace('\\', '/').endsWith("/usr/bin/sleep"));
        assertEquals("60", command.get(1));
        assertFalse(command.contains("-jar"));
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

    @Test
    void formatConciseStatus_shortensLongFailures() {
        JarExecRunner.RunResult focused = new JarExecRunner.RunResult(1,
                "Exit 1 at 2026-01-01T00:00:00Z — Elite Dangerous is not focused (foreground: javaw.exe). Click the game.");
        assertEquals("Elite not focused", JarExecRunner.formatConciseStatus(focused));

        JarExecRunner.RunResult stderr = new JarExecRunner.RunResult(2,
                "Exit 2 at 2026-01-01T00:00:00Z — java.lang.IllegalStateException: Go To navigation failed because reasons");
        String shortErr = JarExecRunner.formatConciseStatus(stderr);
        assertTrue(shortErr.startsWith("exit 2:"));
        assertTrue(shortErr.contains("IllegalStateException"));
        assertTrue(shortErr.length() < 80);

        assertEquals("OK", JarExecRunner.formatConciseStatus(new JarExecRunner.RunResult(0, "Exit 0 at now")));
        assertEquals("program not found", JarExecRunner.formatConciseStatus(
                new JarExecRunner.RunResult(-1, "Program not found: C:\\missing\\RoboHound.jar")));
    }

    @Test
    void isUserCancelled_recognizesRoboHoundDismissExit() {
        assertTrue(JarExecRunner.isUserCancelled(new JarExecRunner.RunResult(2, "Exit 2 at now")));
        assertFalse(JarExecRunner.isUserCancelled(new JarExecRunner.RunResult(1, "Exit 1 — Interrupted.")));
    }
}
