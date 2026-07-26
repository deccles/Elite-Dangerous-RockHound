package org.dce.ed.ui.tabdock;

import java.util.prefs.Preferences;

import org.dce.ed.EdoTestFlags;
import org.dce.ed.OverlayFrame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Persists detachable-tab layout in Java Preferences (live OS user store shared with the desktop app).
 * <p>
 * <b>Tests:</b> do not call {@link #save} or {@link #clear} — that has wiped developers' floating-tab
 * layouts. Use {@link #toJson}/{@link #parse} for round-trips. {@link #save}/{@link #clear} no-op when
 * {@link org.dce.ed.EdoTestFlags#isolateUi()} is true. See {@code .cursor/rules/junit-live-preferences.mdc}.
 */
public final class TabLayoutPreferences {

    private static final String KEY_LAYOUT_JSON = "overlay.tabLayout.json";
    private static final Preferences PREFS = Preferences.userNodeForPackage(OverlayFrame.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private TabLayoutPreferences() {
    }

    public static TabLayoutState load() {
        String raw = PREFS.get(KEY_LAYOUT_JSON, null);
        return parse(raw);
    }

    /**
     * JSON round-trip helper for unit tests (does not touch Preferences).
     */
    public static TabLayoutState parse(String raw) {
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

    /** Serialize for Preferences or in-memory round-trip tests. */
    public static String toJson(TabLayoutState state) {
        if (state == null) {
            return null;
        }
        state.normalize();
        return GSON.toJson(state);
    }

    public static void save(TabLayoutState state) {
        if (EdoTestFlags.isolateUi()) {
            return;
        }
        if (state == null) {
            PREFS.remove(KEY_LAYOUT_JSON);
            return;
        }
        PREFS.put(KEY_LAYOUT_JSON, toJson(state));
    }

    public static void clear() {
        if (EdoTestFlags.isolateUi()) {
            return;
        }
        PREFS.remove(KEY_LAYOUT_JSON);
    }
}
