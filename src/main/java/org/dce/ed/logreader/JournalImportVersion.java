package org.dce.ed.logreader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.dce.ed.cache.SystemCache;

/**
 * Tool-wide journal rebuild version. Bump {@link #VERSION} whenever any journal-derived state
 * (engineering, exobiology, missions, etc.) must be rebuilt from scratch; the next startup runs a
 * full journal rescan once and stores the new version.
 */
public final class JournalImportVersion {

    /**
     * Increment when parsing or derived caches need a full journal rescan at next tool startup.
     */
    public static final int VERSION = 2;

    private static final String TABLE = "edo_journal_import_meta";

    private JournalImportVersion() {
    }

    /** {@code true} when stored version is missing or below {@link #VERSION}. */
    public static boolean isStale() {
        return readStored() < VERSION;
    }

    /** Persist {@link #VERSION} after a successful startup full rescan. */
    public static void markApplied() {
        writeStored(VERSION);
    }

    private static int readStored() {
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTable(c);
                try (Statement st = c.createStatement();
                        ResultSet rs = st.executeQuery(
                                "SELECT import_version FROM " + TABLE + " WHERE singleton = 1")) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Journal import version read failed: " + ex.getMessage());
        }
        return 0;
    }

    private static void writeStored(int version) {
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTable(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + TABLE + " (singleton, import_version) VALUES (1, ?) "
                                + "ON CONFLICT(singleton) DO UPDATE SET import_version = excluded.import_version")) {
                    ps.setInt(1, version);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Journal import version write failed: " + ex.getMessage());
        }
    }

    private static void ensureTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                            + "singleton INTEGER NOT NULL PRIMARY KEY CHECK (singleton = 1),"
                            + "import_version INTEGER NOT NULL"
                            + ")");
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
}
