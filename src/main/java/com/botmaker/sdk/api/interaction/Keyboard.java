package com.botmaker.sdk.api.interaction;

import com.botmaker.shared.capture.NativeControllerFactory;

/**
 * Simulated keyboard input. Keys are expressed with the OS-neutral {@link Key} enum; combos and
 * typing are provided for convenience. Backed per-OS by XTest (Linux) / {@code keybd_event}
 * (Windows) through the internal native controller.
 */
public class Keyboard {

    /** Press and hold a key (remember to {@link #release}). */
    public static void press(Key key) {
        NativeControllerFactory.get().keyDown(key.nativeCode());
    }

    /** Release a held key. */
    public static void release(Key key) {
        NativeControllerFactory.get().keyUp(key.nativeCode());
    }

    /** Press then release a key. */
    public static void tap(Key key) {
        press(key);
        release(key);
    }

    /**
     * Press a chord: hold every key in order, then release them in reverse order — e.g.
     * {@code Keyboard.combo(Key.CTRL, Key.C)} for copy.
     */
    public static void combo(Key... keys) {
        for (Key key : keys) {
            press(key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            release(keys[i]);
        }
    }

    /**
     * Type a string, handling shifting for uppercase / shifted characters. Best effort for
     * non-ASCII input (falls back to the platform's keysym/VK mapping).
     */
    public static void type(String text) {
        NativeControllerFactory.get().typeText(text);
    }
}
