package org.dce.ed.exec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Helpers for the shared Exec program catalog (name → path) and migration from legacy
 * per-binding {@link ExecBinding#getJarPath()} strings.
 */
public final class ExecPrograms {

    public static final String ADD_PROGRAM_LABEL = "Add Program…";

    private ExecPrograms() {
    }

    /**
     * Ensures {@code config.programs} exists, migrates unique legacy jar paths into
     * {@code RoboHound}, {@code RoboHound 2}, …, assigns {@link ExecBinding#getProgramName()},
     * and syncs each binding's {@code jarPath} from the catalog.
     *
     * @return {@code true} when the catalog or binding program fields changed
     */
    public static boolean ensureMigrated(ExecBindingsConfig config) {
        if (config == null) {
            return false;
        }
        if (config.getPrograms() == null) {
            config.setPrograms(new ArrayList<>());
        }
        List<ExecProgram> programs = config.getPrograms();
        List<ExecBinding> bindings = config.getBindings();
        if (bindings == null) {
            return false;
        }

        int programsBefore = programs.size();
        Map<String, String> pathKeyToName = new LinkedHashMap<>();
        for (ExecProgram program : programs) {
            if (program == null) {
                continue;
            }
            String name = program.getName();
            String path = program.getPath();
            if (name.isBlank() || path.isBlank()) {
                continue;
            }
            pathKeyToName.putIfAbsent(pathKey(path), name);
        }

        for (ExecBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            String path = binding.getJarPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            String key = pathKey(path);
            if (pathKeyToName.containsKey(key)) {
                continue;
            }
            String name = nextRoboHoundName(namesOf(programs));
            ExecProgram created = new ExecProgram(name, path.trim());
            programs.add(created);
            pathKeyToName.put(key, name);
        }

        boolean bindingChanged = false;
        for (ExecBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            String beforeName = binding.getProgramName() != null ? binding.getProgramName() : "";
            String beforePath = binding.getJarPath() != null ? binding.getJarPath() : "";
            syncBindingProgram(binding, programs, pathKeyToName);
            if (!beforeName.equals(binding.getProgramName()) || !beforePath.equals(binding.getJarPath())) {
                bindingChanged = true;
            }
        }
        return programs.size() != programsBefore || bindingChanged;
    }

    /** Resolves catalog path for a binding name; falls back to {@link ExecBinding#getJarPath()}. */
    public static String resolvePath(ExecBindingsConfig config, ExecBinding binding) {
        if (binding == null) {
            return "";
        }
        if (config != null && config.getPrograms() != null) {
            String programName = binding.getProgramName();
            if (programName != null && !programName.isBlank()) {
                ExecProgram match = findByName(config.getPrograms(), programName);
                if (match != null && !match.getPath().isBlank()) {
                    return match.getPath();
                }
            }
        }
        String jarPath = binding.getJarPath();
        return jarPath != null ? jarPath.trim() : "";
    }

    public static ExecProgram findByName(Collection<ExecProgram> programs, String name) {
        if (programs == null || name == null || name.isBlank()) {
            return null;
        }
        String target = name.trim();
        for (ExecProgram program : programs) {
            if (program != null && target.equalsIgnoreCase(program.getName())) {
                return program;
            }
        }
        return null;
    }

    public static String nextRoboHoundName(Collection<String> existingNames) {
        Set<String> lower = new HashSet<>();
        if (existingNames != null) {
            for (String name : existingNames) {
                if (name != null && !name.isBlank()) {
                    lower.add(name.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!lower.contains("robohound")) {
            return "RoboHound";
        }
        int n = 2;
        while (lower.contains(("robohound " + n).toLowerCase(Locale.ROOT))) {
            n++;
        }
        return "RoboHound " + n;
    }

    /**
     * Validates a program list for save: every row needs a non-blank name and path; names unique
     * (case-insensitive). Returns an error message, or {@code null} when valid.
     */
    public static String validateForSave(List<ExecProgram> programs) {
        if (programs == null) {
            return "Program list is missing.";
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < programs.size(); i++) {
            ExecProgram program = programs.get(i);
            if (program == null) {
                return "Program row " + (i + 1) + " is empty.";
            }
            String name = program.getName();
            String path = program.getPath();
            if (name.isBlank()) {
                return "Each program needs a name (row " + (i + 1) + ").";
            }
            if (path.isBlank()) {
                return "Each program needs a path (row " + (i + 1) + ").";
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                return "Program names must be unique (duplicate: \"" + name + "\").";
            }
        }
        return null;
    }

    public static String pathKey(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path normalized = Paths.get(path.trim()).toAbsolutePath().normalize();
            String s = normalized.toString();
            return isWindows() ? s.toLowerCase(Locale.ROOT) : s;
        } catch (Exception ignored) {
            String trimmed = path.trim();
            return isWindows() ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
        }
    }

    private static void syncBindingProgram(ExecBinding binding, List<ExecProgram> programs,
            Map<String, String> pathKeyToName) {
        String programName = binding.getProgramName();
        if (programName != null && !programName.isBlank()) {
            ExecProgram byName = findByName(programs, programName);
            if (byName != null) {
                binding.setProgramName(byName.getName());
                binding.setJarPath(byName.getPath());
                return;
            }
        }
        String path = binding.getJarPath();
        if (path != null && !path.isBlank()) {
            String name = pathKeyToName.get(pathKey(path));
            if (name != null) {
                binding.setProgramName(name);
                ExecProgram byName = findByName(programs, name);
                if (byName != null) {
                    binding.setJarPath(byName.getPath());
                }
                return;
            }
        }
        binding.setProgramName("");
    }

    private static List<String> namesOf(List<ExecProgram> programs) {
        List<String> names = new ArrayList<>();
        for (ExecProgram program : programs) {
            if (program != null && !program.getName().isBlank()) {
                names.add(program.getName());
            }
        }
        return names;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }
}
