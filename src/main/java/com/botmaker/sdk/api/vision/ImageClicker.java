package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.core.Direction;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;

import java.util.Comparator;
import java.util.List;

import static com.botmaker.sdk.api.vision.ImageFinder.*;

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

    public static boolean clickBest(ImageTemplate... templates) {
        return clickBest(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickBest(Rect region, ImageTemplate... templates) {
        return clickBest(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickBest(Rect region, double confidence, ImageTemplate... templates) {
        MatchResult best = findBest(region, confidence, templates);

        if (best.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    best.getRandomClickPoint() :
                    best.getCenter();

            Mouse.click(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);

            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Clicked best match: " + best.getTemplateId() +
                        " (confidence: " + String.format("%.3f", best.getConfidence()) + ")");
            }

            return true;
        }

        Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
        return false;
    }

    public static boolean clickFirst(ImageTemplate template, Direction direction, int index) {
        return clickFirst(template, null, direction, index, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean clickFirst(ImageTemplate template, Rect region, Direction direction,
                                     int index, double confidence) {
        List<MatchResult> matches = findAll(template, region, confidence);

        if (matches.isEmpty()) {
            Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
            return false;
        }

        // Sort by direction using Double comparator or casting to int correctly
        switch (direction) {
            case NORTH:  // Top to bottom
                matches.sort(Comparator.comparingDouble(m -> m.getCenter().y));
                break;
            case SOUTH:  // Bottom to top
                matches.sort(Comparator.comparingDouble(m -> -m.getCenter().y));
                break;
            case EAST:   // Left to right
                matches.sort(Comparator.comparingDouble(m -> m.getCenter().x));
                break;
            case WEST:   // Right to left
                matches.sort(Comparator.comparingDouble(m -> -m.getCenter().x));
                break;
        }

        if (index >= matches.size()) {
            Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
            return false;
        }

        MatchResult target = matches.get(index);
        Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                target.getRandomClickPoint() :
                target.getCenter();

        Mouse.click(clickPoint);
        Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);

        if (ClickConfig.DEBUG_MODE) {
            System.out.println("Clicked match #" + index + " (" + direction + ") of " +
                    template.getId() + " at " + clickPoint);
        }

        return true;
    }

    public static boolean clickUntilSuccess(ImageTemplate template, int maxAttempts) {
        return clickUntilSuccess(template, null, maxAttempts, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static boolean clickUntilSuccess(ImageTemplate template, Rect region, int maxAttempts, double confidence) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (click(template, region, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
                return true;
            }

            if (ClickConfig.DEBUG_MODE && attempt > 0 && attempt % 5 == 0) {
                System.out.println("Still trying to click " + template.getId() +
                        " (attempt " + (attempt + 1) + "/" + maxAttempts + ")");
            }
        }

        if (ClickConfig.DEBUG_MODE) {
            System.out.println("Failed to click " + template.getId() + " after " + maxAttempts + " attempts");
        }

        return false;
    }

    public static int clickWhileVisible(ImageTemplate template, int maxClicks) {
        return clickWhileVisible(template, null, maxClicks, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickWhileVisible(ImageTemplate template, Rect region, int maxClicks, double confidence) {
        int clickCount = 0;

        while (clickCount < maxClicks) {
            if (!click(template, region, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
                break; // Template no longer visible
            }
            clickCount++;
        }

        if (ClickConfig.DEBUG_MODE && clickCount > 0) {
            System.out.println("Clicked " + template.getId() + " " + clickCount + " times");
        }

        return clickCount;
    }
}