package org.dce.ed.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.BorderUIResource;

import org.dce.ed.OverlayPreferences;

/**
 * EDO theme applied on top of the platform Look&amp;Feel (does not replace Windows L&amp;F).
 * Installs UIDefaults colors/fonts/borders and registers default UI delegates that are safe for
 * the default {@link EdoSurface#DIALOG} surface. Overlay transparency remains opt-in via
 * {@link EdoSurface#OVERLAY} + existing chrome helpers.
 */
public final class EdoLookAndFeel {

    /** Client property: {@code primary}, {@code chip}, {@code danger}, or unset for platform button. */
    public static final String BUTTON_ROLE_KEY = "edo.buttonRole";
    public static final String BUTTON_ROLE_PRIMARY = "primary";
    public static final String BUTTON_ROLE_CHIP = "chip";
    public static final String BUTTON_ROLE_DANGER = "danger";

    /** Client property: {@link Boolean#TRUE} when the button needs an opaque Win32 hit plate. */
    public static final String HIT_SAFE_KEY = "edo.hitSafe";

    /** Compact outline padding (~Combat grid). */
    public static final String BUTTON_COMPACT_KEY = "edo.buttonCompact";

    private static boolean installed;

    private EdoLookAndFeel() {
    }

    /**
     * Applies theme tokens into UIManager and registers default UI class names.
     * Safe to call repeatedly (e.g. after prefs theme change).
     */
    public static void install() {
        applyDefaults();
        registerUiDelegates();
        installed = true;
    }

    /** {@code true} after at least one successful {@link #install()}. */
    public static boolean isInstalled() {
        return installed;
    }

    /**
     * Re-read {@link EdoUi} / font prefs into UIDefaults after theme preference changes.
     * Does not call {@code updateComponentTreeUI} — callers decide which roots to refresh.
     */
    public static void refreshFromPreferences() {
        applyDefaults();
    }

