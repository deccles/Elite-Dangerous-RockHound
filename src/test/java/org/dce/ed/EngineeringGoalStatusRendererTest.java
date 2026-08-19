package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.lang.reflect.Field;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import org.junit.jupiter.api.Test;

class EngineeringGoalStatusRendererTest {

    @Test
    void textStatusRemainsVisibleWhenProgressBarIsNotSelected() throws Exception {
        EngineeringTabPanel panel = new EngineeringTabPanel(() -> false);
        Field field = EngineeringTabPanel.class.getDeclaredField("goalsTable");
        field.setAccessible(true);
        JTable goalsTable = (JTable) field.get(panel);
        TableCellRenderer renderer = goalsTable.getColumnModel().getColumn(7).getCellRenderer();

        JTable oneRow = new JTable(1, 10);
        JPanel rendered = (JPanel) renderer.getTableCellRendererComponent(
                oneRow, "Ready", false, false, 0, 7);
        rendered.setSize(120, 24);
        rendered.doLayout();

        JLabel label = null;
        for (Component child : rendered.getComponents()) {
            if (child instanceof JLabel found) {
                label = found;
            }
        }
        assertTrue(label != null && label.isVisible());
        assertEquals("Ready", label.getText());
        assertTrue(label.getWidth() > 0, "text status must occupy the Status cell");
    }
}
