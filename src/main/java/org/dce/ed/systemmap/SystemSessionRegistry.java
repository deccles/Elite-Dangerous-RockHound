package org.dce.ed.systemmap;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latest {@link SystemSession} per system name from {@link org.dce.ed.SystemTabPanel} rebuilds — used by the
 * hierarchy graph tool to avoid re-merging journal data differently.
 */
public final class SystemSessionRegistry {

    private static final ConcurrentHashMap<String, SystemSession> BY_NAME = new ConcurrentHashMap<>();

    private SystemSessionRegistry() {
    }

    public static void publish(SystemSession session) {
        if (session == null) {
            return;
        }
        String key = key(session.systemName());
        if (key.isEmpty()) {
            return;
        }
        BY_NAME.put(key, session);
    }

    public static SystemSession lookup(String systemName) {
        String key = key(systemName);
        if (key.isEmpty()) {
            return null;
        }
        return BY_NAME.get(key);
    }

    public static void clear(String systemName) {
        BY_NAME.remove(key(systemName));
    }

    private static String key(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return "";
        }
        return systemName.trim().toLowerCase(Locale.ROOT);
    }
}
