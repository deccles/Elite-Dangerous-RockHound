package org.dce.ed.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Launches a JAR with {@code java -jar} on a background thread. */
public final class JarExecRunner {

    public record RunResult(int exitCode, String detail) {
    }

    private JarExecRunner() {
    }

    public static void runAsync(ExecBinding binding, ExecLaunchContext context,
            Consumer<RunResult> onComplete) {
        Thread t = new Thread(() -> {
            RunResult result = run(binding, context);
            if (onComplete != null) {
                onComplete.accept(result);
            }
        }, "EDO-ExecRunner");
        t.setDaemon(true);
        t.start();
    }

    public static RunResult run(ExecBinding binding, ExecLaunchContext context) {
        if (binding == null) {
            return new RunResult(-1, "No binding.");
        }
        String jarPath = binding.getJarPath();
        if (jarPath == null || jarPath.isBlank()) {
            return new RunResult(-1, "JAR path is empty.");
        }
        Path jar = Paths.get(jarPath.trim());
        if (!Files.isRegularFile(jar)) {
            return new RunResult(-1, "JAR not found: " + jar);
        }

        List<String> command = buildCommand(jar, binding.getProgramArgs());
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(jar.getParent() != null ? jar.getParent().toFile() : null);
            Map<String, String> env = pb.environment();
            if (context != null) {
                env.putAll(context.toEnvironment());
            }
            env.put("EDO_EXEC_STARTED", Instant.now().toString());

            Process process = pb.start();
            String stderr = drain(process.getErrorStream(), 4000);
            int code = process.waitFor();
            String detail = "Exit " + code + " at " + Instant.now();
            if (!stderr.isBlank()) {
                detail += " — " + stderr.trim();
            }
            return new RunResult(code, detail);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            return new RunResult(-1, msg);
        }
    }

    static List<String> buildCommand(Path jar, String programArgs) {
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
        appendProgramArgs(command, programArgs);
        return command;
    }

    private static void appendProgramArgs(List<String> command, String programArgs) {
        if (programArgs == null || programArgs.isBlank()) {
            return;
        }
        for (String token : programArgs.trim().split("\\s+")) {
            if (!token.isBlank()) {
                command.add(token);
            }
        }
    }

    private static String drain(InputStream stream, int maxChars) {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && sb.length() < maxChars) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(line);
            }
        } catch (IOException ignored) {
        }
        if (sb.length() > maxChars) {
            return sb.substring(0, maxChars) + "…";
        }
        return sb.toString();
    }
}
