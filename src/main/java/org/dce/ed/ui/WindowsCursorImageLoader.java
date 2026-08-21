package org.dce.ed.ui;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the standard Windows arrow from its CUR file without substituting a hand-drawn shape. */
final class WindowsCursorImageLoader {

    record CursorImage(BufferedImage image, Point hotspot) {
    }

    private WindowsCursorImageLoader() {
    }

    static CursorImage loadStandardArrow() {
        String windowsDir = System.getenv("WINDIR");
        if (windowsDir == null || windowsDir.isBlank()) {
            return null;
        }
        Path cursor = Path.of(windowsDir, "Cursors", "aero_arrow.cur");
        try {
            return readClosest32PixelEntry(Files.readAllBytes(cursor));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static CursorImage readClosest32PixelEntry(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 22 || data.getShort(2) != 2) {
            return null;
        }
        int count = Short.toUnsignedInt(data.getShort(4));
        int bestOffset = -1;
        int bestSize = Integer.MAX_VALUE;
        int bestWidth = 0;
        int bestHeight = 0;
        int hotspotX = 0;
        int hotspotY = 0;
        for (int i = 0; i < count; i++) {
            int entry = 6 + i * 16;
            if (entry + 16 > bytes.length) {
                return null;
            }
            int width = Byte.toUnsignedInt(bytes[entry]);
            int height = Byte.toUnsignedInt(bytes[entry + 1]);
            width = width == 0 ? 256 : width;
            height = height == 0 ? 256 : height;
            int offset = data.getInt(entry + 12);
            int distance = Math.abs(width - 32) + Math.abs(height - 32);
            if (distance < bestSize) {
                bestSize = distance;
                bestOffset = offset;
                bestWidth = width;
                bestHeight = height;
                hotspotX = Short.toUnsignedInt(data.getShort(entry + 4));
                hotspotY = Short.toUnsignedInt(data.getShort(entry + 6));
            }
        }
        return decode32BitDib(data, bestOffset, bestWidth, bestHeight, hotspotX, hotspotY);
    }

    private static CursorImage decode32BitDib(ByteBuffer data, int offset, int width, int height,
            int hotspotX, int hotspotY) {
        if (offset < 0 || offset + 40 > data.capacity()) {
            return null;
        }
        int headerSize = data.getInt(offset);
        int dibWidth = data.getInt(offset + 4);
        int dibHeight = Math.abs(data.getInt(offset + 8)) / 2;
        int bitsPerPixel = Short.toUnsignedInt(data.getShort(offset + 14));
        if (headerSize < 40 || bitsPerPixel != 32 || dibWidth != width || dibHeight != height) {
            return null;
        }
        int pixels = offset + headerSize;
        int stride = width * 4;
        if (pixels + stride * height > data.capacity()) {
            return null;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sourceRow = pixels + (height - 1 - y) * stride;
            for (int x = 0; x < width; x++) {
                int bgra = data.getInt(sourceRow + x * 4);
                int b = bgra & 0xFF;
                int g = (bgra >>> 8) & 0xFF;
                int r = (bgra >>> 16) & 0xFF;
                int a = (bgra >>> 24) & 0xFF;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return new CursorImage(image, new Point(hotspotX, hotspotY));
    }
}
