package org.dce.ed.exec;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.dce.ed.OverlayPreferences;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.tabdock.OverlayTabId;

/**
 * Right-aligned footer strip of Exec action buttons for overlay tabs that lack a dedicated strip.
 */
public final class ExecTabButtonStrip extends JPanel {

    private static final long serialVersionUID = 1L;

    private final OverlayTabId tabId;
    private final BooleanSupplier passThroughEnabledSupplier;
    private final List<JButton> actionButtons = new ArrayList<>();
    private ExecTriggerService triggerService;

    public ExecTabButtonStrip(OverlayTabId tabId, BooleanSupplier passThroughEnabledSupplier) {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        this.tabId = tabId;
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setVisible(false);
    }

    public void setExecTriggerService(ExecTriggerService service) {
        if (this.triggerService != null) {
            this.triggerService.removeBindingsChangedListener(this::refreshButtons);
        }
        this.triggerService = service;
        if (service != null) {
            service.addBindingsChangedListener(this::refreshButtons);
        }
        refreshButtons();
    }

    public void refreshButtons() {
        SwingUtilities.invokeLater(this::rebuildButtons);
    }

    public boolean isPointerOverActionButton(Point screenPoint) {
        return ExecOverlayButtonSupport.anyButtonContains(actionButtons, screenPoint);
    }

    public List<JButton> actionButtons() {
        return actionButtons;
    }

    public void applyUiFont(Font font) {
        Font uiFont = font != null ? font : OverlayPreferences.getUiFont();
        for (JButton button : actionButtons) {
            OverlayOutlineButtonStyle.applyPrimaryHitSafe(button, uiFont);
        }
    }

    private void rebuildButtons() {
        actionButtons.clear();
        removeAll();
        List<ExecBinding> bindings = ExecOverlayButtonSupport.loadBindingsForButtonTab(triggerService, tabId);
        for (ExecBinding binding : bindings) {
            JButton button = ExecOverlayButtonSupport.createActionButton(binding, triggerService,
                    passThroughEnabledSupplier);
            actionButtons.add(button);
            add(button);
        }
        setVisible(!actionButtons.isEmpty());
        Dimension pref = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(0, pref.height)));
        revalidate();
        repaint();
        // Ensure parent BorderLayout.SOUTH collapses when empty.
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    /** Convenience: wrap strip for BorderLayout.SOUTH attachment. */
    public static JPanel wrapSouth(ExecTabButtonStrip strip) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(strip, BorderLayout.EAST);
        return wrap;
    }
}
