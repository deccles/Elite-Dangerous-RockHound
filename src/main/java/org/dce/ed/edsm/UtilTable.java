package org.dce.ed.edsm;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * Table Utilities.
 */
public class UtilTable {
    public static void autoSizeTableColumns(JTable table) {
    	int[] fixedColumns = new int[0];
    	autoSizeTableColumns(table, fixedColumns);
    }
    /**
     * Auto-size the Table Columns, setting column widths to longest cell value.
     * This does make rendering take longer, but it's a sure-fire way to make sure
     *  all data is fully visible w/o users having to manually resize.
     *
     * @param table
     *            The table to resize.
     * @param fixedColumns
     *            Column indices (view order) to lock to their computed content width
     *            after auto-sizing ({@code min = max = preferred}).
     */
    public static void autoSizeTableColumns(JTable table, int[] fixedColumns ) {
        if (table == null) {
            return;
        }
        TableModel model = table.getModel();
        TableColumn column = null;
        Component comp = null;
        int headerWidth = 0;
        int maxCellWidth = Integer.MIN_VALUE;
        int cellWidth = 0;
        TableCellRenderer headerRenderer =
                table.getTableHeader().getDefaultRenderer();

        for (int i = 0; i < table.getColumnCount(); i++) {
            column = table.getColumnModel().getColumn(i);

            comp = headerRenderer.getTableCellRendererComponent(table,
                    column.getHeaderValue(),
                    false,
                    false,
                    0,
                    0);
            headerWidth = comp.getPreferredSize().width + 10;

            maxCellWidth = Integer.MIN_VALUE;
            for (int j = 0; j < model.getRowCount(); j++) {
                if (j == 100)
                    break;
                TableCellRenderer r = table.getCellRenderer(j, i);
                comp = r.getTableCellRendererComponent(table,
                        model.getValueAt(j, i),
                        false,
                        false,
                        j,
                        i);
                cellWidth = comp.getPreferredSize().width;
                if (cellWidth >= maxCellWidth)
                    maxCellWidth = cellWidth;
            }
            if (maxCellWidth == Integer.MIN_VALUE) {
                maxCellWidth = 0;
            }
            maxCellWidth += 10;
            int width = Math.max(headerWidth, maxCellWidth);
            column.setPreferredWidth(width);
            if (isFixedColumn(fixedColumns, i)) {
                column.setMinWidth(width);
                column.setMaxWidth(width);
                column.setWidth(width);
            }
        }
    }

    private static boolean isFixedColumn(int[] fixedColumns, int column) {
        if (fixedColumns == null) {
            return false;
        }
        for (int fixed : fixedColumns) {
            if (fixed == column) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets min width, max width, and width to zero in table column model and its header's column model
     * @param table
     * @param col
     */
    public static void hideColumn(JTable table, int col){
        if(table == null) return;
        TableColumnModel cm = table.getColumnModel();
        if(cm == null) return;
        TableColumn tc = cm.getColumn(col);
        if(tc == null) return;
        tc.setMinWidth(0);
        tc.setMaxWidth(0);
        tc.setWidth(0);
        if(table.getTableHeader() == null || table.getTableHeader().getColumnModel() == null)
            return;
        TableColumn hdr = table.getTableHeader().getColumnModel().getColumn(col);
        hdr.setMinWidth(0);
        hdr.setMaxWidth(0);
        hdr.setWidth(0);
    }

    /**
     * Removes start/end "html" tags, and replaces "br"(eak) tags with spaces in column header name
     * @param colNm
     * @return
     */
    public static String getColumnNameNoHtml(String colNm) {
        if(colNm == null || colNm.isBlank()) return colNm;
        colNm = colNm.replaceAll("<html>", "");
        colNm = colNm.replaceAll("</html>", "");
        colNm = colNm.replaceAll("<br>", " ");
        colNm = colNm.replaceAll("<br/>", " ");
        colNm = colNm.replaceAll("<center>", "");
        return colNm;
    }
}
