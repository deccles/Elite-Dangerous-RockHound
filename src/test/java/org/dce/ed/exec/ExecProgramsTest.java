package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExecProgramsTest {

    @Test
    void nextRoboHoundName_startsAtBaseThenNumbers() {
        assertEquals("RoboHound", ExecPrograms.nextRoboHoundName(List.of()));
        assertEquals("RoboHound 2", ExecPrograms.nextRoboHoundName(List.of("RoboHound")));
        assertEquals("RoboHound 3", ExecPrograms.nextRoboHoundName(List.of("RoboHound", "RoboHound 2")));
        assertEquals("RoboHound 2", ExecPrograms.nextRoboHoundName(List.of("robohound")));
    }

    @Test
    void validateForSave_rejectsDuplicateNames() {
        List<ExecProgram> programs = new ArrayList<>();
        programs.add(new ExecProgram("Alpha", "C:\\a.exe"));
        programs.add(new ExecProgram("alpha", "C:\\b.exe"));
        assertTrue(ExecPrograms.validateForSave(programs).toLowerCase().contains("unique"));
    }

    @Test
    void validateForSave_allowsDistinct() {
        List<ExecProgram> programs = new ArrayList<>();
        programs.add(new ExecProgram("Alpha", "C:\\a.exe"));
        programs.add(new ExecProgram("Beta", "C:\\b.exe"));
        assertNull(ExecPrograms.validateForSave(programs));
    }

    @Test
    void ensureMigrated_assignsRoboHoundNamesForUniquePaths() {
        ExecBindingsConfig config = new ExecBindingsConfig();
        ExecBinding first = new ExecBinding();
        first.setJarPath("C:\\tools\\RockHound.jar");
        ExecBinding second = new ExecBinding();
        second.setJarPath("C:\\tools\\RockHound.jar");
        ExecBinding third = new ExecBinding();
        third.setJarPath("D:\\other\\tool.exe");
        config.getBindings().add(first);
        config.getBindings().add(second);
        config.getBindings().add(third);

        ExecPrograms.ensureMigrated(config);

        assertEquals(2, config.getPrograms().size());
        assertEquals("RoboHound", config.getPrograms().get(0).getName());
        assertEquals("RoboHound 2", config.getPrograms().get(1).getName());
        assertEquals("RoboHound", first.getProgramName());
        assertEquals("RoboHound", second.getProgramName());
        assertEquals("RoboHound 2", third.getProgramName());
        assertEquals("C:\\tools\\RockHound.jar", first.getJarPath());
        assertEquals("D:\\other\\tool.exe", third.getJarPath());
    }

    @Test
    void ensureMigrated_idempotentWhenCatalogExists() {
        ExecBindingsConfig config = new ExecBindingsConfig();
        config.getPrograms().add(new ExecProgram("RoboHound", "C:\\tools\\RockHound.jar"));
        ExecBinding binding = new ExecBinding();
        binding.setJarPath("C:\\tools\\RockHound.jar");
        config.getBindings().add(binding);

        ExecPrograms.ensureMigrated(config);
        ExecPrograms.ensureMigrated(config);

        assertEquals(1, config.getPrograms().size());
        assertEquals("RoboHound", binding.getProgramName());
    }
}
