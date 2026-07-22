package org.dce.ed.ui.tabdock;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.dce.ed.EliteOverlayTabbedPane;
import org.dce.ed.MouseInteractionMode;
import org.dce.ed.OverlayFrame;
import org.dce.ed.ui.tabdock.OverlayTabTransferable.OverlayTabTransferData;

/**
 * Chrome-style detachable tabs: drag a tab off the main overlay into a floating window,
 * or onto another window's tab strip to move it. Layout is persisted between runs.
 */
public final class TabDockingController {

    private final EliteOverlayTabbedPane tabbedPane;
    private final Supplier<Window> mainWindowSupplier;
    private final Map<String, FloatingTabFrame> floats = new LinkedHashMap<>();
    private final Map<String, String> tabToDock = new LinkedHashMap<>();
    private final Timer persistDebounce;

    private boolean restoring;
    private boolean dragEndedWithoutDrop;
    /** Set once this controller is retired; a disposed controller must never persist again. */
    private boolean disposed;
    /**
     * True once the saved layout has been loaded (or the user changed the layout). A freshly built
     * controller starts with every tab on main; persisting that before {@link #restoreSavedLayout()}
     * runs (e.g. two theme rebuilds back to back) silently wiped the user's float layout.
     */
    private boolean layoutSynced;

    public TabDockingController(EliteOverlayTabbedPane tabbedPane, Supplier<Window> mainWindowSupplier) {
        this.tabbedPane = Objects.requireNonNull(tabbedPane, "tabbedPane");
        this.mainWindowSupplier = Objects.requireNonNull(mainWindowSupplier, "mainWindowSupplier");
        this.persistDebounce = new Timer(400, e -> persistNow());
        this.persistDebounce.setRepeats(false);

        for (OverlayTabId id : OverlayTabId.values()) {
            tabToDock.put(id.cardName(), OverlayTabId.MAIN_DOCK_ID);
        }

        tabbedPane.setTabDockingController(this);
        installDragAndDropOnHost(tabbedPane);
        for (OverlayTabId id : OverlayTabId.values()) {
            JButton button = tabbedPane.getTabButton(id.cardName());
            if (button != null) {
                installTabDrag(button, id.cardName(), OverlayTabId.MAIN_DOCK_ID);
            }
        }
    }

    public void restoreSavedLayout() {
        restoring = true;
        try {
            closeAllFloatsReturningTabs(false);
            TabLayoutState state = TabLayoutPreferences.load();
            state.normalize();

            // Ensure main strip order matches saved main tabs first.
            for (String card : new ArrayList<>(state.mainTabs)) {
                moveTab(card, OverlayTabId.MAIN_DOCK_ID, -1, false);
            }

            for (TabLayoutState.FloatDockState f : state.floats) {
                FloatingTabFrame frame = ensureFloat(f.id);
                frame.setBounds(f.x, f.y, f.width, f.height);
                frame.setAlwaysOnTop(f.alwaysOnTop);
                frame.setMouseInteractionMode(f.mouseInteractionMode());
                for (String card : f.tabs) {
                    moveTab(card, f.id, -1, false);
                }
                if (f.selected != null) {
                    selectInDock(f.id, f.selected);
                }
                frame.setVisible(true);
                updateFloatTitle(frame);
            }

            if (state.mainSelected != null) {
                selectInDock(OverlayTabId.MAIN_DOCK_ID, state.mainSelected);
            }
            tabbedPane.onDockTabsChanged();
        } finally {
            restoring = false;
            layoutSynced = true;
        }
        persistNow();
    }

    public void disposeAll() {
        persistDebounce.stop();
        persistNow();
        disposed = true;
        for (FloatingTabFrame frame : new ArrayList<>(floats.values())) {
            frame.setVisible(false);
            frame.dispose();
        }
        floats.clear();
    }

    /** Select a tab wherever it currently lives (main or float). */
    public boolean selectTabWherever(String cardName) {
        String dockId = tabToDock.get(cardName);
        if (dockId == null) {
            return false;
        }
        return selectInDock(dockId, cardName);
    }

