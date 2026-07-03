package com.botmaker.sdk.api.interaction;

/** Mouse buttons for the richer {@link Mouse} actions (down/up/drag). */
public enum MouseButton {
    LEFT(1), MIDDLE(2), RIGHT(3);

    private final int code;

    MouseButton(int code) {
        this.code = code;
    }

    /** Native button number (1=left, 2=middle, 3=right) understood by the controllers. */
    public int code() {
        return code;
    }
}
