package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

import static com.botmaker.sdk.api.vision.ImageFinder.find;

/**
 * Poll for a template to appear / disappear. Every method mirrors {@link ImageFinder}: a whole-desktop
 * default plus a {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.Rect)}). Matches are returned in absolute coordinates.
 */
public class ImageWaiter {

    // --- waitFor ---

    public static MatchResult waitFor(ImageTemplate template, int timeoutSeconds) {
        return waitFor(template, CaptureSource.desktop(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult waitFor(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitFor(template, CaptureSource.desktop(), timeoutSeconds, confidence);
    }

    public static MatchResult waitFor(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitFor(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /** Core poll: look for {@code template} in {@code source} until found or {@code timeoutSeconds} elapse. */
    public static MatchResult waitFor(ImageTemplate template, CaptureSource source,
                                      int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            MatchResult result = find(template, source, confidence);
            if (result.isFound()) {
                if (ClickConfig.DEBUG_MODE) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println("Found " + template.getId() + " after " + elapsed + "ms");
                }
                return result;
            }
            Wait.milliseconds(100);
        }

        if (ClickConfig.DEBUG_MODE) {
            System.out.println("Timeout waiting for " + template.getId());
        }
        return MatchResult.notFound();
    }

    // --- waitUntilGone ---

    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds) {
        return waitUntilGone(template, CaptureSource.desktop(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitUntilGone(template, CaptureSource.desktop(), timeoutSeconds, confidence);
    }

    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitUntilGone(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitUntilGone(ImageTemplate template, CaptureSource source,
                                        int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            MatchResult result = find(template, source, confidence);
            if (!result.isFound()) {
                if (ClickConfig.DEBUG_MODE) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println(template.getId() + " disappeared after " + elapsed + "ms");
                }
                return true;
            }
            Wait.milliseconds(100);
        }

        if (ClickConfig.DEBUG_MODE) {
            System.out.println("Timeout: " + template.getId() + " still visible");
        }
        return false;
    }

    // --- waitAndClick ---

    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds) {
        return waitAndClick(template, CaptureSource.desktop(), timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds, double confidence) {
        return waitAndClick(template, CaptureSource.desktop(), timeoutSeconds, confidence);
    }

    public static boolean waitAndClick(ImageTemplate template, CaptureSource source, int timeoutSeconds) {
        return waitAndClick(template, source, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitAndClick(ImageTemplate template, CaptureSource source,
                                       int timeoutSeconds, double confidence) {
        MatchResult result = waitFor(template, source, timeoutSeconds, confidence);
        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? result.getRandomClickPoint() : result.getCenter();
            Mouse.click(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Found and clicked " + template.getId());
            }
            return true;
        }
        return false;
    }
}