    public String dockIdForTab(String cardName) {
        return tabToDock.getOrDefault(cardName, OverlayTabId.MAIN_DOCK_ID);
    }

    public boolean isOnMain(String cardName) {
        return OverlayTabId.MAIN_DOCK_ID.equals(dockIdForTab(cardName));
    }

    void schedulePersist() {
        if (restoring || disposed) {
            return;
        }
        persistDebounce.restart();
    }

    private void persistNow() {
        // Never save before the saved layout has been applied to this controller (a fresh
        // controller holds the default all-on-main layout) and never after retirement (a stale
        // debounce firing post-rebuild captured a torn-down pane). Both overwrote the user's
        // float layout with "everything on main".
        if (restoring || disposed || !layoutSynced) {
            return;
        }
        TabLayoutPreferences.save(captureState());
    }

    TabLayoutState captureState() {
        TabLayoutState state = new TabLayoutState();
        for (Component c : tabbedPane.getTabStrip().getComponents()) {
            if (c instanceof JButton button && button.isVisible()) {
                String card = tabbedPane.cardNameForTabButton(button);
                if (card != null) {
                    state.mainTabs.add(card);
                }
            }
        }
        // Include main-dock tabs that are preference-hidden but still owned by main.
        for (Map.Entry<String, String> e : tabToDock.entrySet()) {
            if (OverlayTabId.MAIN_DOCK_ID.equals(e.getValue()) && !state.mainTabs.contains(e.getKey())) {
                state.mainTabs.add(e.getKey());
            }
        }
        state.mainSelected = tabbedPane.getVisibleCardName();

        for (FloatingTabFrame frame : floats.values()) {
            TabLayoutState.FloatDockState f = new TabLayoutState.FloatDockState(frame.getDockId());
            for (Component c : frame.getTabStrip().getComponents()) {
                if (c instanceof JButton button) {
                    String card = tabbedPane.cardNameForTabButton(button);
                    if (card != null) {
                        f.tabs.add(card);
                    }
                }
            }
            if (f.tabs.isEmpty()) {
                continue;
            }
            f.selected = frame.getSelectedCardName();
            if (f.selected == null || !f.tabs.contains(f.selected)) {
                f.selected = f.tabs.get(0);
                for (String card : f.tabs) {
                    if (buttonSelected(tabbedPane.getTabButton(card))) {
                        f.selected = card;
                        break;
                    }
                }
            }
            Rectangle b = frame.getBounds();
            f.x = b.x;
            f.y = b.y;
            f.width = b.width;
            f.height = b.height;
            f.alwaysOnTop = frame.isAlwaysOnTop();
            f.setMouseInteractionMode(frame.getMouseInteractionMode());
            state.floats.add(f);
        }
        state.normalize();
        return state;
    }

    private static boolean buttonSelected(JButton button) {
        return button != null && button.isSelected();
    }

