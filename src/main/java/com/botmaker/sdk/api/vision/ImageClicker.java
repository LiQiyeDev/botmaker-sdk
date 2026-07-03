package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

import java.util.List;

import static com.botmaker.sdk.api.vision.ImageFinder.find;
import static com.botmaker.sdk.api.vision.ImageFinder.findAll;

public class ImageClicker {

    public static boolean click(ImageTemplate template) {
        return click(template, null, ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplate template, int delayMs) {
        return click(template, null, ClickConfig.DEFAULT_CONFIDENCE, delayMs);
    }

    public static boolean click(ImageTemplate template, Rect region) {
        return click(template, region, ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplate template, Rect region, double confidence, int delayMs) {
        MatchResult result = find(template, region, confidence);

        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    result.getRandomClickPoint() :
                    result.getCenter();

            Mouse.click(clickPoint);
            Wait.milliseconds(delayMs > 0 ? delayMs : ClickConfig.DEFAULT_FOUND_DELAY);

            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Clicked " + template.getId() + " at " + clickPoint +
                        " (confidence: " + String.format("%.3f", result.getConfidence()) + ")");
            }

            return true;
        } else {
            Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);

            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Template " + template.getId() + " not found");
            }

            return false;
        }
    }

    public static boolean clickAny(ImageTemplate... templates) {
        return clickAny(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickAny(Rect region, ImageTemplate... templates) {
        return clickAny(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickAny(Rect region, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (click(template, region, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
                return true;
            }
        }
        return false;
    }

    public static int clickAll(ImageTemplate template) {
        return clickAll(template, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickAll(ImageTemplate template, Rect region) {
        return clickAll(template, region, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickAll(ImageTemplate template, Rect region, double confidence) {
        List<MatchResult> matches = findAll(template, region, confidence);

        for (MatchResult match : matches) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    match.getRandomClickPoint() :
                    match.getCenter();

            Mouse.click(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }

        if (ClickConfig.DEBUG_MODE && matches.size() > 0) {
            System.out.println("Clicked " + matches.size() + " instances of " + template.getId());
        }

        return matches.size();
    }
}
