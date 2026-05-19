package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Regression: tab labels must not be laid out narrower than their text (BasicButtonUI ellipsis).
 */
class OverlayTabButtonSizeTest {

	private static final Set<String> TAB_LABELS = Set.of("Route", "System", "Biology", "Mining", "Fleet Carrier");

	@Test
	void tabButtons_preferredSizeFitsFullLabel() throws Exception {
		final List<JButton> tabButtons = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			EliteOverlayTabbedPane tabs = new EliteOverlayTabbedPane(() -> false);
			tabs.setSize(400, 600);
			tabs.doLayout();
			collectTabButtons(tabs, tabButtons);
		});

		assertTrue(tabButtons.size() >= 5, "expected Route/System/Biology/Mining/Fleet Carrier buttons");
		for (JButton button : tabButtons) {
			assertButtonFitsLabel(button);
		}
	}

	private static void collectTabButtons(Container root, List<JButton> out) {
		for (Component child : root.getComponents()) {
			if (child instanceof JButton button) {
				String text = button.getText();
				if (text != null && TAB_LABELS.contains(text)) {
					out.add(button);
				}
			}
			if (child instanceof Container container) {
				collectTabButtons(container, out);
			}
		}
	}

	private static void assertButtonFitsLabel(JButton button) {
		FontMetrics fm = button.getFontMetrics(button.getFont());
		String text = button.getText();
		int textW = fm.stringWidth(text);

		Insets margin = button.getMargin();
		if (margin == null) {
			margin = new Insets(0, 0, 0, 0);
		}
		Insets borderInsets = button.getBorder() != null
				? button.getBorder().getBorderInsets(button)
				: new Insets(0, 0, 0, 0);

		int requiredW = textW + margin.left + margin.right + borderInsets.left + borderInsets.right;
		Dimension pref = button.getPreferredSize();
		assertTrue(pref.width >= requiredW,
				() -> "Tab \"" + text + "\" preferred width " + pref.width + " < required " + requiredW);
	}
}
