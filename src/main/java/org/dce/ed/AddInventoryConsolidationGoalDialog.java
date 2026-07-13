package org.dce.ed;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.function.BooleanSupplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.dce.ed.engineering.EngineeringGoal;
import org.dce.ed.ui.HoverClickPoller;
import org.dce.ed.ui.OverlayOutlineButtonStyle;

/** Picker for an inventory consolidation goal (trade up excess commons). */
final class AddInventoryConsolidationGoalDialog extends JDialog {

    private static final int HOVER_CLICK_DELAY_MS = 500;

    private final BooleanSupplier passThroughEnabledSupplier;
    private final JComboBox<String> maxSourceCombo = new JComboBox<>(new String[] {
            "Grade 1 only",
            "Grades 1–2"
    });
    private final JComboBox<String> targetCombo = new JComboBox<>(new String[] {
            "Grade 3",
            "Grade 4",
            "Grade 5"
    });

    private EngineeringGoal result;

    private AddInventoryConsolidationGoalDialog(Window owner, BooleanSupplier passThroughEnabledSupplier) {
        super(owner, "Reduce common materials", ModalityType.APPLICATION_MODAL);
        this.passThroughEnabledSupplier = passThroughEnabledSupplier;
        buildUi();
        pack();
        setMinimumSize(new Dimension(420, 200));
        setLocationRelativeTo(owner);
    }

    static EngineeringGoal show(Window owner, BooleanSupplier passThroughEnabledSupplier) {
        AddInventoryConsolidationGoalDialog dialog =
                new AddInventoryConsolidationGoalDialog(owner, passThroughEnabledSupplier);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void buildUi() {
        Font base = OverlayPreferences.getUiFont();
        int fontSize = OverlayPreferences.getUiFontSize();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        JLabel intro = new JLabel(
                "<html>Trade excess low-grade materials up within the same trader row "
                        + "to free inventory space. Materials reserved for other goals are kept.</html>");
        form.add(intro, gc);

        gc.gridwidth = 1;
        gc.gridy = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(new JLabel("Consolidate from:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        maxSourceCombo.setSelectedIndex(1);
        form.add(maxSourceCombo, gc);

        gc.gridy = 2;
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(new JLabel("Consolidate up to:"), gc);
        gc.gridx = 1;
        gc.weightx = 1;
        targetCombo.setSelectedIndex(1);
        form.add(targetCombo, gc);

        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = new JButton("Cancel");
        OverlayOutlineButtonStyle.applyChip(cancel, base, false);
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton("Add goal");
        OverlayOutlineButtonStyle.applyChip(ok, base, false);
        ok.addActionListener(e -> accept());
        HoverClickPoller.register(ok, HOVER_CLICK_DELAY_MS, this::accept, passThroughEnabledSupplier);
        buttons.add(cancel);
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        getContentPane().add(root);
        getRootPane().setDefaultButton(ok);
    }

    private void accept() {
        int maxSource = maxSourceCombo.getSelectedIndex() == 0 ? 1 : 2;
        int target = targetCombo.getSelectedIndex() + 3;
        result = EngineeringGoal.inventoryConsolidation(maxSource, target);
        dispose();
    }
}
