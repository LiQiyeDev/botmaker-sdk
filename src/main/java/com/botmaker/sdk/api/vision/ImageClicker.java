package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.ClickEvent;
import com.botmaker.sdk.api.observe.Surface;

import java.util.List;

import static com.botmaker.sdk.api.vision.ImageFinder.find;
import static com.botmaker.sdk.api.vision.ImageFinder.findAll;

/**
 * Locate a template and click it. Every method mirrors {@link ImageFinder}: a whole-desktop default plus a
 * {@link CaptureSource} form (window / monitor / desktop, optionally narrowed with
 * {@link CaptureSource#region(com.botmaker.sdk.api.Rect)}), so a click can be pinned to a specific surface.
 * The template is located within the source; the click lands at the resulting absolute screen coordinate.
 */
public class ImageClicker {

    // --- click (single template) ---

    public static boolean click(ImageTemplate template) {
        return click(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplate template, int delayMs) {
        return click(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE, delayMs);
    }

    public static boolean click(ImageTemplate template, CaptureSource source) {
        return click(template, source, ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    public static boolean click(ImageTemplate template, CaptureSource source, int delayMs) {
        return click(template, source, ClickConfig.DEFAULT_CONFIDENCE, delayMs);
    }

    public static boolean click(ImageTemplate template, CaptureSource source, double confidence) {
        return click(template, source, confidence, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    /** Core single-template click: locate in {@code source}, click the match, wait {@code delayMs}. */
    public static boolean click(ImageTemplate template, CaptureSource source, double confidence, int delayMs) {
        return clickResult(find(template, source, confidence),
                delayMs > 0 ? delayMs : ClickConfig.DEFAULT_FOUND_DELAY);
    }

    // --- Group clicking (first template, in order, that clears the threshold) ---

    /** Click the first template in {@code group} (in order) that clears the threshold. */
    public static boolean click(ImageTemplateGroup group) {
        return clickAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static boolean click(ImageTemplateGroup group, double confidence) {
        return clickAny(Source.current(), confidence, group.toArray());
    }

    public static boolean click(ImageTemplateGroup group, CaptureSource source) {
        return clickAny(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static boolean click(ImageTemplateGroup group, CaptureSource source, double confidence) {
        return clickAny(source, confidence, group.toArray());
    }

    // --- Best clicking ---

    /** Click the globally highest-scoring match for {@code template} ({@code find} already returns the best). */
    public static boolean clickBest(ImageTemplate template) {
        return clickResult(ImageFinder.find(template));
    }

    public static boolean clickBest(ImageTemplate template, CaptureSource source) {
        return clickResult(ImageFinder.find(template, source));
    }

    public static boolean clickBest(ImageTemplate template, CaptureSource source, double confidence) {
        return clickResult(ImageFinder.find(template, source, confidence));
    }

    /** Click the single highest-scoring match across every template in {@code group}. */
    public static boolean clickBest(ImageTemplateGroup group) {
        return clickResult(ImageFinder.findBest(group));
    }

    public static boolean clickBest(ImageTemplateGroup group, CaptureSource source) {
        return clickResult(ImageFinder.findBest(group, source));
    }

    public static boolean clickBest(ImageTemplateGroup group, CaptureSource source, double confidence) {
        return clickResult(ImageFinder.findBest(group, source, confidence));
    }

    // --- Compare clicking (mirrors ImageFinder.findCompare, including the CaptureSource forms) ---

    /** Click {@code good} only if it out-scores {@code bad} at the same location by the default margin. */
    public static boolean clickCompare(ImageTemplate good, ImageTemplate bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    /** Click {@code good} only if it beats every distractor in {@code bad} at its location. */
    public static boolean clickCompare(ImageTemplate good, ImageTemplate... bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    public static boolean clickCompare(ImageTemplate good, ImageTemplate bad, CaptureSource source) {
        return clickResult(ImageFinder.findCompare(good, bad, source));
    }

    public static boolean clickCompare(ImageTemplate good, CaptureSource source, ImageTemplate... bad) {
        return clickResult(ImageFinder.findCompare(good, source, bad));
    }

    /** Click the best {@code good} template that beats every {@code bad} template at its location. */
    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return clickResult(ImageFinder.findCompare(good, bad));
    }

    public static boolean clickCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        return clickResult(ImageFinder.findCompare(good, bad, source));
    }

    // --- clickAny (varargs) ---

    public static boolean clickAny(ImageTemplate... templates) {
        return clickAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickAny(double confidence, ImageTemplate... templates) {
        return clickAny(Source.current(), confidence, templates);
    }

    public static boolean clickAny(CaptureSource source, ImageTemplate... templates) {
        return clickAny(source, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static boolean clickAny(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (click(template, source, confidence, ClickConfig.DEFAULT_FOUND_DELAY)) {
                return true;
            }
        }
        return false;
    }

    // --- clickAll (every location above the threshold) ---

    public static int clickAll(ImageTemplate template) {
        return clickAll(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickAll(ImageTemplate template, double confidence) {
        return clickAll(template, Source.current(), confidence);
    }

    public static int clickAll(ImageTemplate template, CaptureSource source) {
        return clickAll(template, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static int clickAll(ImageTemplate template, CaptureSource source, double confidence) {
        List<MatchResult> matches = findAll(template, source, confidence);
        for (MatchResult match : matches) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? match.getRandomClickPoint() : match.getCenter();
            Mouse.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
        }
        if (ClickConfig.DEBUG_MODE && matches.size() > 0) {
            System.out.println("Clicked " + matches.size() + " instances of " + template.getId());
        }
        return matches.size();
    }

    // --- Click a match already located ---

    /** Click a match already located (used by {@code clickBest}/{@code clickCompare}). */
    private static boolean clickResult(MatchResult result) {
        return clickResult(result, ClickConfig.DEFAULT_FOUND_DELAY);
    }

    /** Click a located match, waiting {@code delayMs} afterwards (the shared click body). */
    private static boolean clickResult(MatchResult result, int delayMs) {
        if (result.isFound()) {
            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ? result.getRandomClickPoint() : result.getCenter();
            Mouse.click(clickPoint);
            emitClick(clickPoint);
            Wait.milliseconds(delayMs);

            if (ClickConfig.DEBUG_MODE) {
                System.out.println("Clicked " + result.getTemplateId() + " at " + clickPoint +
                        " (confidence: " + String.format("%.3f", result.getConfidence()) + ")");
            }
            return true;
        }
        Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
        if (ClickConfig.DEBUG_MODE) {
            System.out.println("Template not found");
        }
        return false;
    }

    /**
     * Reports a left click to registered {@link com.botmaker.sdk.api.observe.BotObserver}s (see
     * {@code api.observe.Bots}). The template-click helpers always left-click at an absolute screen
     * coordinate. Guarded by {@code hasObservers()} so a normal bot run pays nothing.
     */
    private static void emitClick(Point clickPoint) {
        if (Bots.hasObservers()) {
            Bots.fireClick(new ClickEvent(Surface.ofScreen(), clickPoint, ClickEvent.LEFT));
        }
    }
}
