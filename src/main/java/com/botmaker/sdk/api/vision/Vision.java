package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Screen;

import java.util.function.Consumer;

/**
 * High-level, lambda-friendly vision entry point for decision trees. {@link #evaluate} captures a
 * source <b>once</b>, checks every template against that single frame, and hands the resulting
 * {@link ImageState.ScreenState} to a callback — so a bot can branch over what is on screen without
 * issuing (and paying for) a separate capture per template:
 *
 * <pre>{@code
 * Vision.evaluate(gameWindow, btnOk, btnCancel, state -> {
 *     if (state.has(btnOk)) {
 *         state.click(btnOk);
 *     } else if (state.has(btnCancel)) {
 *         // fallback logic
 *     }
 * });
 * }</pre>
 *
 * The {@link CaptureSource} may be the whole {@link Screen} or a specific
 * {@link com.botmaker.sdk.api.capture.Window Window}; the state's coordinates are absolute and
 * clickable in either case.
 */
public class Vision {

    /** Evaluate the templates against the whole screen and branch in the callback. */
    public static void evaluate(Consumer<ImageState.ScreenState> callback, ImageTemplate... templates) {
        evaluate(Screen.asSource(), callback, templates);
    }

    /** Evaluate the templates against {@code source} (screen or window) and branch in the callback. */
    public static void evaluate(CaptureSource source, Consumer<ImageState.ScreenState> callback,
                                ImageTemplate... templates) {
        evaluate(source, ClickConfig.DEFAULT_CONFIDENCE, callback, templates);
    }

    public static void evaluate(CaptureSource source, double confidence,
                                Consumer<ImageState.ScreenState> callback, ImageTemplate... templates) {
        ImageState.ScreenState state = ImageState.checkState(source, confidence, templates);
        callback.accept(state);
    }

    /**
     * Like {@link #evaluate} but returns the captured {@link ImageState.ScreenState} instead of
     * taking a callback — for callers that prefer to branch inline.
     */
    public static ImageState.ScreenState snapshot(CaptureSource source, ImageTemplate... templates) {
        return ImageState.checkState(source, templates);
    }
}
