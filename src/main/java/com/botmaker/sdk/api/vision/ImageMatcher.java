package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Rect;
import java.util.function.Consumer;

import static com.botmaker.sdk.api.vision.ImageClicker.*;
import static com.botmaker.sdk.api.vision.ImageFinder.*;
import static com.botmaker.sdk.api.vision.ImageWaiter.*;

public class ImageMatcher {

    public static boolean exists(ImageTemplate template) {
        return find(template).isFound();
    }

    public static boolean exists(ImageTemplate template, Rect region) {
        return find(template, region).isFound();
    }

    public static boolean exists(ImageTemplate template, double confidence) {
        return find(template, confidence).isFound();
    }

    public static boolean notExists(ImageTemplate template) {
        return !find(template).isFound();
    }

    public static boolean notExists(ImageTemplate template, Rect region) {
        return !find(template, region).isFound();
    }

    public static boolean existsAny(ImageTemplate... templates) {
        return findAny(templates).isFound();
    }

    public static boolean existsAll(ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (!find(template).isFound()) {
                return false;
            }
        }
        return true;
    }

    public static boolean ifExists(ImageTemplate template, Consumer<MatchResult> action) {
        MatchResult result = find(template);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    public static boolean ifExists(ImageTemplate template,
                                   Consumer<MatchResult> ifAction,
                                   Runnable elseAction) {
        MatchResult result = find(template);
        if (result.isFound()) {
            ifAction.accept(result);
            return true;
        } else {
            elseAction.run();
            return false;
        }
    }

    public static boolean clickAndThen(ImageTemplate template, Runnable action) {
        if (click(template)) {
            action.run();
            return true;
        }
        return false;
    }

    public static void whileExists(ImageTemplate template, Runnable action) {
        while (exists(template)) {
            action.run();
        }
    }

    public static void untilExists(ImageTemplate template, Runnable action) {
        while (!exists(template)) {
            action.run();
        }
    }

    public static boolean clickThenWaitFor(ImageTemplate clickTemplate,
                                           ImageTemplate waitTemplate,
                                           int timeoutSeconds) {
        if (!click(clickTemplate)) {
            return false;
        }

        MatchResult result = waitFor(waitTemplate, timeoutSeconds);
        return result.isFound();
    }

    public static boolean waitForGoneThenClick(ImageTemplate waitTemplate,
                                               ImageTemplate clickTemplate,
                                               int timeoutSeconds) {
        if (!waitUntilGone(waitTemplate, timeoutSeconds)) {
            return false;
        }

        return click(clickTemplate);
    }

    public static boolean clickOrWaitAndClick(ImageTemplate template, int timeoutSeconds) {
        if (click(template)) {
            return true;
        }

        return waitAndClick(template, timeoutSeconds);
    }
}