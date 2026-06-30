package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.Screen;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.internal.opencv.OpencvManager;
import com.botmaker.sdk.internal.opencv.RawMatch;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImageFinder {

    public static MatchResult find(ImageTemplate template) {
        return find(template, null, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, Rect region) {
        return find(template, region, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult find(ImageTemplate template, double confidence) {
        return find(template, null, confidence);
    }

    public static MatchResult find(ImageTemplate template, Rect region, double confidence) {
        // Note: a genuine native-load failure surfaces as an Error (e.g. UnsatisfiedLinkError),
        // which is intentionally NOT caught here so it cannot masquerade as "not found".
        Mat background = null;
        try {
            BufferedImage screenshot = Screen.capture();

            if (screenshot == null) {
                return MatchResult.notFound();
            }

            background = OpencvManager.bufferedImageToMat(screenshot);

            RawMatch match = OpencvManager.findBestMatch(template.getMat(), background, false, confidence);

            if (match != null) {
                int offsetX = region != null ? region.x : 0;
                int offsetY = region != null ? region.y : 0;

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
        Mat background = null;
        try {
            BufferedImage screenshot = Screen.capture();

            if (screenshot == null) {
                return new ArrayList<>();
            }

            background = OpencvManager.bufferedImageToMat(screenshot);

            List<RawMatch> matches =
                    OpencvManager.findMultipleMatches(template.getMat(), background, false, confidence);

            int offsetX = region != null ? region.x : 0;
            int offsetY = region != null ? region.y : 0;

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

    public static MatchResult findBest(ImageTemplate... templates) {
        return findBest(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findBest(Rect region, ImageTemplate... templates) {
        return findBest(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static MatchResult findBest(Rect region, double confidence, ImageTemplate... templates) {
        MatchResult best = MatchResult.notFound();

        for (ImageTemplate template : templates) {
            MatchResult result = find(template, region, confidence);
            if (result.isFound() && result.getConfidence() > best.getConfidence()) {
                best = result;
            }
        }

        return best;
    }

    public static MatchResult retryUntilFound(ImageTemplate template, int maxAttempts) {
        return retryUntilFound(template, null, maxAttempts, ClickConfig.DEFAULT_CONFIDENCE);
    }

    public static MatchResult retryUntilFound(ImageTemplate template, Rect region, int maxAttempts, double confidence) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            MatchResult result = find(template, region, confidence);

            if (result.isFound()) {
                return result;
            }

            Wait.milliseconds(ClickConfig.DEFAULT_NOT_FOUND_DELAY);
        }

        return MatchResult.notFound();
    }
}