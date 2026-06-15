package org.dce.ed.exec;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Loads and saves {@link ExecBindingsConfig} under {@code ~/.edo/exec-bindings.json}. */
public final class ExecBindingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;

    public ExecBindingsStore() {
        this(defaultConfigPath());
    }

    ExecBindingsStore(Path configPath) {
        this.configPath = configPath;
    }

    public static Path defaultConfigPath() {
        String home = System.getProperty("user.home");
        return Paths.get(home != null ? home : ".", ".edo", "exec-bindings.json");
    }

    public Path configPath() {
        return configPath;
    }

    public ExecBindingsConfig load() {
        if (!Files.isRegularFile(configPath)) {
            return new ExecBindingsConfig();
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ExecBindingsConfig config = GSON.fromJson(reader, ExecBindingsConfig.class);
            return normalize(config);
        } catch (Exception e) {
            return new ExecBindingsConfig();
        }
    }

    public void save(ExecBindingsConfig config) throws IOException {
        ExecBindingsConfig normalized = normalize(config);
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(normalized, writer);
        }
    }

    private static ExecBindingsConfig normalize(ExecBindingsConfig config) {
        if (config == null) {
            return new ExecBindingsConfig();
        }
        if (config.getBindings() == null) {
            config.setBindings(new java.util.ArrayList<>());
        }
        config.setFleetTritiumLowThreshold(config.getFleetTritiumLowThreshold());
        config.setFleetTritiumLowHysteresis(config.getFleetTritiumLowHysteresis());
        return config;
    }
}
