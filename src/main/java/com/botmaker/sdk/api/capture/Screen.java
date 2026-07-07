package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.internal.capture.ScreenCapture;
import java.awt.image.BufferedImage;

public class Screen {
    /**
     * Captures the entire desktop.
     */
    public static BufferedImage capture() {
        return ScreenCapture.captureDesktop();
    }

    /**
     * Absolute screen coordinate of pixel (0,0) of the image returned by {@link #capture()}.
     * On a multi-monitor desktop the virtual-screen origin can be non-zero (e.g. a monitor placed
     * to the left of / above the primary gives negative coordinates), so this offset must be added
     * to in-image match coordinates to obtain absolute coordinates suitable for clicking.
     */
    public static Point captureOrigin() {
        java.awt.Point origin = ScreenCapture.getVirtualScreenBounds().getLocation();
        return new Point(origin.x, origin.y);
    }

    /**
     * A single monitor (0-based {@code index} into the OS screen-device list) as a {@link CaptureSource},
     * so a bot can match against just one screen on a multi-monitor desktop. In-image match coordinates are
     * converted to absolute, clickable coordinates by adding {@link CaptureSource#origin()} (the monitor's
     * top-left in virtual-screen space). An out-of-range index falls back to the whole desktop.
     */
    public static CaptureSource at(int index) {
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                return ScreenCapture.captureMonitor(index);
            }

            @Override
            public Point origin() {
                java.awt.Rectangle b = ScreenCapture.monitorBounds(index);
                return new Point(b.x, b.y);
            }
        };
    }

    /**
     * The whole desktop as a {@link CaptureSource}, so matchers can treat the screen and a
     * {@link Window} uniformly. Delegates to {@link #capture()} / {@link #captureOrigin()}.
     */
    public static CaptureSource asSource() {
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                return Screen.capture();
            }

            @Override
            public Point origin() {
                return Screen.captureOrigin();
            }
        };
    }
}