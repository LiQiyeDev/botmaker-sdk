package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.internal.capture.Clicker;

public class Mouse {

    /**
     * Clicks at specific coordinates on the screen.
     */
    public static void click(Point p) {
        if (p == null) return;
        // Delegate to internal implementation, explicitly casting double coordinates to int
        Clicker.postLeftClickScreen((int) p.x, (int) p.y);
    }

    public static void click(int x, int y) {
        click(new Point(x, y));
    }
}