    private void installDragAndDropOnHost(TabDockHost host) {
        JComponent strip = host.getTabStrip();
        new DropTarget(strip, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!dtde.isDataFlavorSupported(OverlayTabTransferable.TAB_FLAVOR)) {
                        dtde.rejectDrop();
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE);
                    OverlayTabTransferData data = (OverlayTabTransferData) dtde.getTransferable()
                            .getTransferData(OverlayTabTransferable.TAB_FLAVOR);
                    int insertIndex = insertionIndex(strip, dtde.getLocation());
                    moveTab(data.cardName(), host.getDockId(), insertIndex, true);
                    dtde.dropComplete(true);
                    dragEndedWithoutDrop = false;
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    private void installTabDrag(JButton button, String cardName, String dockIdHint) {
        DragSource ds = DragSource.getDefaultDragSource();
        DragGestureListener gesture = (DragGestureEvent dge) -> {
            String sourceDock = tabToDock.getOrDefault(cardName, OverlayTabId.MAIN_DOCK_ID);
            dragEndedWithoutDrop = true;
            Transferable t = new OverlayTabTransferable(new OverlayTabTransferData(cardName, sourceDock));
            dge.startDrag(null, t, new DragSourceAdapter() {
                @Override
                public void dragDropEnd(DragSourceDropEvent dsde) {
                    if (dsde.getDropSuccess()) {
                        dragEndedWithoutDrop = false;
                        return;
                    }
                    if (!dragEndedWithoutDrop) {
                        return;
                    }
                    // Dropped outside any tab strip → detach into a new floating window.
                    Point screen = dsde.getLocation();
                    if (screen != null && !isOverAnyDockStrip(screen)) {
                        detachToNewFloat(cardName, screen);
                    }
                }
            });
        };
        ds.createDefaultDragGestureRecognizer(button, DnDConstants.ACTION_MOVE, gesture);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                button.putClientProperty("edo.dockId", tabToDock.get(cardName));
            }
        });
        button.putClientProperty("edo.cardName", cardName);
        button.putClientProperty("edo.dockId", dockIdHint);
        button.putClientProperty("edo.dragInstalled", Boolean.TRUE);
    }

    private void ensureTabDragInstalled(JButton button, String cardName) {
        if (button == null) {
            return;
        }
        button.putClientProperty("edo.cardName", cardName);
        button.putClientProperty("edo.dockId", tabToDock.get(cardName));
        if (Boolean.TRUE.equals(button.getClientProperty("edo.dragInstalled"))) {
            return;
        }
        installTabDrag(button, cardName, tabToDock.getOrDefault(cardName, OverlayTabId.MAIN_DOCK_ID));
    }

    private boolean isOverAnyDockStrip(Point screen) {
        if (isPointOverStrip(tabbedPane.getTabStrip(), screen)) {
            return true;
        }
        for (FloatingTabFrame frame : floats.values()) {
            if (isPointOverStrip(frame.getTabStrip(), screen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPointOverStrip(JComponent strip, Point screen) {
        if (strip == null || !strip.isShowing()) {
            return false;
        }
        Point local = new Point(screen);
        SwingUtilities.convertPointFromScreen(local, strip);
        return local.x >= 0 && local.y >= 0 && local.x < strip.getWidth() && local.y < strip.getHeight();
    }

    private static int insertionIndex(JComponent strip, Point dropLocal) {
        int index = 0;
        for (Component c : strip.getComponents()) {
            if (!(c instanceof JButton)) {
                continue;
            }
            Rectangle r = c.getBounds();
            if (dropLocal.x < r.x + r.width / 2) {
                return index;
            }
            index++;
        }
        return index;
    }

    private void detachToNewFloat(String cardName, Point screen) {
        String id = "float-" + UUID.randomUUID();
        FloatingTabFrame frame = ensureFloat(id);
        frame.setLocation(Math.max(0, screen.x - 40), Math.max(0, screen.y - 12));
        moveTab(cardName, id, -1, true);
        frame.setVisible(true);
        frame.toFront();
        updateFloatTitle(frame);
        schedulePersist();
    }

    private FloatingTabFrame ensureFloat(String dockId) {
        FloatingTabFrame existing = floats.get(dockId);
        if (existing != null) {
            return existing;
        }
        FloatingTabFrame frame = new FloatingTabFrame(dockId);
        frame.setSelectiveHitTester(tabbedPane::isPointerOverSelectiveHitForCard);
        frame.setChromeApplier(style -> applyChromeToFloatTabs(frame, style));
        frame.setOnCloseRequest(this::closeFloatReturningTabs);
        frame.setOnMovedOrResized(f -> schedulePersist());
        frame.setOnMouseModeChanged(f -> schedulePersist());
        // New floats default to hybrid (selective); restored floats override after ensureFloat.
        frame.setMouseInteractionMode(org.dce.ed.MouseInteractionMode.SELECTIVE);
        installDragAndDropOnHost(frame);
        floats.put(dockId, frame);
        return frame;
    }

    private void applyChromeToFloatTabs(FloatingTabFrame frame, FloatingTabFrame.ChromeStyle style) {
        if (frame == null || style == null) {
            return;
        }
        for (Component c : frame.getTabStrip().getComponents()) {
            if (c instanceof JButton button) {
                String card = tabbedPane.cardNameForTabButton(button);
                if (card != null) {
                    tabbedPane.applyOverlayBackgroundToCard(
                            card, style.backgroundWithAlpha(), style.treatAsTransparent());
                }
            }
        }
    }

    private void reapplyMainOverlayChrome() {
        OverlayFrame main = OverlayFrame.overlayFrame;
        if (main != null) {
            main.applyOverlayBackgroundFromPreferences(main.isPassThroughEnabled());
        }
    }

    private void closeFloatReturningTabs(FloatingTabFrame frame) {
        List<String> cards = new ArrayList<>();
        for (Component c : frame.getTabStrip().getComponents()) {
            if (c instanceof JButton button) {
                String card = tabbedPane.cardNameForTabButton(button);
                if (card != null) {
                    cards.add(card);
                }
            }
        }
        for (String card : cards) {
            moveTab(card, OverlayTabId.MAIN_DOCK_ID, -1, false);
        }
        floats.remove(frame.getDockId());
        frame.setVisible(false);
        frame.dispose();
        tabbedPane.onDockTabsChanged();
        reapplyMainOverlayChrome();
        schedulePersist();
    }

    private void closeAllFloatsReturningTabs(boolean persist) {
        for (FloatingTabFrame frame : new ArrayList<>(floats.values())) {
            closeFloatReturningTabs(frame);
        }
        if (persist) {
            schedulePersist();
        }
    }

    private void moveTab(String cardName, String targetDockId, int insertIndex, boolean select) {
        OverlayTabId id = OverlayTabId.fromCardName(cardName).orElse(null);
        if (id == null) {
            return;
        }
        String fromDock = tabToDock.getOrDefault(cardName, OverlayTabId.MAIN_DOCK_ID);
        TabDockHost from = hostFor(fromDock);
        TabDockHost to = hostFor(targetDockId);
        if (from == null || to == null) {
            return;
        }

        JButton button = tabbedPane.getTabButton(cardName);
        JComponent content = tabbedPane.getTabContent(cardName);
        if (button == null || content == null) {
            return;
        }

        if (from == to && fromDock.equals(targetDockId)) {
            // Reorder within same dock.
            JComponent strip = to.getTabStrip();
            int current = indexOfComponent(strip, button);
            strip.remove(button);
            int idx = insertIndex < 0 ? strip.getComponentCount() : Math.min(insertIndex, strip.getComponentCount());
            if (current >= 0 && current < idx) {
                idx = Math.max(0, idx - 1);
            }
            strip.add(button, idx);
            to.onDockTabsChanged();
            if (select) {
                selectInDock(targetDockId, cardName);
            }
            schedulePersist();
            return;
        }

        // Remove from source
        from.getTabStrip().remove(button);
        from.getCardPanel().remove(content);
        from.onDockTabsChanged();

        // Add to target
        JComponent strip = to.getTabStrip();
        int idx = insertIndex < 0 ? strip.getComponentCount() : Math.min(insertIndex, strip.getComponentCount());
        strip.add(button, idx);
        to.getCardPanel().add(content, cardName);
        tabToDock.put(cardName, targetDockId);
        button.putClientProperty("edo.dockId", targetDockId);
        ensureTabDragInstalled(button, cardName);
        to.onDockTabsChanged();

        if (from instanceof FloatingTabFrame floatFrom && floatFrom.getTabStrip().getComponentCount() == 0) {
            floats.remove(floatFrom.getDockId());
            floatFrom.setVisible(false);
            floatFrom.dispose();
        } else if (from instanceof FloatingTabFrame ff) {
            updateFloatTitle(ff);
        }
        if (to instanceof FloatingTabFrame tf) {
            updateFloatTitle(tf);
            if (!tf.isVisible()) {
                tf.setVisible(true);
            }
        }

        if (select) {
            boolean wasMainVisible = OverlayTabId.MAIN_DOCK_ID.equals(fromDock)
                    && cardName.equals(tabbedPane.getVisibleCardName());
            selectInDock(targetDockId, cardName);
            // Selection is per dock — give main a new highlighted tab if its selected one left.
            if (wasMainVisible && !OverlayTabId.MAIN_DOCK_ID.equals(targetDockId)) {
                tabbedPane.selectFirstVisibleTabInMain();
            }
        } else {
            // Arriving unselected: drop any stale highlight carried over from the source dock.
            if (!OverlayTabId.MAIN_DOCK_ID.equals(targetDockId)
                    || !cardName.equals(tabbedPane.getVisibleCardName())) {
                button.setSelected(false);
            }
            if (OverlayTabId.MAIN_DOCK_ID.equals(fromDock)) {
                tabbedPane.selectFirstVisibleTabInMain();
            }
        }
        if (OverlayTabId.MAIN_DOCK_ID.equals(targetDockId)) {
            reapplyMainOverlayChrome();
        }
        schedulePersist();
    }

    private TabDockHost hostFor(String dockId) {
        if (OverlayTabId.MAIN_DOCK_ID.equals(dockId)) {
            return tabbedPane;
        }
        return floats.get(dockId);
    }

    /**
     * Native wheel forwarding for floating docks (main overlay is handled separately).
     *
     * @return {@code true} if a float under the pointer consumed the wheel
     */
    public boolean handlePassThroughMouseWheelAtScreen(int screenX, int screenY, int wheelRotation) {
        if (wheelRotation == 0 || floats.isEmpty()) {
            return false;
        }
        Point screen = new Point(screenX, screenY);
        for (FloatingTabFrame frame : floats.values()) {
            if (frame == null || !frame.isShowing()) {
                continue;
            }
            Rectangle bounds = frame.getBounds();
            if (bounds == null || !bounds.contains(screenX, screenY)) {
                continue;
            }
            String card = frame.getSelectedCardName();
            if (card == null) {
                return false;
            }
            MouseInteractionMode mode = frame.getMouseInteractionMode();
            // When WS_EX_TRANSPARENT is cleared, AWT receives the wheel — skip native to avoid double scroll.
            if (mode == MouseInteractionMode.SELECTIVE
                    && tabbedPane.isPointerOverSelectiveHitForCard(card, screen)) {
                return false;
            }
            if (mode == MouseInteractionMode.FULL_PASS_THROUGH
                    && tabbedPane.isPointerOverBiologyMapForCard(card, screen)) {
                return false;
            }
            return tabbedPane.applyPassThroughWheelForCard(card, screenX, screenY, wheelRotation);
        }
        return false;
    }

    private boolean selectInDock(String dockId, String cardName) {
        TabDockHost host = hostFor(dockId);
        if (host == null) {
            return false;
        }
        JButton button = tabbedPane.getTabButton(cardName);
        if (button == null) {
            return false;
        }
        if (OverlayTabId.MAIN_DOCK_ID.equals(dockId)) {
            tabbedPane.selectTabInMain(cardName, button);
        } else {
            tabbedPane.applyTabSelectionStyles(cardName);
            host.getCardLayout().show(host.getCardPanel(), cardName);
            if (host instanceof FloatingTabFrame floatHost) {
                floatHost.setSelectedCardName(cardName);
            }
            Window w = host.getWindow();
            if (w != null) {
                w.toFront();
            }
        }
        return true;
    }

    private void updateFloatTitle(FloatingTabFrame frame) {
        List<String> labels = new ArrayList<>();
        for (Component c : frame.getTabStrip().getComponents()) {
            if (c instanceof JButton button) {
                String card = tabbedPane.cardNameForTabButton(button);
                OverlayTabId.fromCardName(card).ifPresent(id -> labels.add(id.label()));
            }
        }
        frame.setDockTitle(labels.isEmpty() ? "Detached tabs" : String.join(" · ", labels));
    }

    private static int indexOfComponent(JComponent parent, Component child) {
        Component[] comps = parent.getComponents();
        for (int i = 0; i < comps.length; i++) {
            if (comps[i] == child) {
                return i;
            }
        }
        return -1;
    }
}
