package org.dce.ed.systemmap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

public final class SystemMapExpectedTreeLoader {

    private static final Gson GSON = new Gson();

    private SystemMapExpectedTreeLoader() {
    }

    public static SystemMapExpectedTree loadClasspath(String resourceName) throws IOException {
        String path = resourceName.startsWith("/") ? resourceName : "/systemmap/" + resourceName;
        try (InputStream in = SystemMapExpectedTreeLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Expected tree not found: " + path);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                SystemMapExpectedTree tree = GSON.fromJson(reader, SystemMapExpectedTree.class);
                if (tree == null) {
                    throw new IOException("Empty expected tree: " + path);
                }
                return tree;
            }
        }
    }
}
