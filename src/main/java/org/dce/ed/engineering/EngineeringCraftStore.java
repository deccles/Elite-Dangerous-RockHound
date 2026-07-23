package org.dce.ed.engineering;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dce.ed.cache.SystemCache;
import org.dce.ed.logreader.EliteJournalReader;
import org.dce.ed.logreader.EliteLogEvent;
import org.dce.ed.logreader.EliteLogParser;
import org.dce.ed.logreader.event.EngineerCraftEvent;
import org.dce.ed.logreader.event.LoadGameEvent;
import org.dce.ed.logreader.event.LoadoutEvent;

import com.google.gson.JsonObject;

/**
 * Persists {@code EngineerCraft} rows attributed to a {@code ShipID}, plus the latest {@code Loadout}
 * per hull, in the same SQLite file as {@link SystemCache}.
 */
public final class EngineeringCraftStore {

    private static final String CRAFTS_TABLE = "engineering_crafts";
    private static final String LOADOUTS_TABLE = "engineering_ship_loadouts";

    private EngineeringCraftStore() {
    }

    /**
     * Full rebuild from journals: track current ship from Loadout/LoadGame, store every craft with
     * that ship id, and keep the latest loadout JSON per ship.
     */
    public static void reparseFromJournal(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            EliteJournalReader reader = new EliteJournalReader(clientKey);
            List<EliteLogEvent> events = reader.readAllEvents();

            List<EngineeringCraftRecord> crafts = new ArrayList<>();
            Map<Long, LoadoutSnapshot> loadouts = new LinkedHashMap<>();
            long currentShipId = -1L;
            for (EliteLogEvent event : events) {
                if (event instanceof LoadoutEvent loadout && loadout.getShipId() >= 0) {
                    currentShipId = loadout.getShipId();
                    String raw = rawJsonString(loadout);
                    if (raw != null && !raw.isBlank()) {
                        loadouts.put(Long.valueOf(currentShipId), new LoadoutSnapshot(
                                currentShipId,
                                loadout.getTimestamp() != null ? loadout.getTimestamp() : Instant.EPOCH,
                                raw));
                    }
                    continue;
                }
                if (event instanceof LoadGameEvent loadGame && loadGame.getShipId() >= 0) {
                    currentShipId = loadGame.getShipId();
                    continue;
                }
                if (event instanceof EngineerCraftEvent craft) {
                    String raw = rawJsonString(craft);
                    crafts.add(new EngineeringCraftRecord(
                            clientKey,
                            craft.getTimestamp(),
                            currentShipId,
                            craft.getSlot(),
                            craft.getModule(),
                            craft.getBlueprintName(),
                            craft.getLevel(),
                            craft.getQuality(),
                            raw != null ? raw : ""));
                    if (EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(craft)
                            && currentShipId >= 0) {
                        LoadoutSnapshot snap = loadouts.get(Long.valueOf(currentShipId));
                        if (snap != null) {
                            String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(
                                    snap.rawJson(), craft);
                            if (patched != null) {
                                Instant ts = craft.getTimestamp() != null
                                        ? craft.getTimestamp()
                                        : snap.timestamp();
                                if (ts.isBefore(snap.timestamp())) {
                                    ts = snap.timestamp();
                                }
                                loadouts.put(Long.valueOf(currentShipId),
                                        new LoadoutSnapshot(currentShipId, ts, patched));
                            }
                        }
                    }
                }
            }

            try (Connection c = open(dbPath)) {
                ensureTables(c);
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement delCrafts = c.prepareStatement(
                            "DELETE FROM " + CRAFTS_TABLE + " WHERE client_key = ?")) {
                        delCrafts.setString(1, clientKey);
                        delCrafts.executeUpdate();
                    }
                    try (PreparedStatement delLoadouts = c.prepareStatement(
                            "DELETE FROM " + LOADOUTS_TABLE + " WHERE client_key = ?")) {
                        delLoadouts.setString(1, clientKey);
                        delLoadouts.executeUpdate();
                    }
                    try (PreparedStatement insCraft = c.prepareStatement(
                            "INSERT INTO " + CRAFTS_TABLE + " ("
                                    + "client_key, ts_epoch_ms, ship_id, slot, module, blueprint_name, "
                                    + "level, quality, raw_json) VALUES (?,?,?,?,?,?,?,?,?)")) {
                        for (EngineeringCraftRecord craft : crafts) {
                            bindCraft(insCraft, craft);
                            insCraft.addBatch();
                        }
                        insCraft.executeBatch();
                    }
                    try (PreparedStatement insLoadout = c.prepareStatement(
                            "INSERT INTO " + LOADOUTS_TABLE + " ("
                                    + "client_key, ship_id, ts_epoch_ms, raw_json) VALUES (?,?,?,?)")) {
                        for (LoadoutSnapshot snap : loadouts.values()) {
                            insLoadout.setString(1, clientKey);
                            insLoadout.setLong(2, snap.shipId());
                            insLoadout.setLong(3, snap.timestamp().toEpochMilli());
                            insLoadout.setString(4, snap.rawJson());
                            insLoadout.addBatch();
                        }
                        insLoadout.executeBatch();
                    }
                    c.commit();
                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering craft reparse failed: " + ex.getMessage());
        }
    }

    /**
     * Live craft with attributed hull. Skips when {@code shipId < 0}.
     *
     * @return true when the stored Loadout snapshot was patched from this craft
     */
    public static boolean rememberCraft(String clientKey, EngineerCraftEvent craft, long shipId) {
        if (clientKey == null || clientKey.isBlank() || craft == null || shipId < 0) {
            return false;
        }
        String raw = rawJsonString(craft);
        upsertCraft(new EngineeringCraftRecord(
                clientKey,
                craft.getTimestamp(),
                shipId,
                craft.getSlot(),
                craft.getModule(),
                craft.getBlueprintName(),
                craft.getLevel(),
                craft.getQuality(),
                raw != null ? raw : ""));
        return patchStoredLoadoutFromCraft(clientKey, craft, shipId);
    }

    /**
     * Updates the stored Loadout snapshot from an {@code EngineerCraft} when Elite omits a fresh
     * Loadout (grade rolls and experimental-only applies).
     *
     * @return true when the stored loadout JSON was changed
     */
    public static boolean patchStoredLoadoutFromCraft(String clientKey,
                                                      EngineerCraftEvent craft,
                                                      long shipId) {
        if (clientKey == null || clientKey.isBlank() || craft == null || shipId < 0
                || !EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(craft)) {
            return false;
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                String existingRaw = null;
                long existingTs = 0L;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT raw_json, ts_epoch_ms FROM " + LOADOUTS_TABLE
                                + " WHERE client_key = ? AND ship_id = ?")) {
                    ps.setString(1, clientKey);
                    ps.setLong(2, shipId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingRaw = rs.getString(1);
                            existingTs = rs.getLong(2);
                        }
                    }
                }
                if (existingRaw == null || existingRaw.isBlank()) {
                    return false;
                }
                String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(
                        existingRaw, craft);
                if (patched == null) {
                    return false;
                }
                Instant craftTs = craft.getTimestamp() != null ? craft.getTimestamp() : Instant.EPOCH;
                long tsMs = Math.max(existingTs, craftTs.toEpochMilli());
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + LOADOUTS_TABLE + " (client_key, ship_id, ts_epoch_ms, raw_json) "
                                + "VALUES (?,?,?,?) "
                                + "ON CONFLICT(client_key, ship_id) DO UPDATE SET "
                                + "ts_epoch_ms = excluded.ts_epoch_ms, raw_json = excluded.raw_json")) {
                    ps.setString(1, clientKey);
                    ps.setLong(2, shipId);
                    ps.setLong(3, tsMs);
                    ps.setString(4, patched);
                    ps.executeUpdate();
                }
                return true;
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering loadout craft patch failed: " + ex.getMessage());
            return false;
        }
    }

    /** @deprecated use {@link #patchStoredLoadoutFromCraft} */
    public static boolean patchStoredLoadoutFromExperimentalCraft(String clientKey,
                                                                  EngineerCraftEvent craft,
                                                                  long shipId) {
        return patchStoredLoadoutFromCraft(clientKey, craft, shipId);
    }

    public static void rememberLoadout(String clientKey, LoadoutEvent loadout) {
        if (clientKey == null || clientKey.isBlank() || loadout == null || loadout.getShipId() < 0) {
            return;
        }
        String raw = rawJsonString(loadout);
        if (raw == null || raw.isBlank()) {
            return;
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + LOADOUTS_TABLE + " (client_key, ship_id, ts_epoch_ms, raw_json) "
                                + "VALUES (?,?,?,?) "
                                + "ON CONFLICT(client_key, ship_id) DO UPDATE SET "
                                + "ts_epoch_ms = excluded.ts_epoch_ms, raw_json = excluded.raw_json "
                                + "WHERE excluded.ts_epoch_ms >= " + LOADOUTS_TABLE + ".ts_epoch_ms")) {
                    ps.setString(1, clientKey);
                    ps.setLong(2, loadout.getShipId());
                    Instant ts = loadout.getTimestamp() != null ? loadout.getTimestamp() : Instant.EPOCH;
                    ps.setLong(3, ts.toEpochMilli());
                    ps.setString(4, raw);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering loadout store failed: " + ex.getMessage());
        }
    }

    public static List<EngineeringCraftRecord> listCrafts(String clientKey) {
        List<EngineeringCraftRecord> out = new ArrayList<>();
        if (clientKey == null || clientKey.isBlank()) {
            return out;
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT client_key, ts_epoch_ms, ship_id, slot, module, blueprint_name, "
                                + "level, quality, raw_json FROM " + CRAFTS_TABLE
                                + " WHERE client_key = ? ORDER BY ts_epoch_ms ASC, rowid ASC")) {
                    ps.setString(1, clientKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new EngineeringCraftRecord(
                                    rs.getString(1),
                                    Instant.ofEpochMilli(rs.getLong(2)),
                                    rs.getLong(3),
                                    rs.getString(4),
                                    rs.getString(5),
                                    rs.getString(6),
                                    rs.getInt(7),
                                    rs.getDouble(8),
                                    rs.getString(9)));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering craft list failed: " + ex.getMessage());
        }
        return out;
    }

    /** Latest loadout per ship, reconstructed from stored journal JSON. */
    public static Map<Long, LoadoutEvent> loadLatestLoadouts(String clientKey) {
        Map<Long, String> rawByShip = new LinkedHashMap<>();
        Map<Long, Instant> tsByShip = new LinkedHashMap<>();
        if (clientKey == null || clientKey.isBlank()) {
            return Map.of();
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        EliteLogParser parser = new EliteLogParser();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT ship_id, raw_json, ts_epoch_ms FROM " + LOADOUTS_TABLE
                                + " WHERE client_key = ? ORDER BY ship_id ASC")) {
                    ps.setString(1, clientKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long shipId = rs.getLong(1);
                            String raw = rs.getString(2);
                            if (raw == null || raw.isBlank()) {
                                continue;
                            }
                            rawByShip.put(Long.valueOf(shipId), raw);
                            tsByShip.put(Long.valueOf(shipId), Instant.ofEpochMilli(rs.getLong(3)));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering loadout list failed: " + ex.getMessage());
            return Map.of();
        }
        // Overlay EngineerCraft updates that landed after the last stored Loadout (Elite often
        // omits Loadout for grade rolls and experimental-only applies).
        for (EngineeringCraftRecord craftRec : listCrafts(clientKey)) {
            Instant loadoutTs = tsByShip.get(Long.valueOf(craftRec.getShipId()));
            String raw = rawByShip.get(Long.valueOf(craftRec.getShipId()));
            if (raw == null
                    || !EngineeringLoadoutExperimentalPatch.craftShouldOverlayLoadout(
                            craftRec.getTimestamp(),
                            loadoutTs,
                            craftRec.getShipId(),
                            craftRec.getShipId())) {
                continue;
            }
            try {
                EliteLogEvent parsed = parser.parseRecord(craftRec.getRawJson());
                if (!(parsed instanceof EngineerCraftEvent craft)
                        || !EngineeringLoadoutExperimentalPatch.shouldPatchLoadout(craft)) {
                    continue;
                }
                String patched = EngineeringLoadoutExperimentalPatch.patchLoadoutRawJson(raw, craft);
                if (patched != null) {
                    rawByShip.put(Long.valueOf(craftRec.getShipId()), patched);
                    Instant craftTs = craftRec.getTimestamp() != null
                            ? craftRec.getTimestamp()
                            : Instant.EPOCH;
                    Instant prev = loadoutTs != null ? loadoutTs : Instant.EPOCH;
                    tsByShip.put(Long.valueOf(craftRec.getShipId()),
                            craftTs.isAfter(prev) ? craftTs : prev);
                }
            } catch (Exception ignored) {
                // skip corrupt craft
            }
        }
        Map<Long, LoadoutEvent> out = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : rawByShip.entrySet()) {
            try {
                EliteLogEvent event = parser.parseRecord(e.getValue());
                if (event instanceof LoadoutEvent loadout) {
                    out.put(e.getKey(), loadout);
                }
            } catch (Exception ignored) {
                // skip corrupt row
            }
        }
        return out;
    }

    public static boolean hasCrafts(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return false;
        }
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM " + CRAFTS_TABLE + " WHERE client_key = ? LIMIT 1")) {
                    ps.setString(1, clientKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private static void upsertCraft(EngineeringCraftRecord craft) {
        Path dbPath = SystemCache.getSqliteCacheDbPath();
        try {
            ensureParentDir(dbPath);
            try (Connection c = open(dbPath)) {
                ensureTables(c);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + CRAFTS_TABLE + " ("
                                + "client_key, ts_epoch_ms, ship_id, slot, module, blueprint_name, "
                                + "level, quality, raw_json) VALUES (?,?,?,?,?,?,?,?,?) "
                                + "ON CONFLICT(client_key, ts_epoch_ms, ship_id, slot, module, "
                                + "blueprint_name, level) DO UPDATE SET "
                                + "quality = excluded.quality, raw_json = excluded.raw_json")) {
                    bindCraft(ps, craft);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            System.err.println("[EDO] Engineering craft store failed: " + ex.getMessage());
        }
    }

    private static void bindCraft(PreparedStatement ps, EngineeringCraftRecord craft) throws SQLException {
        ps.setString(1, craft.getClientKey());
        ps.setLong(2, craft.getTimestamp().toEpochMilli());
        ps.setLong(3, craft.getShipId());
        ps.setString(4, craft.getSlot());
        ps.setString(5, craft.getModule());
        ps.setString(6, craft.getBlueprintName());
        ps.setInt(7, craft.getLevel());
        ps.setDouble(8, craft.getQuality());
        ps.setString(9, craft.getRawJson());
    }

    private static String rawJsonString(EliteLogEvent event) {
        if (event == null) {
            return null;
        }
        JsonObject raw = event.getRawJson();
        return raw != null ? raw.toString() : null;
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

    private static void ensureTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + CRAFTS_TABLE + " ("
                            + "client_key TEXT NOT NULL,"
                            + "ts_epoch_ms INTEGER NOT NULL,"
                            + "ship_id INTEGER NOT NULL,"
                            + "slot TEXT NOT NULL,"
                            + "module TEXT NOT NULL,"
                            + "blueprint_name TEXT NOT NULL,"
                            + "level INTEGER NOT NULL,"
                            + "quality REAL,"
                            + "raw_json TEXT NOT NULL,"
                            + "PRIMARY KEY (client_key, ts_epoch_ms, ship_id, slot, module, "
                            + "blueprint_name, level)"
                            + ")");
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + LOADOUTS_TABLE + " ("
                            + "client_key TEXT NOT NULL,"
                            + "ship_id INTEGER NOT NULL,"
                            + "ts_epoch_ms INTEGER NOT NULL,"
                            + "raw_json TEXT NOT NULL,"
                            + "PRIMARY KEY (client_key, ship_id)"
                            + ")");
            st.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_engineering_crafts_ship "
                            + "ON " + CRAFTS_TABLE + " (client_key, ship_id, ts_epoch_ms)");
            st.executeUpdate("PRAGMA journal_mode=WAL");
        }
    }

    private record LoadoutSnapshot(long shipId, Instant timestamp, String rawJson) {
    }
}
