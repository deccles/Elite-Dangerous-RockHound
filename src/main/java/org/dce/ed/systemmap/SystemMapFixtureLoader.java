package org.dce.ed.systemmap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

/**
 * Loads {@link SystemMapFixture} JSON from the classpath ({@code src/test/resources/systemmap/}) or from disk.
 */
public final class SystemMapFixtureLoader {

    private static final Gson GSON = new Gson();

    private SystemMapFixtureLoader() {
    }

    public static SystemMapFixture loadClasspath(String resourceName) throws IOException {
        String path = resourceName.startsWith("/") ? resourceName : "/systemmap/" + resourceName;
        try (InputStream in = SystemMapFixtureLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Fixture not found on classpath: " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                SystemMapFixture fixture = GSON.fromJson(reader, SystemMapFixture.class);
                if (fixture == null) {
                    throw new IOException("Empty fixture: " + path);
                }
                return fixture;
            }
        }
    }

    public static SystemMapFixture loadFile(Path file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            SystemMapFixture fixture = GSON.fromJson(reader, SystemMapFixture.class);
            if (fixture == null) {
                throw new IOException("Empty fixture: " + file);
            }
            return fixture;
        }
    }
}
