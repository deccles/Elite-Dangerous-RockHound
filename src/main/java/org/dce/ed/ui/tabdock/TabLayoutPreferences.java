package org.dce.ed.ui.tabdock;

import java.util.prefs.Preferences;

import org.dce.ed.OverlayFrame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/** Persists detachable-tab layout in Java Preferences. */
public final class TabLayoutPreferences {

    private static final String KEY_LAYOUT_JSON = "overlay.tabLayout.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(OverlayFrame.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private TabLayoutPreferences() {
    }

    public static TabLayoutState load() {
        String raw = PREFS.get(KEY_LAYOUT_JSON, null);
        if (raw == null || raw.isBlank()) {
            return TabLayoutState.defaultAllOnMain();
        }
        try {
            TabLayoutState state = GSON.fromJson(raw, TabLayoutState.class);
            if (state == null) {
                return TabLayoutState.defaultAllOnMain();
            }
            state.normalize();
            return state;
        } catch (JsonSyntaxException | IllegalStateException ex) {
            return TabLayoutState.defaultAllOnMain();
        }
    }

    public static void save(TabLayoutState state) {
        if (state == null) {
            PREFS.remove(KEY_LAYOUT_JSON);
            return;
        }
        state.normalize();
        PREFS.put(KEY_LAYOUT_JSON, GSON.toJson(state));
    }

    public static void clear() {
        PREFS.remove(KEY_LAYOUT_JSON);
    }
}
