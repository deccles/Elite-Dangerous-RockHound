package org.dce.ed.session;

import org.dce.ed.cache.SystemCache;

/**
 * Commander/overlay session load/save via SQLite ({@code overlay_global_state.session_json}).
 * Legacy {@code edo-session.json} is imported once by {@link SystemCache#loadEdoSessionState()}.
 */
public final class EdoSessionPersistence {

    private EdoSessionPersistence() {
    }

    public static EdoSessionState load() {
        return SystemCache.getInstance().loadEdoSessionState();
    }

    public static void save(EdoSessionState state) {
        SystemCache.getInstance().saveEdoSessionState(state);
    }
}
