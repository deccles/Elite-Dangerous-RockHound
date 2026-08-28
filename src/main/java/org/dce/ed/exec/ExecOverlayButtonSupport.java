package org.dce.ed.exec;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.IllegalComponentStateException;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.JButton;

import org.dce.ed.OverlayFrame;
import org.dce.ed.OverlayPreferences;
import org.dce.ed.exec.placeholder.ExecArgsTokenizer;
import org.dce.ed.exec.placeholder.ExecPlaceholderId;
import org.dce.ed.exec.placeholder.ExecPlaceholderResolver;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.tabdock.OverlayTabId;

/**
 * Shared helpers for overlay Exec action buttons (Control Panel, Route strip, per-tab footers).
 */
public final class ExecOverlayButtonSupport {

    public static final int BUTTON_HOVER_DELAY_MS = 500;
    public static final int AVAILABILITY_REFRESH_MS = 250;

    private ExecOverlayButtonSupport() {
    }

    public static List<ExecBinding> loadBindingsForButtonTab(ExecTriggerService triggerService, OverlayTabId tab) {
        List<ExecBinding> result = new ArrayList<>();
        if (triggerService == null || tab == null) {
            return result;
        }
        ExecBindingsConfig config = triggerService.store().load();
        if (config == null) {
            return result;
        }
        return bindingsForButtonTab(config.getBindings(), tab);
    }

    static List<ExecBinding> bindingsForButtonTab(List<ExecBinding> bindings, OverlayTabId tab) {
        List<ExecBinding> result = new ArrayList<>();
        if (bindings == null || tab == null) {
            return result;
        }
        String card = tab.cardName();
        for (ExecBinding binding : bindings) {
            if (binding != null && card.equalsIgnoreCase(nullToEmpty(binding.getButtonTab()))) {
                result.add(binding);
            }
        }
        return result;
    }

    public static JButton createActionButton(ExecBinding binding, ExecTriggerService triggerService,
            BooleanSupplier passThroughEnabledSupplier) {
        Objects.requireNonNull(binding, "binding");
        String bindingId = binding.getId();
        Font uiFont = OverlayPreferences.getUiFont();
        JButton button = new JButton(binding.controlPanelLabel());
        OverlayOutlineButtonStyle.applyPrimaryHitSafe(button, uiFont);
        Dimension pref = button.getPreferredSize();
        int height = Math.max(28, pref.height);
        button.setPreferredSize(new Dimension(pref.width, height));
        ActionListener runAction = e -> runBindingById(triggerService, bindingId);
        button.addActionListener(runAction);
        HoverClickPoller.register(button, BUTTON_HOVER_DELAY_MS, () -> button.doClick(),
                passThroughEnabledSupplier);
        return button;
    }

    /** Enables an action only when every placeholder argument has a value for a manual launch. */
    public static void applyRequiredPlaceholderAvailability(JButton button, ExecBinding binding,
            Map<String, String> placeholderValues) {
        if (button == null || binding == null) {
            return;
        }
        Set<ExecPlaceholderId> missing = new LinkedHashSet<>();
        for (String token : ExecArgsTokenizer.tokenize(binding.getProgramArgs())) {
            ExecPlaceholderId.fromToken(token).ifPresent(id -> {
                // Manual launches always create these values at click time.
                if (id == ExecPlaceholderId.TRIGGER || id == ExecPlaceholderId.TIMESTAMP) {
                    return;
                }
                String value = placeholderValues != null ? placeholderValues.get(id.name()) : null;
                if (value == null || value.isBlank()
                        || ExecPlaceholderResolver.UNKNOWN.equalsIgnoreCase(value.trim())) {
                    missing.add(id);
                }
            });
        }
        button.setEnabled(missing.isEmpty());
        if (missing.isEmpty()) {
            button.setToolTipText(null);
            return;
        }
        StringJoiner reason = new StringJoiner("; ");
        for (ExecPlaceholderId id : missing) {
            reason.add(id.token() + " not set");
        }
        button.setToolTipText(reason.toString());
    }

    /** Resolves only symbols used by these buttons, then applies enabled state and explanations. */
    public static void refreshRequiredPlaceholderAvailability(List<JButton> buttons,
            List<ExecBinding> bindings, ExecTriggerService triggerService) {
        if (buttons == null || bindings == null) {
            return;
        }
        Set<ExecPlaceholderId> required = new LinkedHashSet<>();
        for (ExecBinding binding : bindings) {
            if (binding == null) {
                continue;
            }
            for (String token : ExecArgsTokenizer.tokenize(binding.getProgramArgs())) {
                ExecPlaceholderId.fromToken(token).ifPresent(id -> {
                    if (id != ExecPlaceholderId.TRIGGER && id != ExecPlaceholderId.TIMESTAMP) {
                        required.add(id);
                    }
                });
            }
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        if (triggerService != null) {
            for (ExecPlaceholderId id : required) {
                values.put(id.name(), ExecPlaceholderResolver.resolveOne(
                        triggerService.placeholderContext(), null, id));
            }
        }
        int count = Math.min(buttons.size(), bindings.size());
        for (int i = 0; i < count; i++) {
            applyRequiredPlaceholderAvailability(buttons.get(i), bindings.get(i), values);
        }
    }

    public static void runBindingById(ExecTriggerService triggerService, String bindingId) {
        if (triggerService == null) {
            publishExecStatus("Exec service not ready.");
            return;
        }
        triggerService.setStatusListener(ExecOverlayButtonSupport::publishExecStatus);
        triggerService.runBindingNowById(bindingId);
    }

    public static void publishExecStatus(String message) {
        OverlayFrame frame = OverlayFrame.overlayFrame;
        if (frame != null) {
            frame.setExecOverlayStatus(message);
        }
    }

    public static boolean containsScreenPoint(Component component, Point screenPoint) {
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

    public static boolean anyButtonContains(List<JButton> buttons, Point screenPoint) {
        if (buttons == null) {
            return false;
        }
        for (JButton button : buttons) {
            if (containsScreenPoint(button, screenPoint)) {
                return true;
            }
        }
        return false;
    }

    public static OverlayTabId parseButtonTab(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return OverlayTabId.fromCardName(raw.trim().toUpperCase(Locale.ROOT)).orElse(null);
    }

    public static String displayLabel(OverlayTabId tab) {
        return tab != null ? tab.label() : "None";
    }

    public static void forEachPlacementTab(Consumer<OverlayTabId> consumer) {
        for (OverlayTabId id : OverlayTabId.execButtonPlacementValues()) {
            consumer.accept(id);
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
