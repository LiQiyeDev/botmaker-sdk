package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.MatchEvent;
import com.botmaker.sdk.api.observe.Surface;
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

                MatchResult result = new MatchResult(
                        location,
                        match.width(),
                        match.height(),
                        match.score(),
                        template.getId()
                );
                emitMatch(source, region, result);
                return result;
            }

            MatchResult notFound = MatchResult.notFound();
            emitMatch(source, region, notFound);
            return notFound;

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

    // --- Group matching: first-match over an ImageTemplateGroup (mirrors findAny) ---

    public static MatchResult find(ImageTemplateGroup group) {
        return findAny(null, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static MatchResult find(ImageTemplateGroup group, Rect region) {
        return findAny(region, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static MatchResult find(ImageTemplateGroup group, Rect region, double confidence) {
        return findAny(region, confidence, group.toArray());
    }

    // --- Best match: evaluate fully and return the single highest-scoring match ---

    /**
     * The best location for {@code template}. Where {@code find} may accept the first location that
     * clears the threshold, {@code findBest} always returns the globally highest-scoring one.
     */
    public static MatchResult findBest(ImageTemplate template) {
        return find(template);
    }

    public static MatchResult findBest(ImageTemplate template, Rect region) {
        return find(template, region);
    }

    public static MatchResult findBest(ImageTemplate template, Rect region, double confidence) {
        return find(template, region, confidence);
    }

    /** The single highest-scoring match across every template in {@code group}. */
    public static MatchResult findBest(ImageTemplateGroup group) {
        return findBest(group, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult findBest(ImageTemplateGroup group, Rect region) {
        return findBest(group, region, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult findBest(ImageTemplateGroup group, Rect region, double confidence) {
        MatchResult best = MatchResult.notFound();
        for (ImageTemplate template : group.templates()) {
            MatchResult result = find(template, region, confidence);
            if (result.isFound() && (!best.isFound() || result.getConfidence() > best.getConfidence())) {
                best = result;
            }
        }
        return best;
    }

    // --- Compare: a "good" template must out-score similar "bad" ones at the same location ---

    /** Padding (px) around a candidate location when re-scoring a competing template there. */
    private static final int COMPARE_PAD = 4;

    /**
     * Match {@code good} only if it out-scores {@code bad} at the same location by
     * {@link ClickConfig#DEFAULT_COMPARE_MARGIN}. Use for two visually-similar templates (e.g. an
     * active vs. a greyed-out button) where a plain {@code find} would match either.
     *
     * @param good the template you want to match (the "winner")
     * @param bad  the look-alike distractor that must NOT out-score {@code good} at the same spot
     */
    public static MatchResult findCompare(ImageTemplate good, ImageTemplate bad) {
        return compare(List.of(good), List.of(bad), CaptureSource.screen(), null,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    /** Match {@code good} only if it beats every distractor in {@code bad} at its location by the default margin. */
    public static MatchResult findCompare(ImageTemplate good, ImageTemplate... bad) {
        return compare(List.of(good), List.of(bad), CaptureSource.screen(), null,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     */
    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return findCompare(good, bad, null, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, Rect region, double margin) {
        return compare(good.templates(), bad.templates(), CaptureSource.screen(), region,
                ClickConfig.DEFAULT_CONFIDENCE, margin);
    }

    /**
     * Single-capture compare: find each good template's best match, keep the highest-scoring good
     * whose location out-scores every bad template (re-scored on the same frame) by {@code margin}.
     */
    private static MatchResult compare(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                       CaptureSource source, Rect region, double confidence, double margin) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            Point origin = source.origin();
            int offsetX = (region != null ? region.x : 0) + (int) origin.x;
            int offsetY = (region != null ? region.y : 0) + (int) origin.y;

            MatchResult best = MatchResult.notFound();
            for (ImageTemplate good : goods) {
                RawMatch gm = OpencvManager.findBestMatch(good.getMat(), background, false, confidence);
                if (gm == null) {
                    continue;
                }
                boolean wins = true;
                for (ImageTemplate bad : bads) {
                    double badScore = OpencvManager.scoreAround(
                            bad.getMat(), background, false, gm.x(), gm.y(), COMPARE_PAD);
                    if (badScore >= gm.score() - margin) {
                        wins = false;
                        break;
                    }
                }
                if (wins && (!best.isFound() || gm.score() > best.getConfidence())) {
                    best = new MatchResult(
                            new Point(gm.x() + offsetX, gm.y() + offsetY),
                            gm.width(), gm.height(), gm.score(), good.getId());
                }
            }
            emitMatch(source, region, best);
            return best;
        } catch (Exception e) {
            if (ClickConfig.DEBUG_MODE) {
                System.err.println("Error in compare: " + e.getMessage());
                e.printStackTrace();
            }
            return MatchResult.notFound();
        } finally {
            if (background != null) {
                background.release();
            }
        }
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

            List<MatchResult> results = matches.stream()
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

            emitMatches(source, region, results);
            return results;

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

    // --- Observability: report each match attempt to registered BotObservers (see api.observe.Bots) ---
    // Guarded by hasObservers() so a normal bot run (no observer) builds nothing and pays nothing.

    private static void emitMatch(CaptureSource source, Rect region, MatchResult result) {
        if (Bots.hasObservers()) {
            Bots.fireMatch(new MatchEvent(Surface.of(source), region, result));
        }
    }

    private static void emitMatches(CaptureSource source, Rect region, List<MatchResult> results) {
        if (!Bots.hasObservers()) return;
        Surface surface = Surface.of(source);
        if (results.isEmpty()) {
            Bots.fireMatch(new MatchEvent(surface, region, MatchResult.notFound()));
            return;
        }
        for (MatchResult result : results) {
            Bots.fireMatch(new MatchEvent(surface, region, result));
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

    /** True only if <em>every</em> template is currently visible (empty input is false). */
    public static boolean existsAll(ImageTemplate... templates) {
        if (templates.length == 0) {
            return false;
        }
        for (ImageTemplate template : templates) {
            if (!exists(template)) {
                return false;
            }
        }
        return true;
    }

    // --- Group existence checks ---

    /** True if <em>any</em> template in {@code group} is currently visible (first-match). */
    public static boolean exists(ImageTemplateGroup group) {
        return find(group).isFound();
    }

    /** True only if <em>every</em> template in {@code group} is currently visible. */
    public static boolean existsAll(ImageTemplateGroup group) {
        return existsAll(group.toArray());
    }

    /** True if <em>no</em> template in {@code group} is currently visible. */
    public static boolean notExists(ImageTemplateGroup group) {
        return !exists(group);
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

    // --- Lambda control-flow over a group: "Any" (first visible) / "All" (every one visible) ---
    //
    // The "Any" variants hand your action the live match (the first template, in order, that clears the
    // threshold). The "All" variants take a Runnable — "every template is present" has no single
    // meaningful MatchResult, so there is nothing to hand you (mirrors untilExists' Runnable).

    /**
     * Run {@code action} once with the first visible match if <em>any</em> template in {@code group}
     * is currently visible.
     *
     * @param group  the templates to look for; the first one found (in order) wins
     * @param action receives the {@link MatchResult} of the matched template
     * @return {@code true} if a template was found and {@code action} ran
     */
    public static boolean ifExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        MatchResult result = find(group);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Run {@code action} once if <em>every</em> template in {@code group} is currently visible.
     *
     * @param group  the templates that must all be present
     * @param action runs once when all are visible
     * @return {@code true} if all were found and {@code action} ran
     */
    public static boolean ifExistsAll(ImageTemplateGroup group, Runnable action) {
        if (existsAll(group)) {
            action.run();
            return true;
        }
        return false;
    }

    /**
     * Keep running {@code action} (with the fresh first match each time) as long as <em>any</em>
     * template in {@code group} stays visible.
     *
     * @param group  the templates to look for; the first one found (in order) wins each iteration
     * @param action receives the {@link MatchResult} of the matched template on every iteration
     */
    public static void whileExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = find(group)).isFound()) {
            action.accept(result);
        }
    }

    /**
     * Keep running {@code action} as long as <em>every</em> template in {@code group} stays visible.
     *
     * @param group  the templates that must all remain present
     * @param action runs once per iteration while all are visible
     */
    public static void whileExistsAll(ImageTemplateGroup group, Runnable action) {
        while (existsAll(group)) {
            action.run();
        }
    }

    /**
     * Keep running {@code action} until <em>any</em> template in {@code group} appears.
     *
     * @param group  the templates to wait for; the loop ends as soon as one is visible
     * @param action runs once per iteration while none are visible
     */
    public static void untilExistsAny(ImageTemplateGroup group, Runnable action) {
        while (!exists(group)) {
            action.run();
        }
    }

    /**
     * Keep running {@code action} until <em>every</em> template in {@code group} appears.
     *
     * @param group  the templates to wait for; the loop ends once all are visible
     * @param action runs once per iteration until all are visible
     */
    public static void untilExistsAll(ImageTemplateGroup group, Runnable action) {
        while (!existsAll(group)) {
            action.run();
        }
    }
}
