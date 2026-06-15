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
        binding.setTrigger(ExecTriggerId.FLEET_COOLDOWN_COMPLETE);
        binding.setDelayMs(10_000);
        binding.setJarPath("C:\\tools\\demo.jar");
        binding.setProgramArgs("--foo");
        config.getBindings().add(binding);

        store.save(config);
        ExecBindingsConfig loaded = store.load();

        assertEquals(75, loaded.getFleetTritiumLowThreshold());
        assertEquals(15, loaded.getFleetTritiumLowHysteresis());
        List<ExecBinding> rows = loaded.getBindings();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isEnabled());
        assertEquals(ExecTriggerId.FLEET_COOLDOWN_COMPLETE, rows.get(0).getTrigger());
        assertEquals(10_000, rows.get(0).getDelayMs());
        assertEquals("C:\\tools\\demo.jar", rows.get(0).getJarPath());
        assertEquals("--foo", rows.get(0).getProgramArgs());
    }
}
