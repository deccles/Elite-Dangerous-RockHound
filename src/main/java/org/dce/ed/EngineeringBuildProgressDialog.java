package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.dce.ed.edsm.UtilTable;
import org.dce.ed.engineering.BlueprintGrade;
import org.dce.ed.engineering.EngineeringDatabase;
import org.dce.ed.engineering.EngineeringGradeProgress;
import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.engineering.EngineeringGoalProgress;
import org.dce.ed.engineering.EngineeringGoalProgress.ModuleUnitProgress;
import org.dce.ed.ui.EdoUi;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;
import org.dce.ed.ui.OverlayScrollPaneSupport;

/**
 * Per-module engineering grade progress for current goals.
 */
final class EngineeringBuildProgressDialog extends JDialog {

	private static final int HOVER_CLICK_DELAY_MS = 500;

	private EngineeringBuildProgressDialog(Window owner,
			List<ModuleUnitProgress> units,
			EngineeringDatabase database,
			BooleanSupplier passThroughEnabledSupplier) {
		super(owner, "Engineering build progress", ModalityType.MODELESS);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		Font base = OverlayPreferences.getUiFont();
		if (base == null) {
			base = getFont();
		}

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(10, 12, 10, 12));
		root.setBackground(EdoUi.User.BACKGROUND);
		root.setOpaque(true);

		JLabel hint = new JLabel(
				"<html>Each fitted / crafted module is listed with its current grade. "
						+ "The Goals table stays summary-only.</html>");
		hint.setForeground(EdoUi.User.MAIN_TEXT);
		hint.setFont(base);
		root.add(hint, BorderLayout.NORTH);

		ProgressTableModel model = new ProgressTableModel(units, database);
		JTable table = new JTable(model);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setRowHeight(Math.max(20, OverlayPreferences.getUiFontSize() + 6));
		table.setShowGrid(false);
		table.setFillsViewportHeight(true);
		table.setBackground(EdoUi.User.BACKGROUND);
		table.setForeground(EdoUi.User.MAIN_TEXT);
		table.getTableHeader().setBackground(EdoUi.User.PANEL_BG);
		table.getTableHeader().setForeground(EdoUi.User.MAIN_TEXT);
		DefaultTableCellRenderer cell = new DefaultTableCellRenderer();
		cell.setOpaque(false);
		cell.setForeground(EdoUi.User.MAIN_TEXT);
		table.setDefaultRenderer(Object.class, cell);

		JScrollPane scroll = new JScrollPane(table);
		OverlayScrollPaneSupport.installSubtleScrollBars(scroll);
		scroll.setBorder(BorderFactory.createLineBorder(EdoUi.Internal.GRAY_120));
		scroll.getViewport().setBackground(EdoUi.User.BACKGROUND);
		root.add(scroll, BorderLayout.CENTER);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		south.setOpaque(false);
		JButton closeBtn = new JButton("Close");
		OverlayOutlineButtonStyle.applyChip(closeBtn, base, false);
		closeBtn.addActionListener(e -> dispose());
		HoverClickPoller.register(closeBtn, HOVER_CLICK_DELAY_MS, this::dispose, passThroughEnabledSupplier);
		south.add(closeBtn);
		root.add(south, BorderLayout.SOUTH);

		setContentPane(root);
		setPreferredSize(new Dimension(820, 460));
		pack();
		setLocationRelativeTo(owner);
		setAlwaysOnTop(true);

		UtilTable.autoSizeTableColumns(table);
	}

	static void show(Window owner,
			List<EngineeringGoal> goals,
			EngineeringDatabase database,
			String clientKey,
			BooleanSupplier passThroughEnabledSupplier) {
		List<ModuleUnitProgress> units = EngineeringGoalProgress.collectModuleUnitProgress(
				goals, clientKey, database);
		EngineeringBuildProgressDialog dialog =
				new EngineeringBuildProgressDialog(owner, units, database, passThroughEnabledSupplier);
		dialog.setVisible(true);
	}

	private static String unitProgressLabel(ModuleUnitProgress unit, EngineeringDatabase database) {
		EngineeringGoal g = unit.unit();
		if (g == null) {
			return "";
		}
		String progress;
		if (g.isComplete() || g.getFromGrade() >= unit.targetGrade()) {
			progress = "G" + unit.targetGrade() + " done";
		} else if (g.getFromGrade() <= 0 && g.getCraftsAtCurrentGrade() <= 0) {
			progress = "Not started";
		} else {
			progress = EngineeringGradeProgress.progressLabel(g);
		}
		if (!g.getExperimentalId().isBlank()) {
			String expName = database != null
					? database.findById(g.getExperimentalId()).map(BlueprintGrade::getName).orElse("experimental")
					: "experimental";
			progress += g.isExperimentalApplied()
					? " · " + expName + " applied"
					: " · needs " + expName;
		}
		return progress;
	}

	private static final class ProgressTableModel extends AbstractTableModel {
		private final List<Row> rows = new ArrayList<>();

		ProgressTableModel(List<ModuleUnitProgress> units, EngineeringDatabase database) {
			if (units == null || units.isEmpty()) {
				rows.add(new Row("(no goals)", "", "", ""));
				return;
			}
			for (ModuleUnitProgress unit : units) {
				rows.add(new Row(
						unit.goalLabel(),
						unit.moduleLabel(),
						"G" + unit.targetGrade(),
						unitProgressLabel(unit, database)));
			}
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			return switch (column) {
				case 0 -> "Blueprint";
				case 1 -> "Module";
				case 2 -> "Target";
				case 3 -> "Grade";
				default -> "";
			};
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Row row = rows.get(rowIndex);
			return switch (columnIndex) {
				case 0 -> row.blueprint();
				case 1 -> row.module();
				case 2 -> row.target();
				case 3 -> row.grade();
				default -> "";
			};
		}

		private record Row(String blueprint, String module, String target, String grade) {
		}
	}
}
