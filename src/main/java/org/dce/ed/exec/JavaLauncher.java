package org.dce.ed.exec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Locates a {@code java} binary for {@link JarExecRunner} (jpackage, dev classpath, JAVA_HOME). */
final class JavaLauncher {

    private JavaLauncher() {
    }

    static Optional<Path> resolve() {
        String binary = javaBinaryName();
        Optional<Path> fromHome = fromJavaHome(binary);
        if (fromHome.isPresent()) {
            return fromHome;
        }
        Optional<Path> fromProcess = fromPackagedRuntime(binary);
        if (fromProcess.isPresent()) {
            return fromProcess;
        }
        return fromJavaHomeEnv(binary);
    }

    /**
     * jpackage layout: {@code Install/RoboHound.exe} + {@code Install/app/RoboHound.jar}.
     * Prefer the native launcher over {@code java -jar} when both exist.
     */
    static Optional<Path> resolvePackagedExeForJar(Path jar) {
        if (jar == null || jar.getFileName() == null) {
            return Optional.empty();
        }
        if (!jar.getFileName().toString().equalsIgnoreCase("RoboHound.jar")) {
            return Optional.empty();
        }
        Path parent = jar.getParent();
        if (parent == null) {
            return Optional.empty();
        }
        Path installRoot = "app".equalsIgnoreCase(parent.getFileName().toString()) ? parent.getParent() : parent;
        if (installRoot == null) {
            return Optional.empty();
        }
        Path exe = installRoot.resolve("RoboHound.exe").toAbsolutePath().normalize();
        return Files.isRegularFile(exe) ? Optional.of(exe) : Optional.empty();
    }

    private static Optional<Path> fromJavaHome(String binary) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return Optional.empty();
        }
        return existing(Path.of(javaHome, "bin", binary));
    }

    private static Optional<Path> fromJavaHomeEnv(String binary) {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            return Optional.empty();
        }
        return existing(Path.of(javaHome.trim(), "bin", binary));
    }

    /** When {@code java.home} is wrong, jpackage still ships {@code runtime/bin/java.exe} beside the app exe. */
    private static Optional<Path> fromPackagedRuntime(String binary) {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isEmpty()) {
            return Optional.empty();
        }
        Path exe = Path.of(command.get()).toAbsolutePath().normalize();
        Path installDir = exe.getParent();
        if (installDir == null) {
            return Optional.empty();
        }
        Optional<Path> runtime = existing(installDir.resolve("runtime").resolve("bin").resolve(binary));
        if (runtime.isPresent()) {
            return runtime;
        }
        Path parent = installDir.getParent();
        if (parent != null) {
            return existing(parent.resolve("runtime").resolve("bin").resolve(binary));
        }
        return Optional.empty();
    }

    private static Optional<Path> existing(Path candidate) {
        if (candidate != null && Files.isRegularFile(candidate)) {
            return Optional.of(candidate.toAbsolutePath().normalize());
        }
        return Optional.empty();
    }

    private static String javaBinaryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "java.exe" : "java";
    }
}
