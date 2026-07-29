package org.dce.ed.util;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import org.dce.ed.OverlayFrame;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows does not support {@link java.awt.Taskbar.Feature#ICON_IMAGE}; the shell often uses per-HWND icons from
 * {@code WM_SETICON} instead. Undecorated frames in particular may never get a correct taskbar glyph from AWT alone.
 * <p>
 * Icons are built with {@link GDI32#CreateDIBSection} (32-bpp BGRA + alpha) and {@code ICONINFO.hbmMask = null},
 * which Vista+ uses for alpha-blended taskbar pins. The older {@code CreateBitmap(32bpp)} path ignores alpha and
 * an uninitialized monochrome mask produces white/opaque corners.
 */
final class WindowsWindowIcons {

    private static final int WM_SETICON = 0x0080;

    private interface User32Extra extends Library {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class, W32APIOptions.DEFAULT_OPTIONS);

        HICON CreateIconIndirect(WinGDI.ICONINFO iconinfo);
    }

    private WindowsWindowIcons() {
    }

    static void refreshAfterAppIcon(Window window, BufferedImage prepared) {
        if (window == null || prepared == null) {
            return;
        }
        if (!isWindows()) {
            return;
        }
        AtomicBoolean applied = new AtomicBoolean(false);
        Runnable task = () -> {
            if (!applied.compareAndSet(false, true)) {
                return;
            }
            applyPreparedIconToHwnd(window, prepared);
        };
        if (window.isDisplayable() && window.isShowing()) {
            SwingUtilities.invokeLater(task);
        }
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                window.removeWindowListener(this);
                SwingUtilities.invokeLater(task);
            }
        });
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).startsWith("win");
    }

    private static void applyPreparedIconToHwnd(Window window, BufferedImage prepared) {
        if (!window.isDisplayable()) {
            return;
        }
        Pointer wp;
        try {
            wp = Native.getWindowPointer(window);
        } catch (Throwable ignored) {
            return;
        }
        if (wp == null) {
            return;
        }
        HWND hwnd = new HWND(wp);

        int cxBig = Math.max(1, User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXICON));
        int cyBig = Math.max(1, User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYICON));
        int cxSm = Math.max(1, User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSMICON));
        int cySm = Math.max(1, User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSMICON));

        BufferedImage imgBig = scaleExact(prepared, cxBig, cyBig);
        BufferedImage imgSm = scaleExact(prepared, cxSm, cySm);

        HICON hBig = createArgbIcon(imgBig);
        HICON hSm = createArgbIcon(imgSm);
        if (hBig == null || hSm == null) {
            if (hBig != null) {
                User32.INSTANCE.DestroyIcon(hBig);
            }
            if (hSm != null) {
                User32.INSTANCE.DestroyIcon(hSm);
            }
            return;
        }
        HICON hSm2 = User32.INSTANCE.CopyIcon(hSm);
        try {
            User32.INSTANCE.SendMessage(hwnd, WM_SETICON, new WPARAM(WinUser.ICON_BIG), new LPARAM(Pointer.nativeValue(hBig.getPointer())));
            User32.INSTANCE.SendMessage(hwnd, WM_SETICON, new WPARAM(WinUser.ICON_SMALL), new LPARAM(Pointer.nativeValue(hSm.getPointer())));
            if (hSm2 != null) {
                User32.INSTANCE.SendMessage(hwnd, WM_SETICON, new WPARAM(WinUser.ICON_SMALL2), new LPARAM(Pointer.nativeValue(hSm2.getPointer())));
            }
        } finally {
            User32.INSTANCE.DestroyIcon(hBig);
            User32.INSTANCE.DestroyIcon(hSm);
            if (hSm2 != null) {
                User32.INSTANCE.DestroyIcon(hSm2);
            }
        }
        // WM_SETICON runs after initial pass-through setup; re-stamp so child HWNDs stay click-through.
        if (window instanceof OverlayFrame overlay) {
            overlay.reapplyNativeMousePassThroughIfEnabled();
        }
    }

    private static BufferedImage scaleExact(BufferedImage src, int tw, int th) {
        if (src.getWidth() == tw && src.getHeight() == th) {
            return src;
        }
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(tw, th, type);
        Graphics2D g2 = dst.createGraphics();
        try {
            Composite prev = g2.getComposite();
            try {
                g2.setComposite(AlphaComposite.Src);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(src, 0, 0, tw, th, null);
            } finally {
                g2.setComposite(prev);
            }
        } finally {
            g2.dispose();
        }
        return dst;
    }

    /**
     * 32-bpp top-down DIB with per-pixel alpha; {@code hbmMask} is null (Vista+).
     */
    private static HICON createArgbIcon(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage argb = img;
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            try {
                g.setComposite(AlphaComposite.Src);
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
        }

        // JNA forbids zero-length Structure arrays; for 32-bpp BI_RGB the palette is unused (Windows ignores it).
        WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO(1);
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

        PointerByReference ppvBits = new PointerByReference();
        HBITMAP hbmColor = GDI32.INSTANCE.CreateDIBSection(null, bmi, WinGDI.DIB_RGB_COLORS, ppvBits, null, 0);
        if (hbmColor == null || Pointer.nativeValue(hbmColor.getPointer()) == 0) {
            return null;
        }
        Pointer bits = ppvBits.getValue();
        if (bits == null) {
            GDI32.INSTANCE.DeleteObject(hbmColor);
            return null;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int offset = (y * w + x) * 4;
                bits.setInt(offset, argb.getRGB(x, y));
            }
        }

        WinGDI.ICONINFO ii = new WinGDI.ICONINFO();
        ii.fIcon = true;
        ii.xHotspot = 0;
        ii.yHotspot = 0;
        ii.hbmMask = null;
        ii.hbmColor = hbmColor;

        HICON icon;
        try {
            icon = User32Extra.INSTANCE.CreateIconIndirect(ii);
        } finally {
            GDI32.INSTANCE.DeleteObject(hbmColor);
        }
        return icon;
    }
}