    private static void applyDefaults() {
        Color bg = EdoUi.User.BACKGROUND;
        Color fg = EdoUi.User.MAIN_TEXT;
        Color panel = EdoUi.User.PANEL_BG;
        Color selectionBg = EdoUi.ED_ORANGE_LESS_TRANS;
        Color tipFg = EdoUi.Internal.MENU_FG_LIGHT;
        Font uiFont = OverlayPreferences.getUiFont();
        FontUIResource fontRes = uiFont != null ? new FontUIResource(uiFont) : null;

        putColor("Panel.background", bg);
        putColor("Panel.foreground", fg);
        putColor("Viewport.background", bg);
        putColor("ScrollPane.background", bg);
        putColor("ScrollPane.foreground", fg);
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("Table.scrollPaneBorder", new BorderUIResource(BorderFactory.createEmptyBorder()));

        putColor("Table.background", bg);
        putColor("Table.foreground", fg);
        putColor("Table.selectionBackground", fg);
        putColor("Table.selectionForeground", bg);
        putColor("Table.gridColor", EdoUi.Internal.separatorLine());
        putColor("TableHeader.background", bg);
        putColor("TableHeader.foreground", fg);

        putColor("Label.background", bg);
        putColor("Label.foreground", fg);
        putColor("CheckBox.background", bg);
        putColor("CheckBox.foreground", fg);
        putColor("RadioButton.background", bg);
        putColor("RadioButton.foreground", fg);
        putColor("Button.background", panel);
        putColor("Button.foreground", fg);
        putColor("ToggleButton.background", panel);
        putColor("ToggleButton.foreground", fg);

        putColor("TextField.background", panel);
        putColor("TextField.foreground", fg);
        putColor("TextField.caretForeground", fg);
        putColor("TextField.selectionBackground", selectionBg);
        putColor("TextField.selectionForeground", fg);
        putColor("TextField.inactiveBackground", panel);
        putColor("TextField.inactiveForeground", fg);
        UIManager.put("TextField.border", fieldBorder());

        putColor("TextArea.background", panel);
        putColor("TextArea.foreground", fg);
        putColor("TextArea.caretForeground", fg);
        putColor("TextArea.selectionBackground", selectionBg);
        putColor("TextArea.selectionForeground", fg);
        putColor("TextArea.inactiveBackground", panel);
        putColor("TextArea.inactiveForeground", fg);
        UIManager.put("TextArea.border", fieldBorder());

        putColor("ComboBox.background", panel);
        putColor("ComboBox.foreground", fg);
        putColor("ComboBox.selectionBackground", selectionBg);
        putColor("ComboBox.selectionForeground", fg);
        putColor("ComboBox.disabledBackground", panel);
        putColor("ComboBox.disabledForeground", fg);

        putColor("Spinner.background", panel);
        putColor("Spinner.foreground", fg);

        putColor("List.background", bg);
        putColor("List.foreground", fg);
        putColor("List.selectionBackground", fg);
        putColor("List.selectionForeground", bg);

        putColor("Tree.background", bg);
        putColor("Tree.foreground", fg);
        putColor("Tree.selectionBackground", fg);
        putColor("Tree.selectionForeground", bg);
        putColor("Tree.textBackground", bg);
        putColor("Tree.textForeground", fg);

        putColor("Menu.background", EdoUi.Internal.DARK_14);
        putColor("Menu.foreground", tipFg);
        putColor("MenuItem.background", EdoUi.Internal.DARK_14);
        putColor("MenuItem.foreground", tipFg);
        putColor("MenuBar.background", bg);
        putColor("MenuBar.foreground", fg);
        putColor("PopupMenu.background", EdoUi.Internal.DARK_14);
        putColor("PopupMenu.foreground", tipFg);

        putColor("ToolTip.background", panel);
        putColor("ToolTip.foreground", tipFg);
        UIManager.put("ToolTip.border", new BorderUIResource(new EmptyBorder(4, 8, 4, 8)));

        putColor("OptionPane.background", bg);
        putColor("OptionPane.foreground", fg);
        putColor("OptionPane.messageForeground", fg);

        putColor("TitledBorder.titleColor", fg);
        putColor("Separator.foreground", EdoUi.Internal.separatorLine());
        putColor("Separator.background", bg);

        putColor("TabbedPane.background", bg);
        putColor("TabbedPane.foreground", fg);
        putColor("TabbedPane.contentAreaColor", bg);
        putColor("TabbedPane.selected", panel);
        putColor("TabbedPane.highlight", panel);
        putColor("TabbedPane.light", panel);
        putColor("TabbedPane.focus", fg);
        putColor("TabbedPane.darkShadow", EdoUi.Internal.separatorLine());

        putColor("ScrollBar.background", bg);
        putColor("ScrollBar.foreground", fg);
        putColor("ScrollBar.thumb", EdoUi.withAlpha(fg, 72));
        putColor("ScrollBar.track", EdoUi.Internal.TRANSPARENT);

        putColor("Slider.background", bg);
        putColor("Slider.foreground", fg);
        putColor("Slider.tickColor", fg);

        putColor("SplitPane.background", bg);
        putColor("SplitPaneDivider.draggingColor", EdoUi.Internal.MAIN_TEXT_ALPHA_140);

        UIManager.put("Button.margin", new InsetsUIResource(2, 8, 2, 8));
        UIManager.put("CheckBox.icon", OverlayCheckBoxStyle.unselectedIcon());
        UIManager.put("CheckBox.selectedIcon", OverlayCheckBoxStyle.selectedIcon());

        putColor("TitlePane.background", bg);
        putColor("TitlePane.foreground", fg);

        if (fontRes != null) {
            String[] fontKeys = {
                    "Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font",
                    "ColorChooser.font", "ComboBox.font", "Label.font", "List.font", "MenuBar.font",
                    "MenuItem.font", "Menu.font", "PopupMenu.font", "OptionPane.font",
                    "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font",
                    "TabbedPane.font", "Table.font", "TableHeader.font", "TextField.font",
                    "FormattedTextField.font", "PasswordField.font", "TextArea.font",
                    "TextPane.font", "EditorPane.font", "TitledBorder.font", "ToolBar.font",
                    "ToolTip.font", "Tree.font", "Spinner.font", "Slider.font"
            };
            for (String key : fontKeys) {
                UIManager.put(key, fontRes);
            }
        }

        // Outline button metrics (read by OverlayOutlineButtonStyle / EdoButtonUI).
        UIManager.put("edo.outlineButton.arc", 12);
        UIManager.put("edo.outlineButton.padding", new Insets(8, 18, 8, 18));
        UIManager.put("edo.outlineButton.compactPadding", new Insets(5, 12, 5, 12));
        UIManager.put("edo.outlineButton.chipPadding", new Insets(4, 10, 4, 10));
    }

    /**
     * Registers default UI delegates that are safe as dialog defaults.
     * Transparent scroll/viewport UIs and {@link SubtleScrollBarUI} stay factory-installed for
     * overlay trees only (global ScrollBarUI would restyle Log's light document chrome).
     */
    private static void registerUiDelegates() {
        UIManager.put("SliderUI", OverlaySliderUI.class.getName());
        UIManager.put("ComboBoxUI", OverlayComboBoxStyle.OverlayComboBoxUI.class.getName());
    }

    private static void putColor(String key, Color color) {
        if (color == null) {
            return;
        }
        // ColorUIResource(Color) uses Color(int rgb) which forces opaque — keep alpha colors raw.
        if (color.getAlpha() < 255) {
            UIManager.put(key, color);
        } else {
            UIManager.put(key, new ColorUIResource(color));
        }
    }

    private static BorderUIResource fieldBorder() {
        return new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140),
                new EmptyBorder(3, 6, 3, 6)));
    }
}
