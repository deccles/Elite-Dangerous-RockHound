package org.dce.ed.util;

import java.awt.Component;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import javax.swing.SwingUtilities;

import org.dce.ed.DecoratedOverlayDialog;
import org.dce.ed.EliteDangerousOverlay;
import org.dce.ed.OverlayFrame;

/**
 * Relaunches the overlay executable (installed build) or the dev classpath entry point.
 */
public final class OverlayAppRestart {

    private static final String PACKAGED_EXE_NAME = "RockHound.exe";

    private OverlayAppRestart() {
    }

    public static void restart(Component parent) throws IOException {
        Window window = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        persistBeforeRestart(window);
        spawnReplacementProcess();
        System.exit(0);
    }

    private static void persistBeforeRestart(Window window) {
        if (window instanceof OverlayFrame frame) {
            frame.prepareForApplicationRestart();
        } else if (window instanceof DecoratedOverlayDialog dialog) {
            dialog.prepareForApplicationRestart();
        }
    }

    private static void spawnReplacementProcess() throws IOException {
        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isPresent()) {
            Path exe = Path.of(command.get()).toAbsolutePath().normalize();
            if (Files.isRegularFile(exe)
                    && PACKAGED_EXE_NAME.equalsIgnoreCase(exe.getFileName().toString())) {
                new ProcessBuilder(exe.toString())
                        .directory(exe.getParent() != null ? exe.getParent().toFile() : null)
                        .start();
                return;
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String javaBinary = os.contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaBinary);
        String classpath = System.getProperty("java.class.path");
        String mainClass = EliteDangerousOverlay.class.getName();
        new ProcessBuilder(java.toString(), "-cp", classpath, mainClass).start();
    }
}
