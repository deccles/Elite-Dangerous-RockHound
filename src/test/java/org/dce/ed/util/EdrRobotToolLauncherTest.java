package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdrRobotToolLauncherTest {

    @Test
    void locateEdrJar_usesExplicitSystemProperty(@TempDir Path temp) throws Exception {
        Path jar = temp.resolve("EDR.jar");
        Files.writeString(jar, "stub");

        String key = "edr.jar";
        String previous = System.getProperty(key);
        System.setProperty(key, jar.toString());
        try {
            assertTrue(EdrRobotToolLauncher.locateEdrJar().isPresent());
            assertTrue(EdrRobotToolLauncher.locateEdrJar().get().endsWith("EDR.jar"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
