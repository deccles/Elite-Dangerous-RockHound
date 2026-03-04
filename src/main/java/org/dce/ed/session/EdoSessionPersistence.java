package org.dce.ed.session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.dce.ed.cache.SystemCache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Reads and writes {@link EdoSessionState} to a single JSON file (edo-session.json).
 * Uses the same directory as {@link SystemCache}: if {@value org.dce.ed.cache.SystemCache#CACHE_PATH_PROPERTY}
 * is set, the session file is in that path's parent; otherwise user.home.
 */
public final class EdoSessionPersistence {

    private static final String SESSION_FILE_NAME = "edo-session.json";

    private static Path getSessionPath() {
        String override = System.getProperty(SystemCache.CACHE_PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            Path cachePath = Paths.get(override).toAbsolutePath().normalize();
            return cachePath.getParent().resolve(SESSION_FILE_NAME);
        }
        String home = System.getProperty("user.home");
        if (home == null || home.isEmpty()) {
            home = ".";
        }
        return Paths.get(home, SESSION_FILE_NAME);
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private EdoSessionPersistence() {
    }

    /**
     * Load session state from disk. Returns a new empty state if the file does not exist or is invalid.
     */
    public static EdoSessionState load() {
        Path path = getSessionPath();
        if (!Files.isRegularFile(path)) {
            return new EdoSessionState();
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            EdoSessionState state = GSON.fromJson(reader, EdoSessionState.class);
            return state != null ? state : new EdoSessionState();
        } catch (Exception e) {
            System.err.println("EdoSessionPersistence: failed to load " + path + ": " + e.getMessage());
            return new EdoSessionState();
        }
    }

    /**
     * Save session state to disk. Creates parent directory if needed.
     */
    public static void save(EdoSessionState state) {
        if (state == null) {
            return;
        }
        Path path = getSessionPath();
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            System.err.println("EdoSessionPersistence: failed to save " + path + ": " + e.getMessage());
        }
    }
}
