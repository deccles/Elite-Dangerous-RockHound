package org.dce.ed;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.dce.ed.ui.EdoUi;

/** Compact stacked progress display used for multi-module engineering goals. */
final class SegmentedProgressBar extends JComponent {
    private static final long serialVersionUID = 1L;
    private List<Double> progress = List.of();
    private boolean materialsShort;

    SegmentedProgressBar() {
        setOpaque(false);
    }

    void setProgress(List<Double> progress, boolean materialsShort) {
        this.progress = progress != null ? new ArrayList<>(progress) : List.of();
        this.materialsShort = materialsShort;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!(g instanceof Graphics2D original) || progress.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) original.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int padX = 6;
            int innerHeight = Math.max(1, getHeight() - 4);
            // Goals rows are two lines tall; keep a single bar well under half the cell.
            int maxPaintHeight = Math.max(progress.size(), (getHeight() * 3) / 8);
            int paintHeight = Math.min(innerHeight, maxPaintHeight);
            int padY = Math.max(0, (getHeight() - paintHeight) / 2);
            int gap = 1;
            int width = Math.max(0, getWidth() - padX * 2);
            int available = Math.max(progress.size(), paintHeight - gap * (progress.size() - 1));
            int lineHeight = Math.max(1, available / progress.size());
            for (int i = 0; i < progress.size(); i++) {
                int y = padY + i * (lineHeight + gap);
                double fraction = Math.max(0.0, Math.min(1.0, progress.get(i) != null ? progress.get(i) : 0.0));
                g2.setColor(EdoUi.Internal.MAIN_TEXT_ALPHA_40);
                g2.fillRoundRect(padX, y, width, lineHeight, 4, 4);
                int fillWidth = (int) Math.round(width * fraction);
                if (fillWidth > 0) {
                    g2.setColor(fraction >= 0.999 ? Color.GREEN : materialsShort ? Color.RED : Color.YELLOW);
                    g2.fillRoundRect(padX, y, Math.max(fillWidth, Math.min(3, width)), lineHeight, 4, 4);
                }
            }
        } finally {
            g2.dispose();
        }
    }
}
