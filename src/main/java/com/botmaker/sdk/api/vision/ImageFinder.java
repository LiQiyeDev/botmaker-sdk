package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
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

    /**
     * Finds the specified template on the current capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for
     * @return true if the template was found, false otherwise
     * @see #find(ImageTemplate, double)
     * @see #find(ImageTemplate, CaptureSource)
     * @see #find(ImageTemplate, CaptureSource, double)
     */
    public static boolean find(ImageTemplate template) {
        return find(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds the specified template on the current capture source with a custom confidence threshold.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found, false otherwise
     */
    public static boolean find(ImageTemplate template, double confidence) {
        return find(template, Source.current(), confidence);
    }

    /**
     * Finds the specified template on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template the image template to search for
     * @param source   the capture source (window, monitor, or desktop region) to search within
     * @return true if the template was found, false otherwise
     */
    public static boolean find(ImageTemplate template, CaptureSource source) {
        return find(template, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds the specified template on a specific capture source with a custom confidence threshold.
     * This is the core matching method that performs the actual image capture and template matching.
     * <p>
     * The search is performed within the bounds of the capture source. The match result is stored
     * in {@link VisionContext} and can be retrieved with {@link VisionContext#getLastMatch()}.
     * <p>
     * The returned match result contains absolute screen coordinates, so clicks can be performed
     * directly at the matched location using {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for
     * @param source     the capture source (window, monitor, or desktop region) to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found at or above the confidence threshold, false otherwise
     */
    public static boolean find(ImageTemplate template, CaptureSource source, double confidence) {
        MatchResult result = findInternal(template, source, confidence);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Internal method for findAny that returns MatchResult.
     * Does not update VisionContext - caller is responsible for that.
     */
    static MatchResult findAnyInternal(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            MatchResult result = findInternal(template, source, confidence);
            if (result.isFound()) {
                return result;
            }
        }
        return MatchResult.notFound();
    }

    /**
     * Internal method that performs the actual find operation and returns the MatchResult.
     * Does not update VisionContext - caller is responsible for that.
     */
    static MatchResult findInternal(ImageTemplate template, CaptureSource source, double confidence) {
        // Note: a genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError),
        // which is intentionally NOT caught here so it cannot masquerade as "not found".
        Mat background = null;
        try {
            BufferedImage screenshot = source.capture();
            if (screenshot == null) {
                return MatchResult.notFound();
            }
            background = OpencvManager.bufferedImageToMat(screenshot);

            // Get the raw best match (below-threshold included) so a miss can still report its real score.
            RawMatch best = OpencvManager.findBest(template.getMat(), background, false);

            if (best != null && best.score() >= confidence) {
                Point origin = source.origin();
                Point location = new Point(best.x() + origin.x, best.y() + origin.y);
                MatchResult result = new MatchResult(
                        location, best.width(), best.height(), best.score(), template.getId());
                emitMatch(source, result);
                return result;
            }

            // Miss: emit telemetry carrying the best near-miss score (so the dashboard shows why it's
            // borderline), but return not-found result.
            MatchResult missResult = MatchResult.miss(best != null ? best.score() : 0.0);
            emitMatch(source, missResult);
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

    // --- findAny (first template, in order, that clears the threshold) ---

    /**
     * Finds the first template (in order) that appears on the current capture source using the default confidence.
     * Templates are checked in the order provided, and the first one found above the threshold is returned.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found, false otherwise
     * @see #findAny(double, ImageTemplate...)
     * @see #findAny(CaptureSource, ImageTemplate...)
     * @see #findAny(CaptureSource, double, ImageTemplate...)
     */
    public static boolean findAny(ImageTemplate... templates) {
        return findAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    /**
     * Finds the first template (in order) that appears on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found, false otherwise
     */
    public static boolean findAny(double confidence, ImageTemplate... templates) {
        return findAny(Source.current(), confidence, templates);
    }

    /**
     * Finds the first template (in order) that appears on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param source    the capture source to search within
     * @param templates the image templates to search for, in priority order
     * @return true if any template was found, false otherwise
     */
    public static boolean findAny(CaptureSource source, ImageTemplate... templates) {
        return findAny(source, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    /**
     * Finds the first template (in order) that appears on a specific capture source with a custom confidence.
     * This is the core implementation that iterates through templates and returns the first match found.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @param templates  the image templates to search for, in priority order
     * @return true if any template was found, false otherwise
     */
    public static boolean findAny(CaptureSource source, double confidence, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            MatchResult result = findInternal(template, source, confidence);
            if (result.isFound()) {
                VisionContext.setLastMatch(result);
                return true;
            }
        }
        VisionContext.setLastMatch(MatchResult.notFound());
        return false;
    }

    // --- findAny over an ImageTemplateGroup: first template in the group that clears the threshold ---

    /**
     * Finds the first template in the group that appears on the current capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found, false otherwise
     * @see #findAny(ImageTemplateGroup, double)
     * @see #findAny(ImageTemplateGroup, CaptureSource)
     * @see #findAny(ImageTemplateGroup, CaptureSource, double)
     */
    public static boolean findAny(ImageTemplateGroup group) {
        return findAny(Source.current(), ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    /**
     * Finds the first template in the group that appears on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findAny(ImageTemplateGroup group, double confidence) {
        return findAny(Source.current(), confidence, group.toArray());
    }

    /**
     * Finds the first template in the group that appears on a specific capture source using the default confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findAny(ImageTemplateGroup group, CaptureSource source) {
        return findAny(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
    }

    /**
     * Finds the first template in the group that appears on a specific capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findAny(ImageTemplateGroup group, CaptureSource source, double confidence) {
        return findAny(source, confidence, group.toArray());
    }

    // --- Best match: evaluate fully and return the single highest-scoring match ---

    /**
     * Finds the highest-scoring match for any template in the group on the current capture source.
     * Unlike {@link #findAny(ImageTemplateGroup)}, this evaluates every template and returns the best match
     * regardless of order.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found, false otherwise
     * @see #findBest(ImageTemplateGroup, double)
     * @see #findBest(ImageTemplateGroup, CaptureSource)
     * @see #findBest(ImageTemplateGroup, CaptureSource, double)
     */
    public static boolean findBest(ImageTemplateGroup group) {
        return findBest(group, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds the highest-scoring match for any template in the group on the current capture source with a custom confidence.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findBest(ImageTemplateGroup group, double confidence) {
        return findBest(group, Source.current(), confidence);
    }

    /**
     * Finds the highest-scoring match for any template in the group on a specific capture source.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findBest(ImageTemplateGroup group, CaptureSource source) {
        return findBest(group, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds the highest-scoring match for any template in the group on a specific capture source with a custom confidence.
     * This is the core implementation that evaluates every template in the group and returns the single best match.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param group      the template group to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean findBest(ImageTemplateGroup group, CaptureSource source, double confidence) {
        MatchResult best = MatchResult.notFound();
        for (ImageTemplate template : group.templates()) {
            MatchResult result = findInternal(template, source, confidence);
            if (result.isFound() && (!best.isFound() || result.getConfidence() > best.getConfidence())) {
                best = result;
            }
        }
        VisionContext.setLastMatch(best);
        return best.isFound();
    }

    // --- Compare: a "good" template must out-score similar "bad" ones at the same location ---

    /** Padding (px) around a candidate location when re-scoring a competing template there. */
    private static final int COMPARE_PAD = 4;

    /**
     * Match {@code good} only if it out-scores {@code bad} at the same location by
     * {@link ClickConfig#DEFAULT_COMPARE_MARGIN}. Use for two visually-similar templates (e.g. an
     * active vs. a greyed-out button) where a plain {@code find} would match either.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good the template you want to match (the "winner")
     * @param bad  the look-alike distractor that must NOT out-score {@code good} at the same spot
     * @return true if the good template was found and beats the bad template, false otherwise
     */
    public static boolean findCompare(ImageTemplate good, ImageTemplate bad) {
        MatchResult result = compare(List.of(good), List.of(bad), Source.current(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Match {@code good} only if it beats every distractor in {@code bad} at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good the template you want to match (the "winner")
     * @param bad  the look-alike distractors that must NOT out-score {@code good} at the same spot
     * @return true if the good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplate good, ImageTemplate... bad) {
        MatchResult result = compare(List.of(good), List.of(bad), Source.current(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Match {@code good} only if it out-scores {@code bad} at the same location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the template you want to match (the "winner")
     * @param bad    the look-alike distractor that must NOT out-score {@code good} at the same spot
     * @param source the capture source to search within
     * @return true if the good template was found and beats the bad template, false otherwise
     */
    public static boolean findCompare(ImageTemplate good, ImageTemplate bad, CaptureSource source) {
        MatchResult result = compare(List.of(good), List.of(bad), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Match {@code good} only if it beats every distractor in {@code bad} at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the template you want to match (the "winner")
     * @param source the capture source to search within
     * @param bad    the look-alike distractors that must NOT out-score {@code good} at the same spot
     * @return true if the good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplate good, CaptureSource source, ImageTemplate... bad) {
        MatchResult result = compare(List.of(good), List.of(bad), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good the group of good templates to search for
     * @param bad  the group of bad templates that must NOT out-score the good templates
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad) {
        MatchResult result = compare(good.templates(), bad.templates(), Source.current(),
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the specified margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good  the group of good templates to search for
     * @param bad   the group of bad templates that must NOT out-score the good templates
     * @param margin the minimum score difference required for a match
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, double margin) {
        MatchResult result = compare(good.templates(), bad.templates(), Source.current(),
                ClickConfig.DEFAULT_CONFIDENCE, margin);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the default margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source) {
        MatchResult result = compare(good.templates(), bad.templates(), source,
                ClickConfig.DEFAULT_CONFIDENCE, ClickConfig.DEFAULT_COMPARE_MARGIN);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Among the {@code good} templates, return the best-scoring match that still beats every
     * {@code bad} template at its location by the specified margin.
     * <p>
     * The match result is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatch()}.
     *
     * @param good   the group of good templates to search for
     * @param bad    the group of bad templates that must NOT out-score the good templates
     * @param source the capture source to search within
     * @param margin the minimum score difference required for a match
     * @return true if a good template was found and beats all bad templates, false otherwise
     */
    public static boolean findCompare(ImageTemplateGroup good, ImageTemplateGroup bad, CaptureSource source,
                                          double margin) {
        MatchResult result = compare(good.templates(), bad.templates(), source, ClickConfig.DEFAULT_CONFIDENCE, margin);
        VisionContext.setLastMatch(result);
        return result.isFound();
    }

    /**
     * Single-capture compare: find each good template's best match, keep the highest-scoring good
     * whose location out-scores every bad template (re-scored on the same frame) by {@code margin}.
     * This is an internal method - callers are responsible for updating VisionContext.
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

    /**
     * Finds all occurrences of the template on the current capture source using the default confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template the image template to search for
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template) {
        return findAll(template, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds all occurrences of the template on the current capture source with a custom confidence threshold.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template   the image template to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template, double confidence) {
        return findAll(template, Source.current(), confidence);
    }

    /**
     * Finds all occurrences of the template on a specific capture source using the default confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template, CaptureSource source) {
        return findAll(template, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds all occurrences of the template on a specific capture source with a custom confidence threshold.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}. The first match is also stored in
     * {@link VisionContext#getLastMatch()}.
     *
     * @param template   the image template to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the number of matches found
     */
    public static int findAll(ImageTemplate template, CaptureSource source, double confidence) {
        List<MatchResult> results = findAllInternal(template, source, confidence);
        VisionContext.setLastMatchList(results);
        return results.size();
    }

    /**
     * Internal method that performs findAll and returns the list of results.
     * Does not update VisionContext - caller is responsible for that.
     */
    static List<MatchResult> findAllInternal(ImageTemplate template, CaptureSource source, double confidence) {
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

    // --- findAll over an ImageTemplateGroup: every location of every template above the threshold ---

    /**
     * Finds all occurrences of every template in the group on the current capture source.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group the template group to search for
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group) {
        return findAll(group, Source.current(), ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds all occurrences of every template in the group on the current capture source with a custom confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group      the template group to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group, double confidence) {
        return findAll(group, Source.current(), confidence);
    }

    /**
     * Finds all occurrences of every template in the group on a specific capture source.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group, CaptureSource source) {
        return findAll(group, source, ClickConfig.DEFAULT_CONFIDENCE);
    }

    /**
     * Finds all occurrences of every template in the group on a specific capture source with a custom confidence.
     * <p>
     * The list of match results is stored in {@link VisionContext} and can be retrieved with
     * {@link VisionContext#getLastMatchList()}.
     *
     * @param group      the template group to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return the total number of matches found across all templates in the group
     */
    public static int findAll(ImageTemplateGroup group, CaptureSource source, double confidence) {
        List<MatchResult> all = new ArrayList<>();
        for (ImageTemplate template : group.templates()) {
            all.addAll(findAllInternal(template, source, confidence));
        }
        VisionContext.setLastMatchList(all);
        return all.size();
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

    /**
     * Checks if the template exists on the current capture source using the default confidence.
     *
     * @param template the image template to search for
     * @return true if the template was found, false otherwise
     */
    public static boolean exists(ImageTemplate template) {
        return find(template);
    }

    /**
     * Checks if the template exists on the current capture source with a custom confidence threshold.
     *
     * @param template   the image template to search for
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found, false otherwise
     */
    public static boolean exists(ImageTemplate template, double confidence) {
        return find(template, confidence);
    }

    /**
     * Checks if the template exists on a specific capture source using the default confidence.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @return true if the template was found, false otherwise
     */
    public static boolean exists(ImageTemplate template, CaptureSource source) {
        return find(template, source);
    }

    /**
     * Checks if the template exists on a specific capture source with a custom confidence threshold.
     *
     * @param template   the image template to search for
     * @param source     the capture source to search within
     * @param confidence the minimum confidence score (0.0 to 1.0) required for a match
     * @return true if the template was found, false otherwise
     */
    public static boolean exists(ImageTemplate template, CaptureSource source, double confidence) {
        return find(template, source, confidence);
    }

    /**
     * Checks if the template does NOT exist on the current capture source.
     *
     * @param template the image template to search for
     * @return true if the template was NOT found, false otherwise
     */
    public static boolean notExists(ImageTemplate template) {
        return !exists(template);
    }

    /**
     * Checks if the template does NOT exist on a specific capture source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @return true if the template was NOT found, false otherwise
     */
    public static boolean notExists(ImageTemplate template, CaptureSource source) {
        return !exists(template, source);
    }

    /**
     * Checks if any of the templates exists on the current capture source.
     *
     * @param templates the image templates to search for
     * @return true if any template was found, false otherwise
     */
    public static boolean existsAny(ImageTemplate... templates) {
        return findAny(templates);
    }

    /**
     * Checks if any of the templates exists on a specific capture source.
     *
     * @param source    the capture source to search within
     * @param templates the image templates to search for
     * @return true if any template was found, false otherwise
     */
    public static boolean existsAny(CaptureSource source, ImageTemplate... templates) {
        return findAny(source, templates);
    }

    /** True only if <em>every</em> template is currently visible (empty input is false). */
    public static boolean existsAll(ImageTemplate... templates) {
        return existsAll(Source.current(), templates);
    }

    /**
     * Checks if all templates exist on a specific capture source.
     *
     * @param source    the capture source to search within
     * @param templates the image templates to search for
     * @return true if all templates were found, false otherwise
     */
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

    /**
     * Checks if any template in the group exists on the current capture source.
     *
     * @param group the template group to search for
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean exists(ImageTemplateGroup group) {
        return findAny(group);
    }

    /**
     * Checks if any template in the group exists on a specific capture source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if any template in the group was found, false otherwise
     */
    public static boolean exists(ImageTemplateGroup group, CaptureSource source) {
        return findAny(group, source);
    }

    /**
     * Checks if every template in the group exists on the current capture source.
     *
     * @param group the template group to search for
     * @return true if every template in the group was found, false otherwise
     */
    public static boolean existsAll(ImageTemplateGroup group) {
        return existsAll(group.toArray());
    }

    /**
     * Checks if every template in the group exists on a specific capture source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if every template in the group was found, false otherwise
     */
    public static boolean existsAll(ImageTemplateGroup group, CaptureSource source) {
        return existsAll(source, group.toArray());
    }

    /**
     * Checks if no template in the group exists on the current capture source.
     *
     * @param group the template group to search for
     * @return true if no template in the group was found, false otherwise
     */
    public static boolean notExists(ImageTemplateGroup group) {
        return !exists(group);
    }

    /**
     * Checks if no template in the group exists on a specific capture source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @return true if no template in the group was found, false otherwise
     */
    public static boolean notExists(ImageTemplateGroup group, CaptureSource source) {
        return !exists(group, source);
    }

    // --- Lambda control-flow: act on the live match, one capture per check ---

    /**
     * Run {@code action} once with the match if {@code template} is currently visible.
     * The match result is stored in {@link VisionContext}.
     *
     * @param template the image template to search for
     * @param action   the action to run with the match result
     * @return true if the template was found and the action was run, false otherwise
     */
    public static boolean ifExists(ImageTemplate template, Consumer<MatchResult> action) {
        return ifExists(template, Source.current(), action);
    }

    /**
     * Run {@code action} once with the match if {@code template} is currently visible on a specific source.
     * The match result is stored in {@link VisionContext}.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run with the match result
     * @return true if the template was found and the action was run, false otherwise
     */
    public static boolean ifExists(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result = findInternal(template, source, ClickConfig.DEFAULT_CONFIDENCE);
        VisionContext.setLastMatch(result);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible.
     *
     * @param template the image template to search for
     * @param action   the action to run with each match result
     */
    public static void whileExists(ImageTemplate template, Consumer<MatchResult> action) {
        whileExists(template, Source.current(), action);
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as {@code template} stays visible on a specific source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run with each match result
     */
    public static void whileExists(ImageTemplate template, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = findInternal(template, source, ClickConfig.DEFAULT_CONFIDENCE)).isFound()) {
            VisionContext.setLastMatch(result);
            action.accept(result);
        }
        VisionContext.setLastMatch(MatchResult.notFound());
    }

    /**
     * Keep running {@code action} until {@code template} appears (no match exists while it's absent).
     *
     * @param template the image template to search for
     * @param action   the action to run
     */
    public static void untilExists(ImageTemplate template, Runnable action) {
        untilExists(template, Source.current(), action);
    }

    /**
     * Keep running {@code action} until {@code template} appears on a specific source.
     *
     * @param template the image template to search for
     * @param source   the capture source to search within
     * @param action   the action to run
     */
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

    /**
     * Run {@code action} once with the match if any template in the group is currently visible.
     * The match result is stored in {@link VisionContext}.
     *
     * @param group  the template group to search for
     * @param action the action to run with the match result
     * @return true if any template was found and the action was run, false otherwise
     */
    public static boolean ifExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        return ifExistsAny(group, Source.current(), action);
    }

    /**
     * Run {@code action} once with the match if any template in the group is currently visible on a specific source.
     * The match result is stored in {@link VisionContext}.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with the match result
     * @return true if any template was found and the action was run, false otherwise
     */
    public static boolean ifExistsAny(ImageTemplateGroup group, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result = findAnyInternal(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray());
        VisionContext.setLastMatch(result);
        if (result.isFound()) {
            action.accept(result);
            return true;
        }
        return false;
    }

    /**
     * Run {@code action} once if all templates in the group are currently visible.
     *
     * @param group  the template group to search for
     * @param action the action to run
     * @return true if all templates were found and the action was run, false otherwise
     */
    public static boolean ifExistsAll(ImageTemplateGroup group, Runnable action) {
        return ifExistsAll(group, Source.current(), action);
    }

    /**
     * Run {@code action} once if all templates in the group are currently visible on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     * @return true if all templates were found and the action was run, false otherwise
     */
    public static boolean ifExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        if (existsAll(group, source)) {
            action.run();
            return true;
        }
        return false;
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as any template in the group stays visible.
     *
     * @param group  the template group to search for
     * @param action the action to run with each match result
     */
    public static void whileExistsAny(ImageTemplateGroup group, Consumer<MatchResult> action) {
        whileExistsAny(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} (with the fresh match each time) as long as any template in the group stays visible on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run with each match result
     */
    public static void whileExistsAny(ImageTemplateGroup group, CaptureSource source, Consumer<MatchResult> action) {
        MatchResult result;
        while ((result = findAnyInternal(source, ClickConfig.DEFAULT_CONFIDENCE, group.toArray())).isFound()) {
            VisionContext.setLastMatch(result);
            action.accept(result);
        }
        VisionContext.setLastMatch(MatchResult.notFound());
    }

    /**
     * Keep running {@code action} as long as all templates in the group stay visible.
     *
     * @param group  the template group to search for
     * @param action the action to run
     */
    public static void whileExistsAll(ImageTemplateGroup group, Runnable action) {
        whileExistsAll(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} as long as all templates in the group stay visible on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     */
    public static void whileExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (existsAll(group, source)) {
            action.run();
        }
    }

    /**
     * Keep running {@code action} until any template in the group appears.
     *
     * @param group  the template group to search for
     * @param action the action to run
     */
    public static void untilExistsAny(ImageTemplateGroup group, Runnable action) {
        untilExistsAny(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} until any template in the group appears on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     */
    public static void untilExistsAny(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (!exists(group, source)) {
            action.run();
        }
    }

    /**
     * Keep running {@code action} until all templates in the group appear.
     *
     * @param group  the template group to search for
     * @param action the action to run
     */
    public static void untilExistsAll(ImageTemplateGroup group, Runnable action) {
        untilExistsAll(group, Source.current(), action);
    }

    /**
     * Keep running {@code action} until all templates in the group appear on a specific source.
     *
     * @param group  the template group to search for
     * @param source the capture source to search within
     * @param action the action to run
     */
    public static void untilExistsAll(ImageTemplateGroup group, CaptureSource source, Runnable action) {
        while (!existsAll(group, source)) {
            action.run();
        }
    }
}
