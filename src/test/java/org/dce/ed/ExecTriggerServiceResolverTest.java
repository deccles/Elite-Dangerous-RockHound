package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;

import javax.swing.JDialog;
import javax.swing.JFrame;

import org.dce.ed.exec.ExecTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class ExecTriggerServiceResolverTest {

    static {
        TestEnvironment.ensureTestIsolation();
    }

    @BeforeEach
    void assumeDisplay() {
        assumeFalse(
                GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadless(),
                "Requires a display (Swing windows)");
    }

    @Test
    void resolveExecTriggerService_fromOverlayFrame() {
        OverlayContentPanel content = new OverlayContentPanel(() -> false);
        OverlayFrame frame = new OverlayFrame(content);
        try {
            ExecTriggerService service = frame.getExecTriggerService();
            assertSame(service, PreferencesDialog.resolveExecTriggerService(frame));
        } finally {
            frame.dispose();
        }
    }

    @Test
    void resolveExecTriggerService_fromDecoratedOverlayDialog() {
        OverlayContentPanel content = new OverlayContentPanel(() -> false);
        OverlayFrame frame = new OverlayFrame(content);
        DecoratedOverlayDialog decorated = new DecoratedOverlayDialog(content, "EDO");
        decorated.setPersistenceDelegate(frame);
        try {
            ExecTriggerService service = frame.getExecTriggerService();
            assertSame(service, PreferencesDialog.resolveExecTriggerService(decorated));
        } finally {
            decorated.dispose();
            frame.dispose();
        }
    }

    @Test
    void resolveExecTriggerService_returnsNullForUnrelatedWindow() {
        JFrame other = new JFrame();
        try {
            assertNull(PreferencesDialog.resolveExecTriggerService(other));
        } finally {
            other.dispose();
        }
    }

    @Test
    void resolveExecTriggerService_walksOwnerChain() {
        OverlayContentPanel content = new OverlayContentPanel(() -> false);
        OverlayFrame frame = new OverlayFrame(content);
        DecoratedOverlayDialog decorated = new DecoratedOverlayDialog(frame, content, "EDO");
        decorated.setPersistenceDelegate(frame);
        JDialog child = new JDialog(decorated);
        try {
            assertNotNull(PreferencesDialog.resolveExecTriggerService(child));
            assertSame(frame.getExecTriggerService(), PreferencesDialog.resolveExecTriggerService(child));
        } finally {
            child.dispose();
            decorated.dispose();
            frame.dispose();
        }
    }
}
