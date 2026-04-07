package org.dce.ed.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.dce.ed.cache.SystemCache;

import com.google.gson.Gson;

/**
 * Persists {@link SpanshBodyExobiologyInfo} in the same SQLite file as {@link SystemCache}
 * ({@link SystemCache#getSqliteCacheDbPath()}), table {@code spansh_body_exobiology}.
 */
public final class SpanshBodyExobiologySqliteStore {

    private static final Gson GSON = new Gson();
    private static final String TABLE = "spansh_body_exobiology";

    private static final class Payload {
        boolean excludeFromExobiology;
        List<SpanshLandmark> landmarks;
    }

    private SpanshBodyExobiologySqliteStore() {
    }

    /**
     * Loads a row from SQLite, or null if missing / unreadable / DB error.
     */
    public static SpanshBodyExobiologyInfo load(String systemName, String bodyName) {
        String sys = systemName != null ? systemName : "";
        String body = bodyName != null ? bodyName : "";
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTable(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT payload_json FROM " + TABLE + " WHERE system_name = ? AND body_name = ?")) {
                    ps.setString(1, sys);
                    ps.setString(2, body);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        String json = rs.getString(1);
                        if (json == null || json.isBlank()) {
                            return null;
                        }
                        Payload p = GSON.fromJson(json, Payload.class);
                        if (p == null) {
                            return null;
                        }
                        List<SpanshLandmark> lm = p.landmarks != null ? p.landmarks : new ArrayList<>();
                        return new SpanshBodyExobiologyInfo(lm, p.excludeFromExobiology);
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Spansh SQLite load failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Upserts a successful Spansh response. No-op on null info or I/O failure.
     */
    public static void save(String systemName, String bodyName, SpanshBodyExobiologyInfo info) {
        if (info == null) {
            return;
        }
        String sys = systemName != null ? systemName : "";
        String body = bodyName != null ? bodyName : "";
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        Payload p = new Payload();
        p.excludeFromExobiology = info.isExcludeFromExobiology();
        p.landmarks = new ArrayList<>(info.getLandmarks());
        String json = GSON.toJson(p);
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTable(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + TABLE + " (system_name, body_name, payload_json, updated_at_ms) "
                                + "VALUES (?, ?, ?, ?) "
                                + "ON CONFLICT(system_name, body_name) DO UPDATE SET "
                                + "payload_json = excluded.payload_json, updated_at_ms = excluded.updated_at_ms")) {
                    ps.setString(1, sys);
                    ps.setString(2, body);
                    ps.setString(3, json);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Spansh SQLite save failed: " + ex.getMessage());
        }
    }

    private static void ensureParentDir(Path dbPath) throws java.io.IOException {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static Connection open(Path dbPath) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private static void ensureTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                            + "system_name TEXT NOT NULL,"
                            + "body_name TEXT NOT NULL,"
                            + "payload_json TEXT NOT NULL,"
                            + "updated_at_ms INTEGER NOT NULL,"
                            + "PRIMARY KEY (system_name, body_name)"
                            + ")");
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("PRAGMA journal_mode=WAL");
        }
    }
}
