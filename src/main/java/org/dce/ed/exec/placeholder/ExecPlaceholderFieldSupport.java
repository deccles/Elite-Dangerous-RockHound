package org.dce.ed.exec.placeholder;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** {@code $SYMBOL} autocomplete and hover tooltips for exec program-args fields. */
public final class ExecPlaceholderFieldSupport {

    private static final Pattern SYMBOL_AT = Pattern.compile("\\$([A-Z][A-Z0-9_]*)");

    private final JTextField field;
    private final Supplier<Map<String, String>> valuesSupplier;
    private JPopupMenu popup;
    private JList<String> list;
    private boolean suppressEvents;

    public ExecPlaceholderFieldSupport(JTextField field, Supplier<Map<String, String>> valuesSupplier) {
        this.field = field;
        this.valuesSupplier = valuesSupplier;
        wire();
    }

    private void wire() {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onDocumentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onDocumentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onDocumentChanged();
            }
        });

        field.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoverTooltip(e.getPoint());
            }
        });

        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                field.setToolTipText(null);
            }
        });
    }

    private void onDocumentChanged() {
        if (suppressEvents) {
            return;
        }
        int caret = field.getCaretPosition();
        String fragment = fragmentAfterDollar(field.getText(), caret);
        if (fragment == null) {
            hidePopup();
            return;
        }
        List<ExecPlaceholderId> matches = ExecPlaceholderId.matchingPrefix(fragment);
        if (matches.isEmpty()) {
            hidePopup();
            return;
        }
        showMatches(matches);
    }

    private void showMatches(List<ExecPlaceholderId> matches) {
        ensurePopup();
        DefaultListModel<String> model = new DefaultListModel<>();
        for (ExecPlaceholderId id : matches) {
            model.addElement(id.token());
        }
        list.setModel(model);
        if (model.isEmpty()) {
            hidePopup();
            return;
        }
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(10, model.getSize()));
        int width = Math.max(field.getWidth(), 280);
        popup.setPopupSize(width, Math.min(220, list.getPreferredScrollableViewportSize().height + 4));
        popup.show(field, 0, field.getHeight());
    }

    private void ensurePopup() {
        if (popup != null) {
            return;
        }
        popup = new JPopupMenu();
        popup.setFocusable(false);
        list = new JList<>();
        list.setFocusable(false);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 1) {
                    applySelected();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        popup.add(scroll);
    }

    private void applySelected() {
        if (list == null || list.getSelectedValue() == null) {
            return;
        }
        String token = list.getSelectedValue();
        int caret = field.getCaretPosition();
        String text = field.getText();
        int dollar = lastDollarBefore(text, caret);
        if (dollar < 0) {
            hidePopup();
            return;
        }
        suppressEvents = true;
        try {
            String before = text.substring(0, dollar);
            String after = text.substring(caret);
            String updated = before + token + after;
            field.setText(updated);
            field.setCaretPosition(before.length() + token.length());
        } finally {
            suppressEvents = false;
        }
        hidePopup();
    }

    private void hidePopup() {
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    private void updateHoverTooltip(Point point) {
        int index = field.viewToModel2D(point);
        if (index < 0) {
            field.setToolTipText(null);
            return;
        }
        String text = field.getText();
        Matcher m = SYMBOL_AT.matcher(text);
        while (m.find()) {
            if (index >= m.start() && index <= m.end()) {
                ExecPlaceholderId id = ExecPlaceholderId.fromToken(m.group()).orElse(null);
                if (id == null) {
                    field.setToolTipText(null);
                    return;
                }
                Map<String, String> values = valuesSupplier != null ? valuesSupplier.get() : null;
                String current = values != null ? values.get(id.name()) : null;
                String valueLine = current == null || current.isBlank()
                        ? "(empty — not available yet)"
                        : current;
                field.setToolTipText("<html>" + id.token() + ": " + escapeHtml(valueLine)
                        + "<br>" + escapeHtml(id.getDescription()) + "</html>");
                return;
            }
        }
        field.setToolTipText(null);
    }

    /** Symbol under model index in text, or null. */
    public static ExecPlaceholderId symbolAt(String text, int index) {
        if (text == null || text.isBlank() || index < 0) {
            return null;
        }
        Matcher m = SYMBOL_AT.matcher(text);
        while (m.find()) {
            if (index >= m.start() && index <= m.end()) {
                return ExecPlaceholderId.fromToken(m.group()).orElse(null);
            }
        }
        return null;
    }

    public static String tooltipHtml(ExecPlaceholderId id, Map<String, String> values) {
        if (id == null) {
            return null;
        }
        String current = values != null ? values.get(id.name()) : null;
        String valueLine = current == null || current.isBlank()
                ? "(empty — not available yet)"
                : current;
        return "<html>" + id.token() + ": " + escapeHtml(valueLine)
                + "<br>" + escapeHtml(id.getDescription()) + "</html>";
    }

    private static String fragmentAfterDollar(String text, int caret) {
        int dollar = lastDollarBefore(text, caret);
        if (dollar < 0) {
            return null;
        }
        String fragment = text.substring(dollar + 1, Math.min(caret, text.length()));
        if (fragment.contains(" ") || fragment.contains("\t")) {
            return null;
        }
        return fragment;
    }

    private static int lastDollarBefore(String text, int caret) {
        if (text == null || caret <= 0) {
            return -1;
        }
        int limit = Math.min(caret, text.length());
        for (int i = limit - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '$') {
                return i;
            }
            if (Character.isWhitespace(c)) {
                return -1;
            }
        }
        return -1;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
