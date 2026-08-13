package org.dce.ed.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTable;
import javax.swing.JScrollBar;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;

import org.junit.jupiter.api.Test;
import org.dce.ed.mission.CommoditySourceChoice;

class CommoditySourceDialogSizingTest {
    @Test
    void defaultDialogHeightIsAboutTwiceTheOriginal() {
        assertEquals(850, CommoditySourceDialog.defaultDialogSize().height);
        assertEquals(760, CommoditySourceDialog.defaultDialogSize().width);
    }

    @Test
    void scheduleInitialSearch_runsForPopulatedCurrentSystem() throws Exception {
        AtomicInteger searches = new AtomicInteger();

        CommoditySourceDialog.scheduleInitialSearch("Sol", searches::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, searches.get());
    }

    @Test
    void configureResultsTable_sizesRowsForActiveFont() {
        JTable table = new JTable(new Object[][] { { "Gutenberg Vision", "Col 285 Sector OK-P a35-2" } },
                new Object[] { "Station", "System" });
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 22);

        CommoditySourceDialog.configureResultsTable(table, font);

        assertTrue(table.getRowHeight() >= table.getFontMetrics(font).getHeight() + 4);
        assertTrue(table.getTableHeader().getPreferredSize().height
                >= table.getTableHeader().getFontMetrics(font.deriveFont(Font.BOLD)).getHeight() + 4);
    }

    @Test
    void configureSupplyRenderer_colorsOnlyInsufficientUnselectedSupplyRed() {
        JTable table = new JTable(
                new Object[][] { { "Low", 40 }, { "Enough", 42 }, { "High", 100 } },
                new Object[] { "Station", "Supply" });
        CommoditySourceDialog.configureSupplyRenderer(table, 1, 42);
        TableCellRenderer renderer = table.getColumnModel().getColumn(1).getCellRenderer();

        Component low = renderer.getTableCellRendererComponent(table, 40, false, false, 0, 1);
        assertEquals(Color.RED, low.getForeground());
        Component enough = renderer.getTableCellRendererComponent(table, 42, false, false, 1, 1);
        assertNotEquals(Color.RED, enough.getForeground());
        Component selectedLow = renderer.getTableCellRendererComponent(table, 40, true, false, 0, 1);
        assertEquals(table.getSelectionForeground(), selectedLow.getForeground());
    }

    @Test
    void scrollbarAtBottomTriggersLoadMoreOnce() {
        JScrollBar bar = new JScrollBar(JScrollBar.VERTICAL, 90, 10, 0, 100);
        AtomicInteger loads = new AtomicInteger();

        CommoditySourceDialog.configureLoadMore(bar, loads::incrementAndGet);
        bar.setValue(89);
        bar.setValue(90);

        assertEquals(1, loads.get());
    }

    @Test
    void locationCheckboxesHaveDistinctVisibleSelectedState() {
        JCheckBox stations = new JCheckBox("Stations");
        JCheckBox planetary = new JCheckBox("Planetary bases");
        JCheckBox carriers = new JCheckBox("Fleet carriers");

        CommoditySourceDialog.configureLocationCheckboxes(stations, planetary, carriers);

        for (JCheckBox box : new JCheckBox[] { stations, planetary, carriers }) {
            assertTrue(box.getIcon() != null);
            assertTrue(box.getSelectedIcon() != null);
            assertNotEquals(box.getIcon(), box.getSelectedIcon());
        }
    }

    @Test
    void resultValuesUseFriendlyDistanceAndRelativeAge() {
        Instant now = Instant.parse("2026-08-13T12:00:00Z");

        assertEquals("3,684.9", CommoditySourceDialog.formatArrivalDistance(3684.875028));
        assertEquals("12 min ago", CommoditySourceDialog.formatUpdated("2026-08-13T11:48:00Z", now));
        assertEquals("3 hr ago", CommoditySourceDialog.formatUpdated("2026-08-13T09:00:00Z", now));
        assertEquals("4 days ago", CommoditySourceDialog.formatUpdated("2026-08-09T12:00:00Z", now));
    }

    @Test
    void actionButtonsUseThemeAndSaveRequiresSelectedResult() {
        JButton search = new JButton("Search");
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");

        CommoditySourceDialog.configureActionButtons(search, cancel, save,
                new Font(Font.MONOSPACED, Font.PLAIN, 16));
        CommoditySourceDialog.updateSaveEnabled(save, -1);

        assertTrue(search.getBorder() != null);
        assertTrue(cancel.getBorder() != null);
        assertTrue(save.getBorder() != null);
        assertFalse(save.isEnabled());
        CommoditySourceDialog.updateSaveEnabled(save, 0);
        assertTrue(save.isEnabled());
    }

    @Test
    void selectedResultIsResolvedFromBackingRows() {
        CommoditySourceChoice choice = new CommoditySourceChoice("Sol", "Galileo", 1.0, 10.0,
                100, 200, "now", "Orbis", 3, null);

        assertEquals(choice, CommoditySourceDialog.selectedChoice(List.of(choice), 0));
        assertEquals(null, CommoditySourceDialog.selectedChoice(List.of(choice), -1));
    }
}
