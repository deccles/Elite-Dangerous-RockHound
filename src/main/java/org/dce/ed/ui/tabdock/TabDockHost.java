package org.dce.ed.ui.tabdock;

import java.awt.CardLayout;
import java.awt.Rectangle;
import java.awt.Window;

import javax.swing.JPanel;

/** A place that can host overlay tabs (main overlay or a floating window). */
public interface TabDockHost {

    String getDockId();

    Window getWindow();

    JPanel getTabStrip();

    JPanel getCardPanel();

    CardLayout getCardLayout();

    /** Called after tabs are added/removed so chevrons / layout refresh. */
    void onDockTabsChanged();

    Rectangle getBoundsOnScreen();
}
