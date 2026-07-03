package org.dce.ed;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.JDialog;
import javax.swing.JFrame;

import org.dce.ed.exec.ExecTriggerService;
import org.junit.jupiter.api.Test;

class ExecTriggerServiceResolverTest {

    @Test
    void resolveExecTriggerService_fromOverlayFrame() {
        OverlayContentPanel content = new OverlayContentPanel(() -> false);
        OverlayFrame frame = new OverlayFrame(content);
        ExecTriggerService service = frame.getExecTriggerService();

        assertSame(service, PreferencesDialog.resolveExecTriggerService(frame));
    }

    @Test
    void resolveExecTriggerService_fromDecoratedOverlayDialog() {
        OverlayContentPanel content = new OverlayContentPanel(() -> false);
        OverlayFrame frame = new OverlayFrame(content);
        DecoratedOverlayDialog decorated = new DecoratedOverlayDialog(content, "EDO");
        decorated.setPersistenceDelegate(frame);
        ExecTriggerService service = frame.getExecTriggerService();

        assertSame(service, PreferencesDialog.resolveExecTriggerService(decorated));
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
