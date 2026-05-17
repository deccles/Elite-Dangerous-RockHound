package org.dce.ed.systemmap;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Optional: export fixtures from your live journal directory.
 * <p>
 * Set {@code EDO_JOURNAL_DIR} to your Elite {@code Saved Games/.../Elite Dangerous} folder, then run
 * {@code exportSystem} to write a fixture under {@code target/systemmap-export/}.
 */
class SystemMapJournalExtractorTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "EDO_JOURNAL_DIR", matches = ".+")
    void exportSystem() throws Exception {
        Path journalDir = Path.of(System.getenv("EDO_JOURNAL_DIR"));
        assumeTrue(Files.isDirectory(journalDir), "EDO_JOURNAL_DIR must be a directory");

        String system = System.getenv().getOrDefault("EDO_EXPORT_SYSTEM", "Byua Aim TT-X c15-29");
        SystemMapFixture fx = SystemMapJournalExtractor.extractFromJournals(journalDir, system);

        Path outDir = Path.of("target", "systemmap-export");
        Files.createDirectories(outDir);
        String safe = system.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase();
        Path out = outDir.resolve(safe + ".json");
        SystemMapJournalExtractor.writeFixture(fx, out);
        System.out.println("Wrote fixture: " + out.toAbsolutePath());
    }
}
