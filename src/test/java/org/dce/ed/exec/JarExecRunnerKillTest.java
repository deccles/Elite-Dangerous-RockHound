package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class JarExecRunnerKillTest {

    @Test
    void killRunningProcesses_terminatesLongRunningChild() throws Exception {
        assertEquals(0, JarExecRunner.killRunningProcesses());

        ExecBinding binding = longRunningChildBinding();

        Thread runner = new Thread(() -> {
            JarExecRunner.run(binding, ExecLaunchContext.builder(ExecTriggerId.MANUAL).build(), null);
        }, "test-exec-runner");
        runner.setDaemon(true);
        runner.start();

        assertTrue(awaitRunningProcessCountAtLeast(1, 10, TimeUnit.SECONDS),
                "child process should be tracked as running");

        int killed = JarExecRunner.killRunningProcesses();
        assertTrue(killed >= 1, "expected at least one running process to kill");
        runner.join(5000);
        assertTrue(!runner.isAlive(), "runner should finish after process is killed");
        assertEquals(0, JarExecRunner.runningProcessCount());
    }

    @Test
    void killRunningProcesses_noOpWhenIdle() {
        assertEquals(0, JarExecRunner.killRunningProcesses());
    }

    private static ExecBinding longRunningChildBinding() {
        ExecBinding binding = new ExecBinding();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            // Tokenized args (split on whitespace). Avoid powershell -Command "..." which cannot
            // survive that split; ping stays alive long enough to kill.
            binding.setJarPath("C:\\Windows\\System32\\ping.exe");
            binding.setProgramArgs("-n 60 127.0.0.1");
            return binding;
        }
        // Must be a real sleep binary — "sh -c sleep 30" tokenizes wrong and exits immediately.
        String sleep = Files.isRegularFile(Path.of("/usr/bin/sleep")) ? "/usr/bin/sleep" : "/bin/sleep";
        binding.setJarPath(sleep);
        binding.setProgramArgs("60");
        return binding;
    }

    private static boolean awaitRunningProcessCountAtLeast(int min, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadlineNs = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNs) {
            if (JarExecRunner.runningProcessCount() >= min) {
                return true;
            }
            Thread.sleep(25);
        }
        return JarExecRunner.runningProcessCount() >= min;
    }
}
