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

    void mergeBodiesFromEdsm(SystemState state, BodiesResponse edsm);

    void storeSystem(SystemState state);

    void mergeDiscoveryFlags(SystemState state, Map<String, Boolean> discoveryFlagsByBodyName);

    CachedSystemSummary getSummary(long systemAddress, String systemName);
}
