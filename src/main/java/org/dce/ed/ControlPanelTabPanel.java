package org.dce.ed;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.dce.ed.exec.ExecBinding;
import org.dce.ed.exec.ExecBindingsConfig;
import org.dce.ed.exec.ExecTriggerService;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;
import org.dce.ed.ui.TransparentViewportUI;

/**
 * Control Panel overlay tab: buttons for Exec bindings marked "include on Control Panel".
 */
public final class ControlPanelTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int BUTTON_HOVER_DELAY_MS = 500;

    private final BooleanSupplier passThroughEnabledSupplier;
    private final ViewportWidthButtonPanel buttonPanel = new ViewportWidthButtonPanel();
    private final JLabel emptyLabel = new JLabel(
            "<html>No actions yet. In <b>Preferences → Exec</b>, set a <b>Name</b>, "
                    + "check <b>Control Panel</b>, and configure a program.</html>");

    private ExecTriggerService triggerService;
    private final List<JButton> actionButtons = new ArrayList<>();
    private JButton killButton;
    private JPanel killButtonRow;
    private JScrollPane buttonScroll;

    public ControlPanelTabPanel(BooleanSupplier passThroughEnabledSupplier) {
        super(new BorderLayout(0, 0));
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        setOpaque(false);
        // Top/bottom only — side insets become uncleared hybrid gutters (same as System tab).
        setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        killButton = new JButton("Kill scripts");
        killButton.setToolTipText("Stop running exec programs and cancel scheduled launches");
        killButton.addActionListener(e -> killRunningScripts());
        killButton.setEnabled(true);

        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        emptyLabel.setOpaque(false);
        emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.add(emptyLabel);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(wrapKillButtonRow());

        JScrollPane scroll = new JScrollPane(buttonPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(BorderFactory.createEmptyBorder());
        OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
        this.buttonScroll = scroll;
        add(scroll, BorderLayout.CENTER);

        styleKillButton(OverlayPreferences.getUiFont(), false);
        registerKillButtonHover();
    }

    private void registerKillButtonHover() {
        HoverClickPoller.register(
                killButton,
                BUTTON_HOVER_DELAY_MS,
                this::killRunningScripts,
                passThroughEnabledSupplier,
                () -> killButton != null
                        && killButton.isShowing()
                        && triggerService != null);
    }

    public void setExecTriggerService(ExecTriggerService service) {
        this.triggerService = service;
        if (triggerService != null) {
            triggerService.setActivityListener(this::refreshKillButtonState);
        }
        refreshButtons();
    }

    /**
     * True when the pointer is over a Control Panel action button (used to keep Win32 click-through
     * off the button hit area while mouse pass-through is enabled).
     */
    public boolean isPointerOverActionButton(Point screenPoint) {
        if (!isShowing() || screenPoint == null) {
            return false;
        }
        if (killButton != null && killButton.isShowing() && containsScreenPoint(killButton, screenPoint)) {
            return true;
        }
        for (JButton button : actionButtons) {
            if (button != null && button.isShowing() && containsScreenPoint(button, screenPoint)) {
                return true;
            }
        }
        // Also treat the kill-button row plate as interactive (FlowLayout padding around Kill).
        return killButtonRow != null && killButtonRow.isShowing()
                && containsScreenPoint(killButtonRow, screenPoint);
    }

    /**
     * Selective (hybrid) mode: punch chrome below the button stack (including Kill scripts)
     * fully transparent so the game shows through. CLEAR is only safe on the undecorated host.
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        clearNonInteractiveRegionInSelectiveMode(g);
    }

    private void clearNonInteractiveRegionInSelectiveMode(Graphics g) {
        if (g == null || !isSelectiveMouseMode() || !OverlayPreferences.isPassThroughWindowActive()) {
            return;
        }
        // Punch gaps/padding around Control Panel buttons so only the rounded hit plates remain.
        TransparentViewportUI.clearPanelChromeExceptButtons(g, this, buttonPanel);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
            int yStart = buttonStackContentBottomY();
            int yEnd = getHeight();
            if (yEnd > yStart) {
                g2.fillRect(0, yStart, getWidth(), yEnd - yStart);
            }
            // Side padding on the button stack (and any leftover host insets) must not leave a dark
            // gutter beside the punched region — same class of bug as System tab.
            clearSideGutters(g2, 0, Math.max(yStart, getHeight()));
        } finally {
            g2.dispose();
        }
    }

    private void clearSideGutters(Graphics2D g2, int yStart, int yEnd) {
        if (g2 == null || yEnd <= yStart) {
            return;
        }
        int h = yEnd - yStart;
        int w = getWidth();
        Insets hostInsets = getInsets();
        if (hostInsets != null) {
            if (hostInsets.left > 0) {
                g2.fillRect(0, yStart, hostInsets.left, h);
            }
            if (hostInsets.right > 0) {
                g2.fillRect(w - hostInsets.right, yStart, hostInsets.right, h);
            }
        }
        if (buttonPanel == null || !buttonPanel.isShowing()) {
            return;
        }
        Insets stackInsets = buttonPanel.getInsets();
        if (stackInsets == null || (stackInsets.left <= 0 && stackInsets.right <= 0)) {
            return;
        }
        Point origin = SwingUtilities.convertPoint(buttonPanel, 0, 0, this);
        int stackTop = Math.max(yStart, origin.y);
        int stackBottom = Math.min(yEnd, origin.y + buttonPanel.getHeight());
        if (stackBottom <= stackTop) {
            return;
        }
        int stackH = stackBottom - stackTop;
        if (stackInsets.left > 0) {
            g2.fillRect(origin.x, stackTop, stackInsets.left, stackH);
        }
        if (stackInsets.right > 0) {
            g2.fillRect(origin.x + buttonPanel.getWidth() - stackInsets.right, stackTop, stackInsets.right, stackH);
        }
    }

    /** Prefer this window's mouse mode (floating docks) over the main-overlay global. */
    private boolean isSelectiveMouseMode() {
        javax.swing.JRootPane rp = getRootPane();
        if (rp != null) {
            Object mode = rp.getClientProperty(OverlayPreferences.WINDOW_MOUSE_MODE_KEY);
            if (mode instanceof MouseInteractionMode m) {
                return m == MouseInteractionMode.SELECTIVE;
            }
        }
        return OverlayPreferences.getOverlayMouseInteractionMode() == MouseInteractionMode.SELECTIVE;
    }

    /** Bottom edge (in this panel's coords) of Kill scripts, else the last action / empty label. */
    private int buttonStackContentBottomY() {
        Component last = null;
        if (killButton != null && killButton.isShowing()) {
            last = killButton;
        } else if (!actionButtons.isEmpty()) {
            for (int i = actionButtons.size() - 1; i >= 0; i--) {
                JButton b = actionButtons.get(i);
                if (b != null && b.isShowing()) {
                    last = b;
                    break;
                }
            }
        }
        if (last == null && emptyLabel != null && emptyLabel.isShowing() && emptyLabel.isVisible()) {
            last = emptyLabel;
        }
        int bottom;
        if (last != null) {
            Point bottomLeft = SwingUtilities.convertPoint(last, 0, last.getHeight(), this);
            bottom = Math.max(0, bottomLeft.y);
        } else {
            Insets in = getInsets();
            bottom = in != null ? Math.max(0, in.top) : 0;
        }
        if (buttonScroll != null && buttonScroll.isShowing()) {
            Point scrollBottom = SwingUtilities.convertPoint(buttonScroll, 0, buttonScroll.getHeight(), this);
            bottom = Math.min(bottom, Math.max(0, scrollBottom.y));
        }
        return bottom;
    }

    public void refreshButtons() {
        SwingUtilities.invokeLater(this::rebuildButtons);
    }

    private void refreshKillButtonState() {
        if (killButton == null) {
            return;
        }
        boolean active = triggerService != null && triggerService.hasActiveScripts();
        // Always leave the control clickable; red ink only when something is actually running.
        killButton.setEnabled(true);
        styleKillButton(OverlayPreferences.getUiFont(), active);
    }

    private void rebuildButtons() {
        actionButtons.clear();
        buttonPanel.removeAll();
        buttonPanel.add(emptyLabel);

        Font uiFont = OverlayPreferences.getUiFont();
        List<ExecBinding> bindings = loadControlPanelBindings();
        emptyLabel.setVisible(bindings.isEmpty());

        for (ExecBinding binding : bindings) {
            String bindingId = binding.getId();
            JButton button = new JButton(binding.controlPanelLabel());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            OverlayOutlineButtonStyle.applyPrimaryHitSafe(button, uiFont);
            Dimension pref = button.getPreferredSize();
            int height = Math.max(28, pref.height);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            button.setPreferredSize(new Dimension(pref.width, height));

            ActionListener runAction = e -> runBindingById(bindingId);
            button.addActionListener(runAction);
            HoverClickPoller.register(button, BUTTON_HOVER_DELAY_MS, () -> button.doClick(),
                    passThroughEnabledSupplier);

            actionButtons.add(button);
            buttonPanel.add(button);
            buttonPanel.add(Box.createVerticalStrut(6));
        }

        if (bindings.isEmpty()) {
            buttonPanel.add(Box.createVerticalStrut(6));
        }
        buttonPanel.add(wrapKillButtonRow());

        buttonPanel.revalidate();
        buttonPanel.repaint();
        refreshKillButtonState();
        revalidate();
        repaint();
    }

    /** Right-aligned row so Kill scripts keeps its natural width (not stretched by BoxLayout). */
    private JPanel wrapKillButtonRow() {
        if (killButtonRow == null) {
            killButtonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            killButtonRow.setOpaque(false);
            killButtonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            killButtonRow.add(killButton);
        }
        killButtonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, killButtonRow.getPreferredSize().height));
        return killButtonRow;
    }

    private List<ExecBinding> loadControlPanelBindings() {
        List<ExecBinding> result = new ArrayList<>();
        ExecBindingsConfig config = null;
        if (triggerService != null) {
            config = triggerService.store().load();
        }
        if (config == null) {
            return result;
        }
        for (ExecBinding binding : config.getBindings()) {
            if (binding != null && binding.isIncludeOnControlPanel()) {
                result.add(binding);
            }
        }
        return result;
    }

    private void killRunningScripts() {
        if (triggerService == null) {
            publishExecStatus("Exec service not ready.");
            return;
        }
        triggerService.setStatusListener(this::publishExecStatus);
        triggerService.killRunningScripts();
    }

    private void styleKillButton(Font uiFont, boolean active) {
        if (killButton == null) {
            return;
        }
        OverlayOutlineButtonStyle.applyDangerHitSafe(killButton, uiFont, active);
        Dimension pref = killButton.getPreferredSize();
        int height = Math.max(28, pref.height);
        killButton.setPreferredSize(new Dimension(pref.width, height));
        if (killButtonRow != null) {
            killButtonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, killButtonRow.getPreferredSize().height));
        }
    }

    private void runBindingById(String bindingId) {
        if (triggerService == null) {
            publishExecStatus("Exec service not ready.");
            return;
        }
        triggerService.setStatusListener(this::publishExecStatus);
        triggerService.runBindingNowById(bindingId);
    }

    private void publishExecStatus(String message) {
        OverlayFrame frame = OverlayFrame.overlayFrame;
        if (frame != null) {
            frame.setExecOverlayStatus(message);
        }
    }

    private static boolean containsScreenPoint(Component component, Point screenPoint) {
        if (component == null || !component.isShowing() || screenPoint == null) {
            return false;
        }
        try {
            Point origin = component.getLocationOnScreen();
            return new Rectangle(origin.x, origin.y, component.getWidth(), component.getHeight())
                    .contains(screenPoint);
        } catch (IllegalComponentStateException ex) {
            return false;
        }
    }

    public void applyOverlayBackground(Color bgWithAlpha, boolean treatAsTransparent) {
        boolean opaque = !treatAsTransparent;
        setOpaque(opaque);
        if (opaque && bgWithAlpha != null) {
            setBackground(bgWithAlpha);
        }
        Font uiFont = OverlayPreferences.getUiFont();
        for (JButton button : actionButtons) {
            OverlayOutlineButtonStyle.applyPrimaryHitSafe(button, uiFont);
        }
        refreshKillButtonState();
        revalidate();
        repaint();
    }

    public void applyUiFont(Font font) {
        if (font == null) {
            return;
        }
        setFont(font);
        emptyLabel.setFont(font);
        for (JButton button : actionButtons) {
            OverlayOutlineButtonStyle.applyPrimaryHitSafe(button, font);
        }
        refreshKillButtonState();
        revalidate();
        repaint();
    }

    public void applyUiFontPreferences() {
        applyUiFont(OverlayPreferences.getUiFont());
    }

    /**
     * Button stack that stretches to the scroll viewport width so action plates are full-bleed
     * (avoids narrow preferred-width columns with dark hybrid gutters on each side).
     */
    private static final class ViewportWidthButtonPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(16, visibleRect.height - 16)
                    : Math.max(16, visibleRect.width - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
