package org.dce.ed.testutil;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dce.ed.logreader.event.ScanEvent;
import org.dce.ed.logreader.event.ScanEvent.ParentRef;

import com.google.gson.JsonObject;

/** Minimal {@link ScanEvent} builders for state/orbit regression tests. */
public final class ScanEventFixtures {

    private ScanEventFixtures() {
    }

    public static ScanEvent planetScan(int bodyId, String bodyName, String systemName, long systemAddress,
            double distanceLs, String planetClass, List<ParentRef> parents) {
        return new ScanEvent(
                Instant.EPOCH,
                new JsonObject(),
                bodyName,
                bodyId,
                systemName,
                systemAddress,
                distanceLs,
                false,
                planetClass,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                null,
                parents,
                Collections.emptyList(),
                null,
                null,
                null,
                null);
    }

    public static ScanEvent starScan(int bodyId, String bodyName, String systemName, long systemAddress,
            String starType, List<ParentRef> parents) {
        return new ScanEvent(
                Instant.EPOCH,
                new JsonObject(),
                bodyName,
                bodyId,
                systemName,
                systemAddress,
                0.0,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.emptyMap(),
                starType,
                parents,
                Collections.emptyList(),
                null,
                null,
                null,
                null);
    }
}
