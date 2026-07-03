package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;

import java.awt.image.BufferedImage;

/**
 * A source of pixels the vision layer can match against — either the whole {@link Screen} or a
 * single {@link Window}. Abstracting capture behind this seam lets {@code ImageFinder} target a
 * specific window (even off-screen or on a second monitor)
 * without duplicating any matching logic: the matcher runs on {@link #capture()} and converts
 * in-image match coordinates to absolute screen coordinates by adding {@link #origin()}.
 */
public interface CaptureSource {

    /** Pixels of this source. May return {@code null} if the capture failed. */
    BufferedImage capture();

    /**
     * Absolute screen coordinate of pixel (0,0) of the image returned by {@link #capture()}.
     * Add this to an in-image match location to obtain an absolute, clickable coordinate.
     */
    Point origin();

    /** The whole virtual desktop (all monitors). This is the default source for every matcher. */
    static CaptureSource screen() {
        return Screen.asSource();
    }
}
