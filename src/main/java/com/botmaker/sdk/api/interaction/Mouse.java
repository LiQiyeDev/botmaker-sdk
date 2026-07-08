package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

public class Mouse {

    private static NativeController controller() {
        return NativeControllerFactory.get();
    }

    /**
     * Left-clicks at specific coordinates on the screen. Uses the low-level no-cursor-move path
     * (PostMessage on Windows / XTest on Linux) for compatibility with existing behaviour.
     */
    public static void click(Point p) {
        if (p == null) return;
        // Delegate to internal implementation, explicitly casting double coordinates to int
        controller().postLeftClickScreen((int) p.x, (int) p.y);
    }

    public static void click(int x, int y) {
        click(new Point(x, y));
    }

    /**
     * Left-clicks at {@code (x, y)} relative to a {@link CaptureSource}'s {@link CaptureSource#origin() origin}
     * — a fixed point inside a window (or a monitor / region), independent of where that surface currently
     * sits on the desktop. Equivalent to clicking {@code source.origin() + (x, y)} in absolute coordinates.
     */
    public static void click(CaptureSource source, int x, int y) {
        if (source == null) return;
        Point o = source.origin();
        click((int) o.x + x, (int) o.y + y);
    }

    // --- Movement ---

    /** Move the cursor to an absolute screen coordinate. */
    public static void move(Point p) {
        if (p == null) return;
        controller().mouseMove((int) p.x, (int) p.y);
    }

    public static void moveTo(int x, int y) {
        controller().mouseMove(x, y);
    }

    // --- Button actions ---

    /** Hold a mouse button down at the current cursor position. */
    public static void down(MouseButton button) {
        controller().mouseButton(button.code(), true);
    }

    /** Release a held mouse button. */
    public static void up(MouseButton button) {
        controller().mouseButton(button.code(), false);
    }

    private static void clickButtonAt(Point p, MouseButton button) {
        if (p != null) move(p);
        down(button);
        up(button);
    }

    public static void rightClick(Point p) {
        clickButtonAt(p, MouseButton.RIGHT);
    }

    public static void middleClick(Point p) {
        clickButtonAt(p, MouseButton.MIDDLE);
    }

    /** Two quick left presses at the given point. */
    public static void doubleClick(Point p) {
        if (p != null) move(p);
        down(MouseButton.LEFT);
        up(MouseButton.LEFT);
        down(MouseButton.LEFT);
        up(MouseButton.LEFT);
    }

    /** Press-and-hold at {@code start}, move to {@code end}, then release (a drag). */
    public static void drag(Point start, Point end) {
        move(start);
        down(MouseButton.LEFT);
        move(end);
        up(MouseButton.LEFT);
    }

    /** Scroll the wheel: positive = up/away, negative = down/toward. */
    public static void scroll(int amount) {
        controller().scroll(amount);
    }
}