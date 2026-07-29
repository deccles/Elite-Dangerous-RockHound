package org.dce.ed.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;

/**
 * Syncs overlay always-on-top with Elite Dangerous foreground state.
 * <p>
 * Primary path: Win32 {@code EVENT_SYSTEM_FOREGROUND} via {@code SetWinEventHook}, so AOT
 * updates as soon as focus moves (Alt-Tab, click Chrome, etc.). A slow backup poll covers
 * missed events; if the hook fails to install, a faster poll is used instead.
 * <p>
 * Clearing AOT does not bury EDO by itself — the newly focused app comes forward normally.
 */
public final class OverlayFocusAlwaysOnTop {

    private static final int EVENT_SYSTEM_FOREGROUND = 0x0003;
    private static final int WINEVENT_OUTOFCONTEXT = 0x0000;
    private static final int WINEVENT_SKIPOWNPROCESS = 0x0002;

    /** Safety net when the foreground hook is active. */
    private static final int BACKUP_POLL_MS = 2_000;
    /** Used only if SetWinEventHook fails. */
    private static final int FALLBACK_POLL_MS = 50;

    private final Runnable sync;
    private final Timer pollTimer;
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private final AtomicBoolean syncQueued = new AtomicBoolean(false);

    /** Strong ref so the GC cannot collect the native callback stub. */
    private volatile WinUser.WinEventProc winEventProc;
    private volatile HANDLE hookHandle;
    private volatile Thread hookThread;
    private volatile int hookThreadId;

    public OverlayFocusAlwaysOnTop(Runnable sync) {
        this.sync = Objects.requireNonNull(sync, "sync");
        this.pollTimer = new Timer(BACKUP_POLL_MS, e -> queueSync());
        this.pollTimer.setRepeats(true);
    }

    public void start() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::start);
            return;
        }
        if (!stopped.getAndSet(false)) {
            return; // already running
        }
        queueSync();
        startHookThread();
        pollTimer.setDelay(BACKUP_POLL_MS);
        pollTimer.start();
    }

    public void stop() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::stop);
            return;
        }
        if (stopped.getAndSet(true)) {
            return; // already stopped
        }
        pollTimer.stop();
        int tid = hookThreadId;
        if (tid != 0) {
            try {
                User32.INSTANCE.PostThreadMessage(tid, WinUser.WM_QUIT, new WPARAM(0), new LPARAM(0));
            } catch (Exception ignored) {
            }
        }
        Thread t = hookThread;
        if (t != null) {
            try {
                t.join(1_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        hookThread = null;
        hookThreadId = 0;
        winEventProc = null;
        hookHandle = null;
    }

    /** Re-run sync immediately (e.g. after mode switch or preference change). */
    public void refresh() {
        queueSync();
    }

    private void startHookThread() {
        Thread t = new Thread(this::hookThreadMain, "edo-foreground-aot");
        t.setDaemon(true);
        hookThread = t;
        t.start();
    }

    private void hookThreadMain() {
        hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();

        // Must keep this field assigned for the lifetime of the hook (GC root for JNA stub).
        winEventProc = (hWinEventHook, event, hwnd, idObject, idChild, dwEventThread, dwmsEventTime) -> {
            if (!stopped.get() && event != null && event.intValue() == EVENT_SYSTEM_FOREGROUND) {
                queueSync();
            }
        };

        HANDLE hook = User32.INSTANCE.SetWinEventHook(
                EVENT_SYSTEM_FOREGROUND,
                EVENT_SYSTEM_FOREGROUND,
                null,
                winEventProc,
                0,
                0,
                WINEVENT_OUTOFCONTEXT | WINEVENT_SKIPOWNPROCESS);
        hookHandle = hook;

        if (hook == null) {
            // No message loop needed; switch poll to a snappy fallback.
            SwingUtilities.invokeLater(() -> {
                if (!stopped.get()) {
                    pollTimer.setDelay(FALLBACK_POLL_MS);
                    if (!pollTimer.isRunning()) {
                        pollTimer.start();
                    }
                }
            });
            return;
        }

        WinUser.MSG msg = new WinUser.MSG();
        while (!stopped.get()) {
            int result = User32.INSTANCE.GetMessage(msg, null, 0, 0);
            if (result <= 0) {
                break; // WM_QUIT or error
            }
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }

        try {
            User32.INSTANCE.UnhookWinEvent(hook);
        } catch (Exception ignored) {
        }
        if (hookHandle == hook) {
            hookHandle = null;
        }
    }

    private void queueSync() {
        if (stopped.get()) {
            return;
        }
        // Coalesce bursts of foreground events onto one EDT sync.
        if (!syncQueued.compareAndSet(false, true)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            syncQueued.set(false);
            if (!stopped.get()) {
                sync.run();
            }
        });
    }
}
