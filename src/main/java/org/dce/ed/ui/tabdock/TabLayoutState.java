package org.dce.ed.ui.tabdock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dce.ed.MouseInteractionMode;

/** Serializable Chrome-style tab layout: main dock + floating docks. */
public final class TabLayoutState {

    public static final class FloatDockState {
        public String id;
        public final List<String> tabs = new ArrayList<>();
        public String selected;
        public int x;
        public int y;
        public int width = 480;
        public int height = 720;
        public boolean alwaysOnTop = true;
        /** Persisted mouse mode: normal / selective / full. Default selective (hybrid). */
        public String mouseMode = MouseInteractionMode.SELECTIVE.prefsValue();

        public FloatDockState() {
        }

        public FloatDockState(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public MouseInteractionMode mouseInteractionMode() {
            return MouseInteractionMode.fromPrefsValue(mouseMode, MouseInteractionMode.SELECTIVE);
        }

        public void setMouseInteractionMode(MouseInteractionMode mode) {
            MouseInteractionMode m = mode != null ? mode : MouseInteractionMode.SELECTIVE;
            this.mouseMode = m.prefsValue();
        }
    }

    /** Tabs hosted in the main overlay, left-to-right. */
    public final List<String> mainTabs = new ArrayList<>();
    public String mainSelected;
    public final List<FloatDockState> floats = new ArrayList<>();

    public TabLayoutState() {
    }

    public static TabLayoutState defaultAllOnMain() {
        TabLayoutState state = new TabLayoutState();
        for (OverlayTabId id : OverlayTabId.values()) {
            state.mainTabs.add(id.cardName());
        }
        state.mainSelected = OverlayTabId.SYSTEM.cardName();
        return state;
    }

    /** Ensures every known tab appears exactly once; unknown ids are dropped. */
    public void normalize() {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (OverlayTabId id : OverlayTabId.values()) {
            seen.put(id.cardName(), Boolean.FALSE);
        }

        List<String> cleanedMain = new ArrayList<>();
        for (String card : mainTabs) {
            if (seen.containsKey(card) && !seen.get(card)) {
                cleanedMain.add(card);
                seen.put(card, Boolean.TRUE);
            }
        }
        mainTabs.clear();
        mainTabs.addAll(cleanedMain);

        List<FloatDockState> cleanedFloats = new ArrayList<>();
        for (FloatDockState f : floats) {
            if (f == null || f.id == null || f.id.isBlank()) {
                continue;
            }
            List<String> tabs = new ArrayList<>();
            for (String card : f.tabs) {
                if (seen.containsKey(card) && !seen.get(card)) {
                    tabs.add(card);
                    seen.put(card, Boolean.TRUE);
                }
            }
            if (tabs.isEmpty()) {
                continue;
            }
            f.tabs.clear();
            f.tabs.addAll(tabs);
            if (f.selected == null || !f.tabs.contains(f.selected)) {
                f.selected = f.tabs.get(0);
            }
            if (f.width < 200) {
                f.width = 480;
            }
            if (f.height < 160) {
                f.height = 720;
            }
            if (f.mouseMode == null || f.mouseMode.isBlank()) {
                f.mouseMode = MouseInteractionMode.SELECTIVE.prefsValue();
            } else {
                f.setMouseInteractionMode(f.mouseInteractionMode());
            }
            cleanedFloats.add(f);
        }
        floats.clear();
        floats.addAll(cleanedFloats);

        for (Map.Entry<String, Boolean> e : seen.entrySet()) {
            if (!e.getValue()) {
                mainTabs.add(e.getKey());
            }
        }
        if (mainSelected == null || !mainTabs.contains(mainSelected)) {
            mainSelected = mainTabs.isEmpty() ? OverlayTabId.SYSTEM.cardName() : mainTabs.get(0);
        }
    }
}
