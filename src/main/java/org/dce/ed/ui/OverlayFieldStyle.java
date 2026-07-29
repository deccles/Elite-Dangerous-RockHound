package org.dce.ed.ui;

import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Dark-theme text fields and spinners for preferences / dialogs (avoids glarey Windows white plates).
 * Field borders come from {@link EdoLookAndFeel} UIDefaults when present.
 */
public final class OverlayFieldStyle {

    private OverlayFieldStyle() {
    }

    public static void applyTextField(JTextField field, Font font) {
        if (field == null) {
            return;
        }
        if (font != null) {
            field.setFont(font);
        }
        field.setOpaque(true);
        field.setForeground(EdoUi.User.MAIN_TEXT);
        field.setBackground(EdoUi.User.PANEL_BG);
        field.setCaretColor(EdoUi.User.MAIN_TEXT);
        field.setSelectionColor(EdoUi.ED_ORANGE_LESS_TRANS);
        field.setSelectedTextColor(EdoUi.User.MAIN_TEXT);
        field.setBorder(textFieldBorder());
    }

    public static void applyTextArea(JTextArea area, Font font) {
        if (area == null) {
            return;
        }
        if (font != null) {
            area.setFont(font);
        }
        area.setOpaque(true);
        area.setForeground(EdoUi.User.MAIN_TEXT);
        area.setBackground(EdoUi.User.PANEL_BG);
        area.setCaretColor(EdoUi.User.MAIN_TEXT);
        area.setSelectionColor(EdoUi.ED_ORANGE_LESS_TRANS);
        area.setSelectedTextColor(EdoUi.User.MAIN_TEXT);
        Border fromLaf = UIManager.getBorder("TextArea.border");
        area.setBorder(fromLaf != null ? fromLaf : textFieldBorder());
    }

    public static void applySpinner(JSpinner spinner, Font font) {
        if (spinner == null) {
            return;
        }
        if (font != null) {
            spinner.setFont(font);
        }
        spinner.setOpaque(true);
        spinner.setForeground(EdoUi.User.MAIN_TEXT);
        spinner.setBackground(EdoUi.User.PANEL_BG);
        spinner.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));
        JComponent editor = spinner.getEditor();
        if (editor != null) {
            editor.setOpaque(true);
            editor.setBackground(EdoUi.User.PANEL_BG);
            editor.setForeground(EdoUi.User.MAIN_TEXT);
            editor.setBorder(new EmptyBorder(0, 0, 0, 0));
        }
        if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
            JTextField field = defaultEditor.getTextField();
            if (font != null) {
                field.setFont(font);
            }
            field.setOpaque(true);
            field.setForeground(EdoUi.User.MAIN_TEXT);
            field.setBackground(EdoUi.User.PANEL_BG);
            field.setCaretColor(EdoUi.User.MAIN_TEXT);
            field.setSelectionColor(EdoUi.ED_ORANGE_LESS_TRANS);
            field.setSelectedTextColor(EdoUi.User.MAIN_TEXT);
            field.setBorder(new EmptyBorder(2, 6, 2, 4));
        }
        // Spinner next/prev buttons are often left as system-white by Windows L&F.
        for (java.awt.Component child : spinner.getComponents()) {
            if (child instanceof javax.swing.JButton button) {
                button.setOpaque(true);
                button.setBackground(EdoUi.User.PANEL_BG);
                button.setForeground(EdoUi.User.MAIN_TEXT);
                button.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140));
            }
        }
    }

    private static Border textFieldBorder() {
        Border fromLaf = UIManager.getBorder("TextField.border");
        if (fromLaf != null) {
            return fromLaf;
        }
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EdoUi.Internal.MAIN_TEXT_ALPHA_140),
                new EmptyBorder(3, 6, 3, 6));
    }
}
