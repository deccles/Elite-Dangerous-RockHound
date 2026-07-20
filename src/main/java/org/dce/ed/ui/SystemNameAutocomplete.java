package org.dce.ed.ui;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.dce.ed.util.EdsmClient;

/**
 * EDSM-backed system name autocomplete for a single {@link JTextField}, matching the behavior of
 * {@link org.dce.ed.util.EdsmQueryTool} (prefix search, debounced, popup list).
 */
public final class SystemNameAutocomplete {

    private static final int MIN_PREFIX = 3;
    private static final int DEBOUNCE_MS = 250;

    private final JTextField field;
    private final EdsmClient edsmClient;

    private boolean suppressDocEvents;
    private Timer debounceTimer;
    private String pendingPrefix;
    private JPopupMenu popup;
    private JList<String> list;
    /** Snapshot when opening the list (for UP-from-first-row restore). */
    private String originalTextForListNavigation;

    public SystemNameAutocomplete(JTextField field, EdsmClient edsmClient) {
        this.field = field;
        this.edsmClient = edsmClient;
        wire();
    }

    private void wire() {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTyping();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTyping();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTyping();
            }
        });

        InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = field.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "systemNameAcHide");
        am.put("systemNameAcHide", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hidePopup();
            }
        });

        im.put(KeyStroke.getKeyStroke("DOWN"), "systemNameAcDown");
        am.put("systemNameAcDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (popup == null || !popup.isVisible() || list == null || list.getModel().getSize() == 0) {
                    return;
                }
                originalTextForListNavigation = field.getText();
                list.setSelectedIndex(0);
                list.ensureIndexIsVisible(0);
                list.requestFocusInWindow();
            }
        });
    }

    private void onTyping() {
        if (suppressDocEvents) {
            return;
        }
        String text = field.getText().trim();
        if (text.length() < MIN_PREFIX) {
            pendingPrefix = null;
            hidePopup();
            return;
        }
        pendingPrefix = text;
        if (debounceTimer == null) {
            debounceTimer = new Timer(DEBOUNCE_MS, e -> runFetch());
            debounceTimer.setRepeats(false);
        }
        debounceTimer.restart();
    }

    private void runFetch() {
        final String prefix = pendingPrefix;
        if (prefix == null || prefix.length() < MIN_PREFIX) {
            return;
        }
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return edsmClient.suggestSystemNames(prefix);
            }

            @Override
            protected void done() {
                try {
                    List<String> suggestions = get();
                    if (suggestions == null) {
                        suggestions = Collections.emptyList();
                    }
                    if (prefix.equals(pendingPrefix)) {
                        showSuggestions(suggestions);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void ensurePopup() {
        if (popup != null) {
            return;
        }
        popup = new JPopupMenu();
        popup.setFocusable(false);

        list = new JList<>();
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(true);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                    applySelection();
                }
            }
        });

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.setFocusable(false);
        popup.add(sp);

        InputMap lim = list.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap lam = list.getActionMap();

        lim.put(KeyStroke.getKeyStroke("ENTER"), "systemNameAcAccept");
        lam.put("systemNameAcAccept", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applySelection();
            }
        });

        lim.put(KeyStroke.getKeyStroke("ESCAPE"), "systemNameAcEscape");
        lam.put("systemNameAcEscape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                hidePopup();
                field.requestFocusInWindow();
                field.setCaretPosition(field.getText().length());
            }
        });

        lim.put(KeyStroke.getKeyStroke("UP"), "systemNameAcUp");
        lam.put("systemNameAcUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int idx = list.getSelectedIndex();
                if (idx > 0) {
                    int newIdx = idx - 1;
                    list.setSelectedIndex(newIdx);
                    list.ensureIndexIsVisible(newIdx);
                } else {
                    String restore = originalTextForListNavigation;
                    if (restore == null) {
                        restore = field.getText();
                    }
                    field.setText(restore);
                    field.requestFocusInWindow();
                    field.setCaretPosition(restore.length());
                    originalTextForListNavigation = null;
                    hidePopup();
                }
            }
        });

        lim.put(KeyStroke.getKeyStroke("DOWN"), "systemNameAcDownInList");
        lam.put("systemNameAcDownInList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int size = list.getModel().getSize();
                if (size == 0) {
                    return;
                }
                int idx = list.getSelectedIndex();
                if (idx < 0) {
                    idx = 0;
                }
                if (idx < size - 1) {
                    int newIdx = idx + 1;
                    list.setSelectedIndex(newIdx);
                    list.ensureIndexIsVisible(newIdx);
                }
            }
        });
    }

    private void showSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            hidePopup();
            return;
        }
        ensurePopup();
        list.setListData(suggestions.toArray(new String[0]));
        list.setVisibleRowCount(Math.min(8, suggestions.size()));

        int width = Math.max(field.getWidth(), 200);
        int listHeight = list.getPreferredScrollableViewportSize().height;
        if (listHeight <= 0) {
            listHeight = list.getPreferredSize().height;
        }
        if (listHeight <= 0) {
            listHeight = 120;
        }
        popup.setPopupSize(width, listHeight + 4);
        popup.show(field, 0, field.getHeight());
        field.requestFocusInWindow();
        field.setCaretPosition(field.getText().length());
    }

    private void applySelection() {
        if (list == null) {
            return;
        }
        String selected = list.getSelectedValue();
        if (selected == null || selected.isEmpty()) {
            return;
        }
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        pendingPrefix = null;
        hidePopup();
        suppressDocEvents = true;
        try {
            field.setText(selected);
            field.setCaretPosition(selected.length());
        } finally {
            suppressDocEvents = false;
        }
    }

    private void hidePopup() {
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    /** Selective / hybrid mouse mode: keep click-through off while the suggestion list is open. */
    public boolean isPointerOverPopup(java.awt.Point screenPoint) {
        if (popup == null || !popup.isShowing() || screenPoint == null) {
            return false;
        }
        try {
            java.awt.Point origin = popup.getLocationOnScreen();
            return new java.awt.Rectangle(origin.x, origin.y, popup.getWidth(), popup.getHeight())
                    .contains(screenPoint);
        } catch (java.awt.IllegalComponentStateException ex) {
            return false;
        }
    }
}
