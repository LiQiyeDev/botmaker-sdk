package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
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
 * Single-frame image lookup: does this template appear right now, and where?
 *
 * <p>Every matcher takes a {@link CaptureSource} — one of a {@link com.botmaker.sdk.api.capture.Window},
 * a {@link CaptureSource#monitor(int) monitor}, or the whole {@link CaptureSource#desktop() desktop} — so a
 * search can be pinned to a window or a single screen and still return absolute, clickable coordinates. A
 * search <em>region</em> is expressed as a {@link CaptureSource#region(com.botmaker.sdk.api.Rect) region of a
 * source}, not a separate parameter. The no-source overloads default to the whole desktop.
 *
 * <p>Besides {@code find}/{@code findAll}/{@code findAny} this class owns the boolean {@code exists} checks
 * and the lambda control-flow helpers ({@link #whileExists}, {@link #untilExists}, {@link #ifExists}) — each
 * is one capture that hands the matched {@link MatchResult} to your action.
 */
public class ImageFinder {

    // --- find (single template) ---

    public static MatchResult find(ImageTemplate template) {
        return find(template, CaptureSource.desktop(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, double confidence) {
        return find(template, CaptureSource.desktop(), confidence);
    }

    public static MatchResult find(ImageTemplate template, CaptureSource source) {
        return find(template, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /** Core single-template matcher: capture {@code source}, match, return absolute coordinates. */
    public static MatchResult find(ImageTemplate template, CaptureSource source, double confidence) {
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
                Point location = new Point(match.x() + origin.x, match.y() + origin.y);
                MatchResult result = new MatchResult(
                        location, match.width(), match.height(), match.score(), template.getId());
                emitMatch(source, result);
                return result;
            }

            MatchResult notFound = MatchResult.notFound();
            emitMatch(source, notFound);
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

    // --- findAny (first template, in order, that clears the threshold) ---

    public static MatchResult findAny(ImageTemplate... templates) {
        return findAny(CaptureSource.desktop(), ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findAny(double confidence, ImageTemplate... templates) {
        return findAny(CaptureSource.desktop(), confidence, templates);
    }

    public static MatchResult findAny(CaptureSource source, ImageTemplate... templates) {
        return findAny(source, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findAny(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            MatchResult result = find(template, source, confidence);
            if (result.isFound()) {
                return result;
            }
        }
        return MatchResult.notFound();
    }

    // --- Group matching: first-match over an ImageTemplateGroup (mirrors findAny) ---

    public static MatchResult find(ImageTemplateGroup group) {
        return findAny(CaptureSource.desktop(), ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static MatchResult find(ImageTemplateGroup group, double confidence) {
        return findAny(CaptureSource.desktop(), confidence, group.toArray());
    }

    public static MatchResult find(ImageTemplateGroup group, CaptureSource source) {
        return findAny(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    public static MatchResult find(ImageTemplateGroup group, CaptureSource source, double confidence) {
        return findAny(source, confidence, group.toArray());
    }

    // --- Best match: evaluate fully and return the single highest-scoring match ---

    /**
     * The best location for {@code template}. Where {@code find} may accept the first location that
     * clears the threshold, {@code findBest} always returns the globally highest-scoring one.
     */
    public static MatchResult findBest(ImageTemplate template) {
        return find(template);
    }

    public static MatchResult findBest(ImageTemplate template, double confidence) {
        return find(template, confidence);
    }

    public static MatchResult findBest(ImageTemplate template, CaptureSource source) {
        return find(template, source);
    }

    public static MatchResult findBest(ImageTemplate template, CaptureSource source, double confidence) {
        return find(template, source, confidence);
    }

    /** The single highest-scoring match across every template in {@code group}. */
    public static MatchResult findBest(ImageTemplateGroup group) {
        return findBest(group, CaptureSource.desktop(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult findBest(ImageTemplateGroup group, double confidence) {
        return findBest(group, CaptureSource.desktop(), confidence);
    }

    public static MatchResult findBest(ImageTemplateGroup group, CaptureSource source) {
        return findBest(group, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult findBest(ImageTemplateGroup group, CaptureSource source, double confidence) {
        MatchResult best = MatchResult.notFound();
        for (ImageTemplate template : group.templates()) {
            MatchResult result = find(template, source, confidence);
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
        return compare(List.of(good), List.of(bad), CaptureSource.desktop(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    /** Match {@code good} only if it beats every distractor in {@code bad} at its location by the default margin. */
    public static MatchResult findCompare(ImageTemplate good, ImageTemplate... bad) {
        return compare(List.of(good), List.of(bad), CaptureSource.desktop(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    public static MatchResult findCompare(ImageTemplate good, ImageTemplate bad, CaptureSource source) {
        return compare(List.of(good), List.of(bad), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    public static MatchResult findCompare(ImageTemplate good, CaptureSource source, ImageTemplate... bad) {
        return compare(List.of(good), List.of(bad), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     */
    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        return compare(good.templates(), bad.templates(), CaptureSource.desktop(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, double margin) {
        return compare(good.templates(), bad.templates(), CaptureSource.desktop(),
                ClickConfig.DEFAULT_CONFIDENCE, margin);
    }

    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        return compare(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
    }

    public static MatchResult findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source,
                                          double margin) {
        return compare(good.templates(), bad.templates(), source, ClickConfig.DEFAULT_CONFIDENCE, margin);
    }

    /**
     * Single-capture compare: find each good template's best match, keep the highest-scoring good
     * whose location out-scores every bad template (re-scored on the same frame) by {@code margin}.
     */
    private static MatchResult compare(List<ImageTemplate> goods, List<ImageTemplate> bads,
                                       CaptureSource source, double confidence, double margin) {
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            Point origin = source.origin();
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

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
            emitMatch(source, best);
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

    // --- findAll (every location above the threshold) ---

    public static List<MatchResult> findAll(ImageTemplate template) {
        return findAll(template, CaptureSource.desktop(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static List<MatchResult> findAll(ImageTemplate template, double confidence) {
        return findAll(template, CaptureSource.desktop(), confidence);
    }

    public static List<MatchResult> findAll(ImageTemplate template, CaptureSource source) {
        return findAll(template, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static List<MatchResult> findAll(ImageTemplate template, CaptureSource source, double confidence) {
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
            int offsetX = (int) origin.x;
            int offsetY = (int) origin.y;

            List<MatchResult> results = matches.stream()
                    .map(r -> {
                        Point location = new Point(r.x() + offsetX, r.y() + offsetY);
                        return new MatchResult(location, r.width(), r.height(), r.score(), template.getId());
                    })
                    .collect(Collectors.toList());

            emitMatches(source, results);
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
    // The Surface is the source's whole-surface identity (window/screen); the region is its sub-rectangle
    // when the source was narrowed with CaptureSource.region(...), else null.

    private static void emitMatch(CaptureSource source, MatchResult result) {
        if (Bots.hasObservers()) {
            Bots.fireMatch(new MatchEvent(Surface.of(source.base()), source.subRegion(), result));
        }
    }

    private static void emitMatches(CaptureSource source, List<MatchResult> results) {
        if (!Bots.hasObservers()) return;
        Surface surface = Surface.of(source.base());
        if (results.isEmpty()) {
            Bots.fireMatch(new MatchEvent(surface, source.subRegion(), MatchResult.notFound()));
            return;
        }
        for (MatchResult result : results) {
            Bots.fireMatch(new MatchEvent(surface, source.subRegion(), result));
        }
    }

    // --- Existence checks (single template) ---

    public static boolean exists(ImageTemplate template) {
        return find(template).isFound();
    }

    public static boolean exists(ImageTemplate template, double confidence) {
        return find(template, confidence).isFound();
    }

    public static boolean exists(ImageTemplate template, CaptureSource source) {
        return find(template, source).isFound();
    }

    public static boolean exists(ImageTemplate template, CaptureSource source, double confidence) {
        return find(template, source, confidence).isFound();
    }

    public static boolean notExists(ImageTemplate template) {
        return !exists(template);
    }

    public static boolean notExists(ImageTemplate template, CaptureSource source) {
        return !exists(template, source);
    }

    public static boolean existsAny(ImageTemplate... templates) {
        return findAny(templates).isFound();
    }

    public static boolean existsAny(CaptureSource source, ImageTemplate... templates) {
        return findAny(source, templates).isFound();
    }

    /** True only if <em>every</em> template is currently visible (empty input is false). */
    public static boolean existsAll(ImageTemplate... templates) {
        return existsAll(CaptureSource.desktop(), templates);
    }

    public static boolean existsAll(CaptureSource source, ImageTemplate... templates) {
        if (templates.length == 0) {
            return false;
        }
        for (ImageTemplate template : templates) {
            if (!exists(template, source)) {
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

    public static boolean exists(ImageTemplateGroup group, CaptureSource source) {
        return find(group, source).isFound();
    }

    /** True only if <em>every</em> template in {@code group} is currently visible. */
    public static boolean existsAll(ImageTemplateGroup group) {
        return existsAll(group.toArray());
    }

    public static boolean existsAll(ImageTemplateGroup group, CaptureSource source) {
        return existsAll(source, group.toArray());
    }

    /** True if <em>no</em> template in {@code group} is currently visible. */
    public static boolean notExists(ImageTemplateGroup group) {
        return !exists(group);
    }

    public static boolean notExists(ImageTemplateGroup group, CaptureSource source) {
        return !exists(group, source);
    }

    // --- Lambda control-flow: act on the live match, one capture per check ---

    /** Run {@code action} once with the match if {@code template} is currently visible. */
    public static boolean ifExists(ImageTemplate template, Consumer<MatchResult> action) {
        return ifExists(template, CaptureSource.desktop(), action);
    }

    public static boolean ifExists(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result = find(template, source);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /** Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible. */
    public static void whileExists(ImageTemplate template, Consumer<MatchResult> action) {
        whileExists(template, CaptureSource.desktop(), action);
    }

    public static void whileExists(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = find(template, source)).isFound()) {
            action.accept(result);
        }
    }

    /** Keep running {@code action} until {@code template} appears (no match exists while it's absent). */
    public static void untilExists(ImageTemplate template, Runnable action) {
        untilExists(template, CaptureSource.desktop(), action);
    }

    public static void untilExists(ImageTemplate template, CaptureSource source, Runnable action) {
        while (!exists(template, source)) {
            action.run();
        }
    }

    // --- Lambda control-flow over a group: "Any" (first visible) / "All" (every one visible) ---
    //
    // The "Any" variants hand your action the live match (the first template, in order, that clears the
    // threshold). The "All" variants take a Runnable — "every template is present" has no single
    // meaningful MatchResult, so there is nothing to hand you (mirrors untilExists' Runnable).

    public static boolean ifExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        return ifExistsAny(group, CaptureSource.desktop(), action);
    }

    public static boolean ifExistsAny(ImageTemplateGroup group, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result = find(group, source);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    public static boolean ifExistsAll(ImageTemplateGroup group, Runnable action) {
        return ifExistsAll(group, CaptureSource.desktop(), action);
    }

    public static boolean ifExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        if (existsAll(group, source)) {
            action.run();
            return true;
        }
        return false;
    }

    public static void whileExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        whileExistsAny(group, CaptureSource.desktop(), action);
    }

    public static void whileExistsAny(ImageTemplateGroup group, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = find(group, source)).isFound()) {
            action.accept(result);
        }
    }

    public static void whileExistsAll(ImageTemplateGroup group, Runnable action) {
        whileExistsAll(group, CaptureSource.desktop(), action);
    }

    public static void whileExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (existsAll(group, source)) {
            action.run();
        }
    }

    public static void untilExistsAny(ImageTemplateGroup group, Runnable action) {
        untilExistsAny(group, CaptureSource.desktop(), action);
    }

    public static void untilExistsAny(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (!exists(group, source)) {
            action.run();
        }
    }

    public static void untilExistsAll(ImageTemplateGroup group, Runnable action) {
        untilExistsAll(group, CaptureSource.desktop(), action);
    }

    public static void untilExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (!existsAll(group, source)) {
            action.run();
        }
    }
}
