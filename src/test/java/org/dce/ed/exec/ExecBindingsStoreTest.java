package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecBindingsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoad_roundTrip() throws Exception {
        ExecBindingsStore store = new ExecBindingsStore(tempDir.resolve("exec-bindings.json"));
        ExecBindingsConfig config = new ExecBindingsConfig();
        config.setFleetTritiumLowThreshold(75);
        config.setFleetTritiumLowHysteresis(15);
        ExecBinding binding = new ExecBinding();
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.JOURNAL_EVENT);
        binding.setJournalEventType("Docked");
        binding.setDelayMs(10_000);
        binding.setJarPath("C:\\tools\\demo.jar");
        binding.setProgramArgs("--foo");
        binding.setName("My macro");
        binding.setIncludeOnControlPanel(true);
        binding.setButtonTab("ROUTE");
        config.getBindings().add(binding);

        store.save(config);
        ExecBindingsConfig loaded = store.load();

        assertEquals(75, loaded.getFleetTritiumLowThreshold());
        assertEquals(15, loaded.getFleetTritiumLowHysteresis());
        List<ExecBinding> rows = loaded.getBindings();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isEnabled());
        assertEquals(ExecTriggerId.JOURNAL_EVENT, rows.get(0).getTrigger());
        assertEquals(10_000, rows.get(0).getDelayMs());
        assertEquals("C:\\tools\\demo.jar", rows.get(0).getJarPath());
        assertEquals("RoboHound", rows.get(0).getProgramName());
        assertEquals(1, loaded.getPrograms().size());
        assertEquals("RoboHound", loaded.getPrograms().get(0).getName());
        assertEquals("C:\\tools\\demo.jar", loaded.getPrograms().get(0).getPath());
        assertEquals("--foo", rows.get(0).getProgramArgs());
        assertEquals("Docked", rows.get(0).getJournalEventType());
        assertEquals("My macro", rows.get(0).getName());
        assertTrue(rows.get(0).isIncludeOnControlPanel());
        assertEquals("ROUTE", rows.get(0).getButtonTab());
    }

    @Test
    void saveAndLoad_noneTrigger_roundTrip() throws Exception {
        ExecBindingsStore store = new ExecBindingsStore(tempDir.resolve("exec-bindings.json"));
        ExecBindingsConfig config = new ExecBindingsConfig();
        ExecBinding binding = new ExecBinding();
        binding.setTrigger(ExecTriggerId.NONE);
        binding.setJarPath("C:\\tools\\manual.jar");
        config.getBindings().add(binding);

        store.save(config);
        ExecBindingsConfig loaded = store.load();

        assertEquals(ExecTriggerId.NONE, loaded.getBindings().get(0).getTrigger());
    }

    @Test
    void newBinding_defaultsToNoTrigger() {
        assertEquals(ExecTriggerId.NONE, new ExecBinding().getTrigger());
    }
}
