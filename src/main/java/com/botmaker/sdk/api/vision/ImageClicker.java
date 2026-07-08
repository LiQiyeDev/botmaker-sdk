package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.ClickEvent;
import com.botmaker.sdk.api.observe.Surface;

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
            emitClick(clickPoint);
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

    // --- Group / best / compare clicking ---

    /** Click the first template in {@code group} (in order) that clears the threshold. */
    public static boolean click(ImageTemplateGroup group) {
        return clickAny(null, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static boolean click(ImageTemplateGroup group, Rect region) {
        return clickAny(region, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static boolean click(ImageTemplateGroup group, Rect region, double confidence) {
        return clickAny(region, confidence, group.toArray());
    }

    /** Click the globally highest-scoring match for {@code template}. */
    public static boolean clickBest(ImageTemplate template) {
        return clickResult(ImageFinder.findBest(template));
    }

    /** Click the single highest-scoring match across every template in {@code group}. */
    public static boolean clickBest(ImageTemplateGroup group) {
        return clickResult(ImageFinder.findBest(group));
    }

    /** Click {@code good} only if it out-scores {@code bad} at the same location by the default margin. */
    public static boolean clickCompare(ImageTemplate good, ImageTemplate bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    /** Click {@code good} only if it beats every distractor in {@code bad} at its location. */
    public static boolean clickCompare(ImageTemplate good, ImageTemplate... bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    /** Click the best {@code good} template that beats every {@code bad} template at its location. */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    /** Click a match already located (used by {@code clickBest}/{@code clickCompare}). */
    private static boolean clickResult(MatchResult result) {
        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    result.getRandomClickPoint() :
                    result.getCenter();

            Mouse.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);

            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Clicked " + result.getTemplateId() + " at " + clickPoint +
                        " (confidence: " + String.format("%.3f", result.getConfidence()) + ")");
            }
            return true;
        } else {
            Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
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
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }

        if (ClickConfig.DEBUG_MODE && matches.size() > 0) {
            System.out.println("Clicked " + matches.size() + " instances of " + template.getId());
        }

        return matches.size();
    }

    /**
     * Reports a left click to registered {@link BotObserver}s (see {@code api.observe.Bots}). The
     * template-click helpers always left-click at an absolute screen coordinate. Guarded by
     * {@code hasObservers()} so a normal bot run pays nothing.
     */
    private static void emitClick(Point clickPoint) {
        if (Bots.hasObservers()) {
            Bots.fireClick(new ClickEvent(Surface.ofScreen(), clickPoint, ClickEvent.LEFT));
        }
    }

    // =====================================================================================
    // CaptureSource-targeted overloads: locate the template within a specific window / monitor,
    // then click at the resulting absolute screen coordinate. Mirrors the screen-default family.
    // =====================================================================================

    public static boolean click(ImageTemplate template, CaptureSource source) {
        return click(template, source, null, ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplate template, CaptureSource source, Rect region, double confidence, int delayMs) {
        return clickResult(find(template, source, region, confidence),
                delayMs > 0 ? delayMs : ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplateGroup group, CaptureSource source) {
        return clickAny(source, null, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static boolean clickBest(ImageTemplate template, CaptureSource source) {
        return clickResult(ImageFinder.findBest(template, source));
    }

    public static boolean clickBest(ImageTemplateGroup group, CaptureSource source) {
        return clickResult(ImageFinder.findBest(group, source));
    }

    public static boolean clickAny(CaptureSource source, ImageTemplate... templates) {
        return clickAny(source, null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickAny(CaptureSource source, Rect region, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (click(template, source, region, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
                return true;
            }
        }
        return false;
    }

    public static int clickAll(ImageTemplate template, CaptureSource source) {
        return clickAll(template, source, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickAll(ImageTemplate template, CaptureSource source, Rect region, double confidence) {
        List<MatchResult> matches = findAll(template, source, region, confidence);
        for (MatchResult match : matches) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? match.getRandomClickPoint() : match.getCenter();
            Mouse.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }
        return matches.size();
    }

    /** {@link #clickResult(MatchResult)} with an explicit found-delay (source-targeted click path). */
    private static boolean clickResult(MatchResult result, int delayMs) {
        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? result.getRandomClickPoint() : result.getCenter();
            Mouse.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(delayMs);
            return true;
        }
        Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
        return false;
    }
}
