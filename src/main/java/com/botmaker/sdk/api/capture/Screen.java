package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.internal.capture.ScreenCapture;
import java.awt.image.BufferedImage;

public class Screen {
    /**
     * Captures the entire desktop.
     */
    public static BufferedImage capture() {
        return ScreenCapture.captureDesktop();
    }
}