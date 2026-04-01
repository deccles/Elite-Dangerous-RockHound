package org.dce.ed.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.dce.ed.TestEnvironment;
import org.dce.ed.cache.SystemCache;
import org.dce.ed.route.RouteEntry;
import org.dce.ed.route.RouteScanStatus;
import org.dce.ed.route.RouteSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Commander session blob in SQLite: fleet carrier snapshot + exobiology total, plus one-shot legacy JSON import.
 */
class EdoSessionSqliteRoundTripTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @BeforeEach
    void resetDb() {
        SystemCache.getInstance().clearAndDeleteOnDisk();
    }

    @Test
    void saveAndLoad_roundTripExoAndFleetRoute() {
        SystemCache cache = SystemCache.getInstance();
        EdoSessionState written = new EdoSessionState();
        written.setVersion(2);
        written.setExobiologyCreditsTotalUnsold(42_000L);

        RouteSession rs = new RouteSession(null, j -> true);
        rs.applyKnownCurrentSystem("From", 100L, new double[] { 1.0, 2.0, 3.0 });
        RouteEntry e = new RouteEntry();
        e.systemName = "Next";
        e.systemAddress = 200L;
        e.status = RouteScanStatus.UNKNOWN;
        rs.replaceBaseRouteEntries(List.of(e));
        written.setFleetCarrier(FleetCarrierSessionMapper.fromRouteSession(rs));

        cache.saveEdoSessionState(written);

        EdoSessionState read = cache.loadEdoSessionState();
        assertEquals(42_000L, read.getExobiologyCreditsTotalUnsold().longValue());
        assertNotNull(read.getFleetCarrier());
        RouteSession restored = new RouteSession(null, j -> true);
        FleetCarrierSessionMapper.applyToRouteSession(restored, read.getFleetCarrier());
        assertEquals("From", restored.getCurrentSystemName());
        assertEquals(100L, restored.getCurrentSystemAddress());
        assertEquals(1, restored.getBaseRouteEntries().size());
        assertEquals("Next", restored.getBaseRouteEntries().get(0).systemName);
        assertEquals(200L, restored.getBaseRouteEntries().get(0).systemAddress);
    }

    @Test
    void legacyEdoSessionJson_importedAndRemoved() throws Exception {
        SystemCache cache = SystemCache.getInstance();
        cache.clearAndDeleteOnDisk();

        EdoSessionState legacy = new EdoSessionState();
        legacy.setCurrentSystemName("LegacyShip");
        legacy.setCarrierJumpTargetSystem("Tarantula");
        Path legacyPath = SystemCache.getLegacySessionJsonPath();
        Files.createDirectories(legacyPath.getParent());
        Files.writeString(legacyPath, new Gson().toJson(legacy));

        EdoSessionState loaded = cache.loadEdoSessionState();
        assertEquals("LegacyShip", loaded.getCurrentSystemName());
        assertEquals("Tarantula", loaded.getCarrierJumpTargetSystem());
        assertFalse(Files.isRegularFile(legacyPath), "legacy JSON should be deleted after successful migration write");
    }

    @Test
    void edoSessionPersistence_delegatesToSystemCache() {
        SystemCache cache = SystemCache.getInstance();
        EdoSessionState s = new EdoSessionState();
        s.setExobiologyCreditsTotalUnsold(99L);
        EdoSessionPersistence.save(s);
        EdoSessionState r = EdoSessionPersistence.load();
        assertEquals(99L, r.getExobiologyCreditsTotalUnsold().longValue());
    }
}
