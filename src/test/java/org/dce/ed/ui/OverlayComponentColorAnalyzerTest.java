package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.junit.jupiter.api.Test;

class OverlayComponentColorAnalyzerTest {

    @Test
    void detectsNearWhiteOpaquePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        assertFalse(OverlayComponentColorAnalyzer.analyzeWhiteComponents(panel).isEmpty());
    }

    @Test
    void configureScrollPane_clearsWhiteChrome() {
        JTable table = new JTable();
        JScrollPane scroll = new JScrollPane(table);
        OverlayTransparentChrome.configureScrollPane(scroll);
        assertFalse(scroll.isOpaque());
        assertFalse(table.isOpaque());
        assertTrue(OverlayComponentColorAnalyzer.analyzeWhiteComponents(scroll).isEmpty());
    }
}
