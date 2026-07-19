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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.dce.ed.exec.placeholder.ExecPlaceholderContext;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;
import org.dce.ed.exec.placeholder.ExecPlaceholderSubstitutor;

/** Launches a bound program ({@code .exe} or {@code java -jar}) on a background thread. */
public final class JarExecRunner {

    public record RunResult(int exitCode, String detail) {
    }

    private static final int CONCISE_STATUS_MAX_LEN = 56;

    /** Matches RoboHound {@code MacroPlayMain#EXIT_CANCELLED_BY_USER}. */
    public static final int EXIT_CANCELLED_BY_USER = 2;

    private static final Set<TrackedProcess> RUNNING = ConcurrentHashMap.newKeySet();

    private record TrackedProcess(Process process, String label) {
    }

    private JarExecRunner() {
    }

    /** Number of child processes currently running (started by {@link #run} / {@link #runAsync}). */
    public static int runningProcessCount() {
        return RUNNING.size();
    }

    /**
     * Forcibly terminates all tracked exec child processes (e.g. rogue RoboHound macros).
     *
     * @return how many processes were still alive and received {@code destroyForcibly}
     */
    public static int killRunningProcesses() {
        int killed = 0;
        for (TrackedProcess tracked : RUNNING) {
            Process process = tracked.process();
            if (process == null || !process.isAlive()) {
                continue;
            }
            try {
                process.descendants().forEach(handle -> handle.destroyForcibly());
            } catch (UnsupportedOperationException ignored) {
            }
            process.destroyForcibly();
            killed++;
        }
        return killed;
    }

    /** User dismissed the in-game banner (×) or otherwise cancelled — not an Exec failure. */
    public static boolean isUserCancelled(RunResult result) {
        return result != null && result.exitCode() == EXIT_CANCELLED_BY_USER;
    }

    /**
     * Short status text for the overlay bar (avoids long stderr / paths that clear before they can be read).
     */
    public static String formatConciseStatus(RunResult result) {
        if (result == null) {
            return "failed";
        }
        if (result.exitCode() == 0) {
            return "OK";
        }
        String detail = result.detail();
        if (detail == null || detail.isBlank()) {
            return "exit " + result.exitCode();
        }
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("not focused")) {
            return "Elite not focused";
        }
        if (lower.startsWith("program not found")) {
            return "program not found";
        }
        if (lower.contains("program path is empty") || lower.contains("no program")) {
            return "no program configured";
        }
        if (lower.startsWith("no binding")) {
            return "no binding";
        }
        if (lower.contains("java runtime not found") || lower.contains("cannot run program")) {
            return "Java not found";
        }

