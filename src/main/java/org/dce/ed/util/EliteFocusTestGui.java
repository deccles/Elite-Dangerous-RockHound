package org.dce.ed.util;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Test harness for forcing focus onto Elite Dangerous. One button per Win32 technique,
 * because Windows' foreground-lock blocks the polite approaches in many configurations.
 */
public final class EliteFocusTestGui {

    /** User32 calls not exposed by JNA's stock {@link User32} mapping. */
    private interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        /** Undocumented but stable since XP; activates like Alt-Tab, ignoring foreground lock. */
        void SwitchToThisWindow(HWND hWnd, boolean fAltTab);

        boolean BringWindowToTop(HWND hWnd);

        boolean IsIconic(HWND hWnd);
    }

    private static JTextArea log;
    private static Robot robot;

    private EliteFocusTestGui() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EliteFocusTestGui::show);
    }

    private static void show() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            robot = null;
        }

        JFrame frame = new JFrame("Elite Focus Test");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        log = new JTextArea(12, 60);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel buttons = new JPanel(new GridLayout(0, 1, 6, 6));
        buttons.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttons.add(button("1: Production path (tryBringToForeground)",
                EliteFocusTestGui::tryAttachThreadInput));
        buttons.add(button("2: Ctrl-key tap + SetForegroundWindow (safe last resort)",
                EliteFocusTestGui::tryCtrlKeyTrick));
        buttons.add(button("3: SwitchToThisWindow (Alt-Tab style)",
                EliteFocusTestGui::trySwitchToThisWindow));
        buttons.add(button("4: Minimize + Restore ED window",
                EliteFocusTestGui::tryMinimizeRestore));
        buttons.add(button("5: Legacy Alt-key trick (unsafe — can kill ED input)",
                EliteFocusTestGui::tryAltKeyTrick));
        buttons.add(button("6: Escalate production techniques until foreground",
                EliteFocusTestGui::tryEscalate));

        frame.setLayout(new BorderLayout());
        frame.add(buttons, BorderLayout.NORTH);
        frame.add(new JScrollPane(log), BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        append("Ready. Foreground: " + EliteWindowFocus.foregroundDescription());
    }

    private static JButton button(String label, Runnable technique) {
        JButton b = new JButton(label);
        b.addActionListener(e -> runTechnique(label, technique));
        return b;
    }

    private static void runTechnique(String label, Runnable technique) {
        HWND elite = EliteWindowFocus.findEliteWindow();
        if (elite == null) {
            append("Elite window not found (is EliteDangerous64.exe running?)");
            return;
        }
        append("--- " + label);
        technique.run();
        append("Immediately after: " + verdict());
        Timer verify = new Timer(500, ev -> append("After 500ms:       " + verdict()));
        verify.setRepeats(false);
        verify.start();
    }

    private static String verdict() {
        return (EliteWindowFocus.isEliteForeground() ? "ED IS foreground" : "ED NOT foreground")
                + " | " + EliteWindowFocus.foregroundDescription();
    }

    // --- Technique 1: what EDO already does -------------------------------------------------

    private static void tryAttachThreadInput() {
        boolean ok = EliteWindowFocus.tryBringToForeground();
        append("tryBringToForeground returned " + ok);
    }

    // --- Technique 2: tap Ctrl so *we* own the last input, then SetForegroundWindow ---------

    private static void tryCtrlKeyTrick() {
        HWND elite = EliteWindowFocus.findEliteWindow();
        if (robot == null) {
            append("Robot unavailable; cannot send Ctrl tap");
            return;
        }
        if (User32Ext.INSTANCE.IsIconic(elite)) {
            User32.INSTANCE.ShowWindow(elite, WinUser.SW_RESTORE);
        }
        // Ctrl (not Alt): defeats foreground lock without leaving Elite in stuck-Alt / dead-input.
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        boolean set = User32.INSTANCE.SetForegroundWindow(elite);
        int err = Kernel32.INSTANCE.GetLastError();
        User32Ext.INSTANCE.BringWindowToTop(elite);
        EliteWindowFocus.releaseStuckModifiers();
        append("SetForegroundWindow=" + set + " GetLastError=" + err);
    }

    // --- Legacy (unsafe): Alt held across SetForegroundWindow can kill ED keyboard/joystick --

    private static void tryAltKeyTrick() {
        HWND elite = EliteWindowFocus.findEliteWindow();
        if (robot == null) {
            append("Robot unavailable; cannot send Alt tap");
            return;
        }
        if (User32Ext.INSTANCE.IsIconic(elite)) {
            User32.INSTANCE.ShowWindow(elite, WinUser.SW_RESTORE);
        }
        append("WARNING: Alt trick can leave ED input dead until click-away/back");
        robot.keyPress(KeyEvent.VK_ALT);
        boolean set = User32.INSTANCE.SetForegroundWindow(elite);
        robot.keyRelease(KeyEvent.VK_ALT);
        int err = Kernel32.INSTANCE.GetLastError();
        User32Ext.INSTANCE.BringWindowToTop(elite);
        EliteWindowFocus.releaseStuckModifiers();
        append("SetForegroundWindow=" + set + " GetLastError=" + err);
    }

    // --- Technique 3: SwitchToThisWindow ----------------------------------------------------

    private static void trySwitchToThisWindow() {
        HWND elite = EliteWindowFocus.findEliteWindow();
        if (User32Ext.INSTANCE.IsIconic(elite)) {
            User32.INSTANCE.ShowWindow(elite, WinUser.SW_RESTORE);
        }
        User32Ext.INSTANCE.SwitchToThisWindow(elite, true);
        EliteWindowFocus.releaseStuckModifiers();
        append("SwitchToThisWindow called");
    }

    // --- Technique 4: minimize then restore forces activation on restore ---------------------

    private static void tryMinimizeRestore() {
        HWND elite = EliteWindowFocus.findEliteWindow();
        User32.INSTANCE.ShowWindow(elite, WinUser.SW_MINIMIZE);
        sleep(150);
        User32.INSTANCE.ShowWindow(elite, WinUser.SW_RESTORE);
        EliteWindowFocus.releaseStuckModifiers();
        append("Minimize+Restore done");
    }

    // --- Technique 6: escalate (production order; Alt omitted) ------------------------------

    private static void tryEscalate() {
        Runnable[] steps = {
                EliteFocusTestGui::tryAttachThreadInput,
                EliteFocusTestGui::trySwitchToThisWindow,
                EliteFocusTestGui::tryCtrlKeyTrick,
                EliteFocusTestGui::tryMinimizeRestore,
        };
        String[] names = { "Production path", "SwitchToThisWindow", "Ctrl-key trick", "Minimize+Restore" };
        for (int i = 0; i < steps.length; i++) {
            append("Escalate step " + (i + 1) + ": " + names[i]);
            steps[i].run();
            sleep(200);
            if (EliteWindowFocus.isEliteForeground()) {
                append("SUCCESS at step " + (i + 1) + " (" + names[i] + ")");
                return;
            }
        }
        append("All techniques failed; foreground: " + EliteWindowFocus.foregroundDescription());
    }

    // --- Helpers ------------------------------------------------------------------------------

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void append(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
        System.out.println("[FocusTest] " + line);
    }
}
