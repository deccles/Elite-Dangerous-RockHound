package org.dce.ed.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class JarExecRunnerKillTest {

    @Test
    void killRunningProcesses_terminatesLongRunningChild() throws Exception {
        assertEquals(0, JarExecRunner.killRunningProcesses());

        ExecBinding binding = new ExecBinding();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            binding.setJarPath("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            binding.setProgramArgs("-NoProfile -Command Start-Sleep -Seconds 30");
        } else {
            binding.setJarPath("/bin/sh");
            binding.setProgramArgs("-c sleep 30");
        }

        CountDownLatch started = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            started.countDown();
            JarExecRunner.run(binding, ExecLaunchContext.builder(ExecTriggerId.MANUAL).build(), null);
        }, "test-exec-runner");
        runner.setDaemon(true);
        runner.start();

        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

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
}
