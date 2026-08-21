package org.dce.ed.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.dce.ed.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SystemCacheEdsmQueryCacheTest {

    @BeforeAll
    static void isolateCache() {
        TestEnvironment.ensureTestIsolation();
    }

    @Test
    void edsmQueryResponseRoundTripsThroughSqlite() {
        SystemCache cache = SystemCache.getInstance();
        cache.clearAndDeleteOnDisk();
        cache.putEdsmQueryCache("bodies:sol", 1234L, 200, "application/json", "{\"name\":\"Sol\"}");

        SystemCache.EdsmQueryCacheRecord loaded = cache.getEdsmQueryCache("bodies:sol");

        assertNotNull(loaded);
        assertEquals(1234L, loaded.queriedAtEpochMillis());
        assertEquals(200, loaded.statusCode());
        assertEquals("application/json", loaded.contentType());
        assertEquals("{\"name\":\"Sol\"}", loaded.body());
    }
}
