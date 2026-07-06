package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

class ExecShortcutKeyTriggerTest {

    @Test
    void onShortcutKeyPressed_runsMatchingEnabledBinding() throws Exception {
        java.nio.file.Path configPath = java.nio.file.Files.createTempDirectory("edo-exec")
                .resolve("exec-bindings.json");
        ExecBindingsStore store = new ExecBindingsStore(configPath);
        ExecBindingsConfig config = new ExecBindingsConfig();
        ExecBinding binding = new ExecBinding();
        binding.setEnabled(true);
        binding.setTrigger(ExecTriggerId.SHORTCUT_KEY);
        binding.setShortcutKeyCode(NativeKeyEvent.VC_F10);
        binding.setJarPath("demo.jar");
        config.getBindings().add(binding);

        ExecTriggerService service = new ExecTriggerService(store);
        service.setConfigSupplier(() -> config);
        AtomicBoolean launched = new AtomicBoolean(false);
        service.setStatusListener(msg -> {
            if (msg != null && msg.startsWith("Running ")) {
                launched.set(true);
            }
        });

        service.onShortcutKeyPressed(NativeKeyEvent.VC_F9);
        assertFalse(launched.get());

        service.onShortcutKeyPressed(NativeKeyEvent.VC_F10);
        assertTrue(launched.get());
    }

    @Test
    void shortcutKey_roundTripsThroughStore() throws Exception {
        ExecBindingsStore store = new ExecBindingsStore(
                java.nio.file.Files.createTempDirectory("edo-exec").resolve("exec-bindings.json"));
        ExecBindingsConfig config = new ExecBindingsConfig();
        ExecBinding binding = new ExecBinding();
        binding.setTrigger(ExecTriggerId.SHORTCUT_KEY);
        binding.setShortcutKeyCode(NativeKeyEvent.VC_F7);
        config.getBindings().add(binding);
        store.save(config);

        ExecBinding loaded = store.load().getBindings().get(0);
        assertEquals(ExecTriggerId.SHORTCUT_KEY, loaded.getTrigger());
        assertEquals(NativeKeyEvent.VC_F7, loaded.getShortcutKeyCode());
        assertEquals("F7", loaded.getShortcutKeyDisplay());
    }
}
