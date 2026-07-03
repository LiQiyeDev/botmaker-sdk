package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.opencv.OpencvManager;
import com.botmaker.sdk.internal.opencv.RawMatch;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Single-frame image lookup: does this template appear on screen right now, and where?
 *
 * <p>Besides the raw {@code find}/{@code findAll}/{@code findAny} matchers this class also owns the
 * boolean {@code exists} checks and the lambda control-flow helpers ({@link #whileExists},
 * {@link #untilExists}, {@link #ifExists}) — each is one capture that hands the matched
 * {@link MatchResult} to your action, so you can act on the location without a second lookup.
 */
public class ImageFinder {

    public static MatchResult find(ImageTemplate template) {
        return find(template, (Rect) null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, Rect region) {
        return find(template, region, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, double confidence) {
        return find(template, (Rect) null, confidence);
    }

    public static MatchResult find(ImageTemplate template, Rect region, double confidence) {
        return find(template, CaptureSource.screen(), region, confidence);
    }

    // --- Window-targeted overloads: match (and return absolute coords) within a specific source ---

    public static MatchResult find(ImageTemplate template, CaptureSource source) {
        return find(template, source, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, CaptureSource source, double confidence) {
        return find(template, source, null, confidence);
    }

    public static MatchResult find(ImageTemplate template, CaptureSource source, Rect region, double confidence) {
        // Note: a genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError),
        // which is intentionally NOT caught here so it cannot masquerade as "not found".
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();

            if (screenshot == null) {
                return MatchResult.notFound();
            }

            background = OpencvManager.bufferedImageToMat(screenshot);

            RawMatch match = OpencvManager.findBestMatch(template.getMat(), background, false, confidence);

            if (match != null) {
                Point origin = source.origin();
                int offsetX = (region != null ? region.x : 0) + (int) origin.x;
                int offsetY = (region != null ? region.y : 0) + (int) origin.y;

                Point location = new Point(match.x() + offsetX, match.y() + offsetY);

                return new MatchResult(
                        location,
                        match.width(),
                        match.height(),
                        match.score(),
                        template.getId()
                );
            }

            return MatchResult.notFound();

        } catch (Exception e) {
            if (ClickConfig.DEBUG_MODE) {
                System.err.println("Error finding template: " + e.getMessage());
                e.printStackTrace();
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    public static MatchResult findAny(ImageTemplate... templates) {
        return findAny(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findAny(Rect region, ImageTemplate... templates) {
        return findAny(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findAny(Rect region, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            MatchResult result = find(template, region, confidence);
            if (result.isFound()) {
                return result;
            }
        }
        return MatchResult.notFound();
    }

    public static List<MatchResult> findAll(ImageTemplate template) {
        return findAll(template, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static List<MatchResult> findAll(ImageTemplate template, Rect region) {
        return findAll(template, region, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static List<MatchResult> findAll(ImageTemplate template, Rect region, double confidence) {
        return findAll(template, CaptureSource.screen(), region, confidence);
    }

    public static List<MatchResult> findAll(ImageTemplate template, CaptureSource source, Rect region, double confidence) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();

            if (screenshot == null) {
                return new ArrayList<>();
            }

            background = OpencvManager.bufferedImageToMat(screenshot);

            List<RawMatch> matches =
                    OpencvManager.findMultipleMatches(template.getMat(), background, false, confidence);

            Point origin = source.origin();
            int offsetX = (region != null ? region.x : 0) + (int) origin.x;
            int offsetY = (region != null ? region.y : 0) + (int) origin.y;

            return matches.stream()
                    .map(r -> {
                        Point location = new Point(r.x() + offsetX, r.y() + offsetY);
                        return new MatchResult(
                                location,
                                r.width(),
                                r.height(),
                                r.score(),
                                template.getId()
                        );
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            if (ClickConfig.DEBUG_MODE) {
                System.err.println("Error in findAll: " + e.getMessage());
                e.printStackTrace();
            }
            return new ArrayList<>();
        } finally {
            if (background != null) {
                background.release();
            }
        }
    }

    // --- Existence checks ---

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

    // --- Lambda control-flow: act on the live match, one capture per check ---

    /** Run {@code action} once with the match if {@code template} is currently visible. */
    public static boolean ifExists(ImageTemplate template, Consumer<MatchResult> action) {
        MatchResult result = find(template);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /** Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible. */
    public static void whileExists(ImageTemplate template, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = find(template)).isFound()) {
            action.accept(result);
        }
    }

    /** Keep running {@code action} until {@code template} appears (no match exists while it's absent). */
    public static void untilExists(ImageTemplate template, Runnable action) {
        while (!exists(template)) {
            action.run();
        }
    }
}
