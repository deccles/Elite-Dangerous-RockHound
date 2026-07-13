package org.dce.ed.cache;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.dce.ed.edsm.BodiesResponse;
import org.dce.ed.state.SystemState;

/**
 * Storage abstraction for system cache backends.
 */
public interface SystemStore {
    void clearAndDeleteOnDisk();

    CachedSystem loadLastSystem() throws IOException;

    CachedSystem get(long systemAddress, String systemName);

    /**
     * Persisted cache row for the same system identity as {@code state} ({@link #get(long, String)}).
     * This is a snapshot from SQLite, not the live in-memory {@link SystemState} (which may be newer until {@link #storeSystem}).
     */
    default CachedSystem getCachedForState(SystemState state) {
        if (state == null) {
            return null;
        }
        return get(state.getSystemAddress(), state.getSystemName());
    }

    void put(long systemAddress,
            String systemName,
            double[] starPos,
            Integer totalBodies,
            Integer nonBodyCount,
            Double fssProgress,
            Boolean allBodiesFound,
            Long exobiologyCreditsTotalUnsold,
            List<CachedBody> bodies);

    void loadInto(SystemState state, CachedSystem cs);

    default void mergeBodiesFromEdsm(SystemState state, BodiesResponse edsm) {
        mergeBodiesFromEdsm(state, edsm, false);
    }

    void mergeBodiesFromEdsm(SystemState state, BodiesResponse edsm, boolean allowEdsmStandalone);

    void storeSystem(SystemState state);

    void mergeDiscoveryFlags(SystemState state, Map<String, Boolean> discoveryFlagsByBodyName);

    CachedSystemSummary getSummary(long systemAddress, String systemName);
}
