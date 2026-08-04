package org.dce.ed.exec;

import java.awt.Component;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.dce.ed.exec.placeholder.ExecArgsTokenizer;
import org.dce.ed.ui.EdoOptionDialog;
import org.dce.ed.ui.SystemTableHoverCopyManager;
import org.dce.ed.ui.tabdock.OverlayTabId;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Overlay drop installer for scripts that carry an {@code edo} metadata block.
 */
public final class EdoScriptInstaller {

    private static final Gson GSON = new Gson();

    private EdoScriptInstaller() {
    }

    /** {@code true} when the file is JSON with a top-level {@code edo} object. */
    public static boolean hasEdoMetadata(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        String name = file.getFileName() != null ? file.getFileName().toString() : "";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return false;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            return root != null && root.isJsonObject() && root.getAsJsonObject().has("edo")
                    && root.getAsJsonObject().get("edo").isJsonObject();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Installs the script via {@code edo.installArgs} and creates/updates an Exec binding.
     * Must be called on the EDT.
     */
    public static void installDroppedFile(Component parent, Path file, ExecTriggerService triggerService) {
        if (file == null || triggerService == null) {
            return;
        }
        try {
            ParsedScript parsed = parse(file);
            List<String> errors = parsed.metadata.validationErrors();
            if (!errors.isEmpty()) {
                toast(parent, "EDO install failed: " + errors.get(0));
                return;
            }

            ExecBindingsStore store = triggerService.store();
            ExecBindingsConfig config = store.load();
            ExecPrograms.ensureMigrated(config);

            String programPath = resolveOrBrowseProgram(parent, config, parsed.metadata.getProgramName(), store);
            if (programPath == null || programPath.isBlank()) {
                toast(parent, "EDO install cancelled (program not configured).");
                return;
            }

            ExecBinding existing = findConflictingBinding(config, parsed.metadata);
            boolean replace = false;
            if (existing != null) {
                int choice = EdoOptionDialog.showConfirm(parent,
                        "An Exec binding already matches this script:\n"
                                + existing.controlPanelLabel()
                                + "\n\nReplace it?",
                        "Install script",
                        JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION) {
                    toast(parent, "EDO install cancelled.");
                    return;
                }
                replace = true;
            }

            String installArgs = expandInstallArgs(parsed.metadata.getInstallArgs(), file);
            if (replace && !containsForceFlag(installArgs)) {
                installArgs = installArgs + " --force";
            }

            JarExecRunner.RunResult result = runInstall(programPath, parsed.metadata.getProgramName(), installArgs);
            if (result != null && result.exitCode() == 3 && !containsForceFlag(installArgs)) {
                int overwrite = EdoOptionDialog.showConfirm(parent,
                        "That program reports the script already exists.\nOverwrite?",
                        "Install script",
                        JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) {
                    toast(parent, "EDO install cancelled.");
                    return;
                }
                installArgs = installArgs + " --force";
                result = runInstall(programPath, parsed.metadata.getProgramName(), installArgs);
            }
            if (result == null || result.exitCode() != 0) {
                String detail = result != null ? JarExecRunner.formatConciseStatus(result) : "failed";
                toast(parent, "Install failed: " + detail);
                return;
            }

            ExecBinding binding = replace && existing != null ? existing : new ExecBinding();
            applyMetadata(binding, parsed.metadata, programPath);
            binding.setEnabled(true);
            if (!replace || existing == null) {
                config.getBindings().add(binding);
            }
            store.save(config);
            triggerService.fireBindingsChanged();

            String label = binding.controlPanelLabel();
            toast(parent, "Installed: " + (label.isBlank() ? parsed.metadata.getProgramName() : label));
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            toast(parent, "EDO install failed: " + msg);
        }
    }

    private static JarExecRunner.RunResult runInstall(String programPath, String programName, String installArgs) {
        ExecBinding installBinding = new ExecBinding();
        installBinding.setProgramName(programName);
        installBinding.setJarPath(programPath);
        installBinding.setProgramArgs(installArgs);
        return JarExecRunner.run(installBinding, null, null);
    }

    private static ParsedScript parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("edo") || !root.get("edo").isJsonObject()) {
                throw new IOException("Missing edo metadata");
            }
            EdoScriptMetadata metadata = GSON.fromJson(root.get("edo"), EdoScriptMetadata.class);
            if (metadata == null) {
                throw new IOException("Invalid edo metadata");
            }
            return new ParsedScript(metadata);
        }
    }

    private static String resolveOrBrowseProgram(Component parent, ExecBindingsConfig config, String programName,
            ExecBindingsStore store) {
        ExecProgram existing = ExecPrograms.findByName(config.getPrograms(), programName);
        if (existing != null && existing.getPath() != null && !existing.getPath().isBlank()) {
            return existing.getPath();
        }
        int ask = EdoOptionDialog.showConfirm(parent,
                "This script needs program \"" + programName + "\".\n"
                        + "Choose the .exe or .jar?",
                "Add program",
                JOptionPane.OK_CANCEL_OPTION);
        if (ask != JOptionPane.OK_OPTION && ask != JOptionPane.YES_OPTION) {
            return null;
        }
        Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        if (owner == null && parent instanceof Window w) {
            owner = w;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + programName);
        chooser.setFileFilter(new FileNameExtensionFilter("Executables (*.exe, *.jar)", "exe", "jar"));
        int result = chooser.showOpenDialog(owner != null ? owner : parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return null;
        }
        String path = selected.getAbsolutePath();
        ExecProgram program = new ExecProgram(programName, path);
        config.getPrograms().add(program);
        try {
            // Persist catalog entry immediately so a failed install still remembers the program path.
            store.save(config);
        } catch (IOException ignored) {
        }
        return path;
    }

    private static ExecBinding findConflictingBinding(ExecBindingsConfig config, EdoScriptMetadata metadata) {
        String bindingName = metadata.getBindingName();
        String programName = metadata.getProgramName();
        String programArgs = metadata.getProgramArgs();
        for (ExecBinding binding : config.getBindings()) {
            if (binding == null) {
                continue;
            }
            if (!bindingName.isBlank() && bindingName.equalsIgnoreCase(binding.getName())) {
                return binding;
            }
            if (programName.equalsIgnoreCase(nullToEmpty(binding.getProgramName()))
                    && programArgs.equals(nullToEmpty(binding.getProgramArgs()))) {
                return binding;
            }
        }
        return null;
    }

    private static void applyMetadata(ExecBinding binding, EdoScriptMetadata metadata, String programPath) {
        if (!metadata.getBindingName().isBlank()) {
            binding.setName(metadata.getBindingName());
        }
        binding.setIncludeOnControlPanel(metadata.isIncludeOnControlPanel());
        OverlayTabId tab = ExecOverlayButtonSupport.parseButtonTab(metadata.getButtonTab());
        binding.setButtonTab(tab != null ? tab.cardName() : "");
        binding.setProgramName(metadata.getProgramName());
        binding.setJarPath(programPath);
        binding.setProgramArgs(metadata.getProgramArgs());
        binding.setDelayMs(metadata.getDelaySeconds() * 1000);
        ExecTriggerId trigger = parseTrigger(metadata.getTrigger());
        binding.setTrigger(trigger);
        if (trigger == ExecTriggerId.JOURNAL_EVENT && !metadata.getJournalEventType().isBlank()) {
            binding.setJournalEventType(metadata.getJournalEventType());
        }
        binding.setJournalAttributeFilters(metadata.getJournalAttributeFilters());
        if (trigger == ExecTriggerId.SHORTCUT_KEY && !metadata.getShortcutKey().isBlank()) {
            binding.setShortcutKeyDisplay(metadata.getShortcutKey());
        }
    }

    private static ExecTriggerId parseTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            return ExecTriggerId.NONE;
        }
        try {
            return ExecTriggerId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ExecTriggerId.NONE;
        }
    }

    static String expandInstallArgs(String installArgs, Path file) {
        String absolute = file.toAbsolutePath().normalize().toString();
        List<String> tokens = ExecArgsTokenizer.tokenize(installArgs);
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (out.length() > 0) {
                out.append(' ');
            }
            String expanded = "$FILE".equals(token) ? quoteIfNeeded(absolute) : token;
            // Also allow embedded $FILE inside a larger token.
            if (!"$FILE".equals(token) && token.contains("$FILE")) {
                expanded = token.replace("$FILE", absolute);
                expanded = quoteIfNeeded(expanded);
            }
            out.append(expanded);
        }
        return out.toString();
    }

    private static boolean containsForceFlag(String args) {
        for (String token : ExecArgsTokenizer.tokenize(args)) {
            if ("--force".equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static void toast(Component parent, String message) {
        Component anchor = parent != null ? parent : null;
        if (anchor instanceof javax.swing.JComponent jc) {
            SystemTableHoverCopyManager.showToast(jc, message);
        } else if (OverlayFrameCompat.status(message)) {
            // status bar fallback
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private record ParsedScript(EdoScriptMetadata metadata) {
    }

    /** Tiny bridge so this class does not hard-depend on OverlayFrame at class-init. */
    private static final class OverlayFrameCompat {
        static boolean status(String message) {
            try {
                org.dce.ed.OverlayFrame frame = org.dce.ed.OverlayFrame.overlayFrame;
                if (frame != null) {
                    frame.setExecOverlayStatus(message);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
    }
}
