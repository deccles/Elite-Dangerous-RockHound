package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdoScriptInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void expandInstallArgs_replacesFileToken() {
        Path file = tempDir.resolve("macro.json");
        String expanded = EdoScriptInstaller.expandInstallArgs("--install $FILE", file);
        assertTrue(expanded.contains("--install"));
        assertTrue(expanded.contains(file.toAbsolutePath().normalize().toString())
                || expanded.contains("\""));
    }

    @Test
    void validation_requiresInstallFileToken() {
        EdoScriptMetadata meta = new EdoScriptMetadata();
        meta.setProgramName("RoboHound");
        meta.setProgramArgs("--play x");
        meta.setInstallArgs("--install");
        assertEquals(1, meta.validationErrors().size());
        meta.setInstallArgs("--install $FILE");
        assertTrue(meta.validationErrors().isEmpty());
    }
}
