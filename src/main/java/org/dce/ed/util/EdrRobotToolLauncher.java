package org.dce.ed.util;

import java.awt.Component;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Opens {@code EDR.jar} / {@link org.dce.edr.EdrRobotTool} from the overlay Tools menu.
 */
public final class EdrRobotToolLauncher {

    private static final String EDR_MAIN_CLASS = "org.dce.edr.EdrRobotTool";
    private static final String EDR_JAR_NAME = "EDR.jar";

    private EdrRobotToolLauncher() {
    }

    public static void launch(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (launchInProcess()) {
                    return;
                }
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) {
                    msg = ex.getClass().getSimpleName();
                }
                JOptionPane.showMessageDialog(parent,
                        "Unable to launch EDR Robot Tool:\n" + msg,
                        "EDR Robot Tool",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            Optional<Path> jar = locateEdrJar();
            if (jar.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        """
                                Could not find EDR.jar.

                                Build EDR (mvn package in the EDR project), then either:
                                  • Place EDR.jar next to EDO-Overlay.jar, or
                                  • Set the EDR_JAR environment variable, or
                                  • Set -Dedr.jar=full\\path\\to\\EDR.jar
                                """,
                        "EDR Robot Tool",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                List<String> command = buildJavaJarCommand(jar.get());
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(jar.get().getParent() != null ? jar.get().getParent().toFile() : null);
                pb.start();
            } catch (IOException ex) {
                String msg = ex.getMessage();
                if (msg == null || msg.isBlank()) {
                    msg = ex.getClass().getSimpleName();
                }
                JOptionPane.showMessageDialog(parent,
                        "Unable to launch EDR Robot Tool:\n" + msg,
                        "EDR Robot Tool",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static boolean launchInProcess() {
        try {
            Class<?> clazz = Class.forName(EDR_MAIN_CLASS);
            clazz.getMethod("showFrame").invoke(null);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("EDR Robot Tool failed to start", ex);
        }
    }

    static Optional<Path> locateEdrJar() {
        List<Path> candidates = new ArrayList<>();

        String explicit = System.getProperty("edr.jar");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("EDR_JAR");
        }
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(Paths.get(explicit.trim()));
        }

        jarDirectoryFromRunningProcess().ifPresent(dir -> candidates.add(dir.resolve(EDR_JAR_NAME)));

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("EDR.jar"));
        candidates.add(cwd.resolve("EDR/target/EDR.jar"));
        candidates.add(cwd.resolve("../EDR/target/EDR.jar"));
        candidates.add(cwd.resolve("target/EDR.jar"));

        Path homeEdo = Path.of(System.getProperty("user.home"), ".edo", EDR_JAR_NAME);
        candidates.add(homeEdo);

        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private static List<String> buildJavaJarCommand(Path jar) {
        List<String> command = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String javaBinary = os.contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaBinary);
        if (Files.isRegularFile(java)) {
            command.add(java.toString());
        } else {
            command.add("java");
        }
        command.add("-jar");
        command.add(jar.toString());
        return command;
    }

    private static Optional<Path> jarDirectoryFromRunningProcess() {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isEmpty()) {
            return Optional.empty();
        }
        Path exe = Path.of(command.get()).toAbsolutePath().normalize();
        if (Files.isRegularFile(exe)) {
            Path parent = exe.getParent();
            if (parent != null) {
                return Optional.of(parent);
            }
        }
        try {
            URI location = EdrRobotToolLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Paths.get(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                Path parent = path.getParent();
                if (parent != null) {
                    return Optional.of(parent);
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
