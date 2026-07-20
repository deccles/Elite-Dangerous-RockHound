package org.dce.ed.ui.tabdock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TabLayoutStateTest {

    @Test
    void normalizePutsMissingTabsOnMainAndDropsDuplicates() {
        TabLayoutState state = new TabLayoutState();
        state.mainTabs.add("ROUTE");
        state.mainTabs.add("ROUTE");
        TabLayoutState.FloatDockState f = new TabLayoutState.FloatDockState("float-1");
        f.tabs.add("SYSTEM");
        f.tabs.add("NOT_A_TAB");
        f.x = 10;
        f.y = 20;
        f.width = 100;
        f.height = 50;
        state.floats.add(f);

        state.normalize();

        assertTrue(state.mainTabs.contains("ROUTE"));
        assertFalse(state.mainTabs.contains("SYSTEM"));
        assertEquals(1, state.floats.size());
        assertEquals(java.util.List.of("SYSTEM"), state.floats.get(0).tabs);
        assertTrue(state.mainTabs.contains("MINING"));
        assertEquals(OverlayTabId.values().length,
                state.mainTabs.size() + state.floats.stream().mapToInt(d -> d.tabs.size()).sum());
    }

    @Test
    void preferencesRoundTrip() {
        TabLayoutState state = TabLayoutState.defaultAllOnMain();
        state.mainTabs.remove("ROUTE");
        state.mainTabs.remove("SYSTEM");
        TabLayoutState.FloatDockState f = new TabLayoutState.FloatDockState("float-abc");
        f.tabs.add("ROUTE");
        f.tabs.add("SYSTEM");
        f.selected = "SYSTEM";
        f.x = 100;
        f.y = 200;
        f.width = 500;
        f.height = 600;
        f.setMouseInteractionMode(org.dce.ed.MouseInteractionMode.SELECTIVE);
        state.floats.add(f);
        state.normalize();

        TabLayoutPreferences.save(state);
        TabLayoutState loaded = TabLayoutPreferences.load();
        assertEquals(1, loaded.floats.size());
        assertEquals(java.util.List.of("ROUTE", "SYSTEM"), loaded.floats.get(0).tabs);
        assertEquals("SYSTEM", loaded.floats.get(0).selected);
        assertEquals(100, loaded.floats.get(0).x);
        assertEquals(org.dce.ed.MouseInteractionMode.SELECTIVE, loaded.floats.get(0).mouseInteractionMode());
        assertFalse(loaded.mainTabs.contains("ROUTE"));

        TabLayoutPreferences.clear();
    }
}