        int sep = detail.indexOf(" — ");
        String tail = sep >= 0 ? detail.substring(sep + 3).trim() : detail;
        if (tail.isEmpty() || tail.regionMatches(true, 0, "Exit ", 0, 5)) {
            return "exit " + result.exitCode();
        }
        String summary = truncateConcise(firstLine(tail), CONCISE_STATUS_MAX_LEN);
        if (summary.isEmpty()) {
            return "exit " + result.exitCode();
        }
        return "exit " + result.exitCode() + ": " + summary;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }

    static String truncateConcise(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, maxLen - 1)).trim() + "…";
    }

    public static void runAsync(ExecBinding binding, ExecLaunchContext context,
            ExecPlaceholderContext placeholderContext, Consumer<RunResult> onComplete) {
        Thread t = new Thread(() -> {
            RunResult result = run(binding, context, placeholderContext);
            if (onComplete != null) {
                onComplete.accept(result);
            }
        }, "EDO-ExecRunner");
        t.setDaemon(true);
        t.start();
    }

    public static RunResult run(ExecBinding binding, ExecLaunchContext context,
            ExecPlaceholderContext placeholderContext) {
        if (binding == null) {
            return new RunResult(-1, "No binding.");
        }
        String programPath = binding.getJarPath();
        if (programPath == null || programPath.isBlank()) {
            return new RunResult(-1, "Program path is empty.");
        }
        Path program = Paths.get(programPath.trim());
        if (!Files.isRegularFile(program)) {
            return new RunResult(-1, "Program not found: " + program);
        }

        Map<String, String> resolved = ExecPlaceholderResolver.resolveAll(placeholderContext, context);
        List<String> command = buildCommand(program, binding.getProgramArgs(), placeholderContext, context, resolved);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(program.getParent() != null ? program.getParent().toFile() : null);
            Map<String, String> env = pb.environment();
            if (context != null) {
                env.putAll(context.toEnvironment());
            }
            putResolvedEnvironment(env, resolved);
            env.put("EDO_EXEC_STARTED", Instant.now().toString());

            Process process = pb.start();
            TrackedProcess tracked = new TrackedProcess(process, program.getFileName().toString());
            RUNNING.add(tracked);
            try {
                int code = process.waitFor();
                String stderr = drain(process.getErrorStream(), 4000);
                String detail = "Exit " + code + " at " + Instant.now();
                if (!stderr.isBlank()) {
                    detail += " — " + stderr.trim();
                }
                return new RunResult(code, detail);
            } finally {
                RUNNING.remove(tracked);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            if (e instanceof IOException && msg.toLowerCase(Locale.ROOT).contains("cannot run program")) {
                msg = msg + ". " + javaResolutionHint();
            }
            return new RunResult(-1, msg);
        }
    }

    static void putResolvedEnvironment(Map<String, String> env, Map<String, String> resolved) {
        if (env == null || resolved == null) {
            return;
        }
        putKnownEnvironmentValue(env, "EDO_SHIP_TYPE", resolved.get("SHIP_TYPE"));
        putKnownEnvironmentValue(env, "EDO_SHIP_ID", resolved.get("SHIP_ID"));
        putKnownEnvironmentValue(env, "EDO_SHIP_NAME", resolved.get("SHIP_NAME"));
        putKnownEnvironmentValue(env, "EDO_SHIP_IDENT", resolved.get("SHIP_IDENT"));
    }

    private static void putKnownEnvironmentValue(Map<String, String> env, String key, String value) {
        if (value == null || value.isBlank() || ExecPlaceholderResolver.UNKNOWN.equalsIgnoreCase(value.trim())) {
            return;
        }
        env.put(key, value.trim());
    }

    static List<String> buildCommand(Path program, String programArgs) {
        return buildCommand(program, programArgs, null, null, null);
    }

    static List<String> buildCommand(Path program, String programArgs,
            ExecPlaceholderContext placeholderContext, ExecLaunchContext launchContext,
            Map<String, String> resolved) {
        List<String> command = new ArrayList<>();
        // Native binaries (.exe or any non-.jar path): run directly. Only .jar uses java -jar
        // / packaged companion exe.
        if (isNativeExecutable(program)) {
            command.add(program.toAbsolutePath().normalize().toString());
            appendProgramArgs(command, programArgs, placeholderContext, launchContext, resolved);
            return command;
        }
        Optional<Path> packagedExe = JavaLauncher.resolvePackagedExeForJar(program);
        if (packagedExe.isPresent()) {
            command.add(packagedExe.get().toString());
            appendProgramArgs(command, programArgs, placeholderContext, launchContext, resolved);
            return command;
        }
        Optional<Path> java = JavaLauncher.resolve();
        if (java.isPresent()) {
            command.add(java.get().toString());
        } else {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            command.add(os.contains("win") ? "java.exe" : "java");
        }
        command.add("-jar");
        command.add(program.toString());
        appendProgramArgs(command, programArgs, placeholderContext, launchContext, resolved);
        return command;
    }

    /** {@code true} for any launch path that is not a {@code .jar} (Windows {@code .exe}, Unix binaries, etc.). */
    static boolean isNativeExecutable(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        return !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    static boolean isWindowsExecutable(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");
    }

    static String javaResolutionHint() {
        return "Java runtime not found. Use an installed RoboHound.exe path for RoboHound.jar, "
                + "or install JDK 21+ and ensure JAVA_HOME is set.";
    }

    private static void appendProgramArgs(List<String> command, String programArgs,
            ExecPlaceholderContext placeholderContext, ExecLaunchContext launchContext,
            Map<String, String> resolved) {
        if (programArgs == null || programArgs.isBlank()) {
            return;
        }
        if (placeholderContext != null) {
            command.addAll(ExecPlaceholderSubstitutor.expandProgramArgs(
                    programArgs, placeholderContext, launchContext, resolved));
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
