package org.dce.ed.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dce.ed.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpanshBodyExobiologySqliteStoreTest {

    @BeforeAll
    static void isolate() {
        TestEnvironment.ensureTestIsolation();
    }

    @Test
    void saveAndLoad_roundTrip() {
        SpanshBodyExobiologyInfo original = new SpanshBodyExobiologyInfo(
                List.of(new SpanshLandmark("Biological", "Foo", -12.5, 88.0)),
                true);
        SpanshBodyExobiologySqliteStore.save("Juenae BC-B d1-7935", "4 b", original);
        SpanshBodyExobiologyInfo loaded = SpanshBodyExobiologySqliteStore.load("Juenae BC-B d1-7935", "4 b");
        assertNotNull(loaded);
        assertTrue(loaded.isExcludeFromExobiology());
        assertEquals(1, loaded.getLandmarks().size());
        assertEquals("Biological", loaded.getLandmarks().get(0).getType());
        assertEquals("Foo", loaded.getLandmarks().get(0).getSubtype());
        assertEquals(-12.5, loaded.getLandmarks().get(0).getLatitude(), 1e-9);
        assertEquals(88.0, loaded.getLandmarks().get(0).getLongitude(), 1e-9);
    }

    @Test
    void upsert_updatesRow() {
        SpanshBodyExobiologySqliteStore.save("S", "B",
                new SpanshBodyExobiologyInfo(List.of(), true));
        SpanshBodyExobiologySqliteStore.save("S", "B",
                new SpanshBodyExobiologyInfo(List.of(new SpanshLandmark("x", "y", 0, 0)), false));
        SpanshBodyExobiologyInfo loaded = SpanshBodyExobiologySqliteStore.load("S", "B");
        assertNotNull(loaded);
        assertFalse(loaded.isExcludeFromExobiology());
        assertEquals(1, loaded.getLandmarks().size());
    }
}
