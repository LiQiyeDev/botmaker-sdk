package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

import static com.botmaker.sdk.api.vision.ImageFinder.find;

public class ImageWaiter {

    public static MatchResult waitFor(ImageTemplate template, int timeoutSeconds) {
        return waitFor(template, null, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult waitFor(ImageTemplate template, Rect region, int timeoutSeconds) {
        return waitFor(template, region, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult waitFor(ImageTemplate template, Rect region, int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            MatchResult result = find(template, region, confidence);
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

    public static boolean waitUntilGone(ImageTemplate template, int timeoutSeconds) {
        return waitUntilGone(template, null, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitUntilGone(ImageTemplate template, Rect region, int timeoutSeconds, double confidence) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            MatchResult result = find(template, region, confidence);
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

    public static boolean waitAndClick(ImageTemplate template, int timeoutSeconds) {
        return waitAndClick(template, null, timeoutSeconds, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean waitAndClick(ImageTemplate template, Rect region, int timeoutSeconds, double confidence) {
        MatchResult result = waitFor(template, region, timeoutSeconds, confidence);

        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    result.getRandomClickPoint() :
                    result.getCenter();

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