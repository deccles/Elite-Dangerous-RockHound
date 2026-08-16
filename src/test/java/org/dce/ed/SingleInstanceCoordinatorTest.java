package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceCoordinatorTest {

    @Test
    void secondLaunchSignalsTheExistingInstance(@TempDir Path directory) throws Exception {
        CountDownLatch activated = new CountDownLatch(1);

        try (SingleInstanceCoordinator first = SingleInstanceCoordinator.start(directory, activated::countDown)) {
            Optional<SingleInstanceCoordinator> second = SingleInstanceCoordinator.tryStart(directory, () -> { });

            assertFalse(second.isPresent());
            assertTrue(activated.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingTheExistingInstanceAllowsANewLaunch(@TempDir Path directory) throws Exception {
        SingleInstanceCoordinator first = SingleInstanceCoordinator.start(directory, () -> { });
        first.close();

        try (SingleInstanceCoordinator replacement = SingleInstanceCoordinator.start(directory, () -> { })) {
            assertTrue(replacement.isPrimaryInstance());
        }
    }

    @Test
    void staleActivationDataDoesNotPreventAFirstLaunch(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("rockhound-instance.port"), "1");

        try (SingleInstanceCoordinator coordinator = SingleInstanceCoordinator.start(directory, () -> { })) {
            assertTrue(coordinator.isPrimaryInstance());
        }
    }

    @Test
    void stalledLocalConnectionDoesNotBlockLaterActivation(@TempDir Path directory) throws Exception {
        CountDownLatch activated = new CountDownLatch(1);

        try (SingleInstanceCoordinator first = SingleInstanceCoordinator.start(directory, activated::countDown);
                Socket stalledClient = new Socket()) {
            int port = Integer.parseInt(Files.readString(
                    directory.resolve("rockhound-instance.port"), StandardCharsets.US_ASCII).trim());
            stalledClient.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            Thread.sleep(100L);

            Optional<SingleInstanceCoordinator> second = SingleInstanceCoordinator.tryStart(directory, () -> { });

            assertFalse(second.isPresent());
            assertTrue(activated.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void corruptPortDataProducesAControlledFailureWhileTheLockIsOwned(@TempDir Path directory) throws Exception {
        try (SingleInstanceCoordinator first = SingleInstanceCoordinator.start(directory, () -> { })) {
            Files.writeString(directory.resolve("rockhound-instance.port"), "99999");

            assertThrows(IOException.class, () -> SingleInstanceCoordinator.tryStart(directory, () -> { }));
        }
    }

    @Test
    void separateProcessSignalsPrimaryAndCrashReleasesOwnership(@TempDir Path directory) throws Exception {
        Path activationMarker = directory.resolve("activated");
        Path primaryOutput = directory.resolve("primary.out");
        Process primary = startHelperProcess(directory, activationMarker, "primary", primaryOutput);

        try {
            awaitFileText(primaryOutput, "READY");

            Path secondaryOutput = directory.resolve("secondary.out");
            Process secondary = startHelperProcess(directory, activationMarker, "secondary", secondaryOutput);
            assertTrue(secondary.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, secondary.exitValue());
            awaitFileText(secondaryOutput, "SECONDARY");
            awaitFileText(activationMarker, "ACTIVATED");
        } finally {
            primary.destroyForcibly();
            assertTrue(primary.waitFor(5, TimeUnit.SECONDS));
        }

        Path replacementOutput = directory.resolve("replacement.out");
        Process replacement = startHelperProcess(directory, activationMarker, "primary", replacementOutput);
        try {
            awaitFileText(replacementOutput, "READY");
        } finally {
            replacement.destroyForcibly();
            assertTrue(replacement.waitFor(5, TimeUnit.SECONDS));
        }
    }

    private static Process startHelperProcess(
            Path directory, Path activationMarker, String mode, Path output) throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(
                javaExecutable,
                "-cp",
                classPath,
                ProcessMain.class.getName(),
                directory.toString(),
                activationMarker.toString(),
                mode,
                output.toString()).start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    private static void awaitFileText(Path file, String expectedText) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Files.exists(file) && Files.readString(file).contains(expectedText)) {
                return;
            }
            Thread.sleep(25L);
        }
        String actual = Files.exists(file) ? Files.readString(file) : "<missing>";
        throw new AssertionError("Expected " + file + " to contain " + expectedText + ", but was: " + actual);
    }

    public static final class ProcessMain {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            Path activationMarker = Path.of(args[1]);
            Path statusFile = Path.of(args[3]);
            if ("secondary".equals(args[2])) {
                Optional<SingleInstanceCoordinator> result = SingleInstanceCoordinator.tryStart(directory, () -> { });
                Files.writeString(statusFile, result.isEmpty() ? "SECONDARY" : "PRIMARY");
                if (result.isPresent()) {
                    result.get().close();
                }
                return;
            }

            try (SingleInstanceCoordinator ignored = SingleInstanceCoordinator.start(directory, () -> {
                try {
                    Files.writeString(activationMarker, "ACTIVATED");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })) {
                Files.writeString(statusFile, "READY");
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            }
        }
    }
}
