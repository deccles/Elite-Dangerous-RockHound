package org.dce.ed.tools;

import org.dce.ed.session.EdoSessionPersistence;
import org.dce.ed.session.EdoSessionState;

/**
 * Clears the persisted geo survey credits total (same field the overlay toolbar uses).
 * <p>
 * Run with the overlay closed so it does not overwrite this value on exit.
 * <p>
 * Optional: {@code --cache path} or {@code -c path} sets system property {@code edo.cacheDbFile}
 * before opening the SQLite cache (must appear before any code touches {@code SystemCache}).
 */
public final class ClearGeoSurveyCreditsMain {

    private static final String CACHE_DB_FILE_PROPERTY = "edo.cacheDbFile";

    public static void main(String[] args) {
        applyCacheArgBeforeSystemCacheLoads(args);

        EdoSessionState state = EdoSessionPersistence.load();
        Long before = state.getGeoSurveyCreditsTotal();
        state.setGeoSurveyCreditsTotal(0L);
        EdoSessionPersistence.save(state);

        System.out.println("geoSurveyCreditsTotal: "
                + (before == null ? "null" : before + " Cr") + " -> 0");
    }

    private static void applyCacheArgBeforeSystemCacheLoads(String[] args) {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--cache".equals(a) || "-c".equals(a)) {
                if (i + 1 < args.length) {
                    System.setProperty(CACHE_DB_FILE_PROPERTY, args[++i]);
                } else {
                    System.err.println("Missing path after " + a);
                    System.exit(2);
                }
                return;
            }
        }
    }

    private ClearGeoSurveyCreditsMain() {
    }
}
