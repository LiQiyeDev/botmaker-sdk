package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.internal.opencv.OpencvManager;
import com.botmaker.sdk.internal.opencv.RawMatch;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageState {

    public static List<String> findWhichAreVisible(ImageTemplate... templates) {
        return findWhichAreVisible(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static List<String> findWhichAreVisible(Rect region, ImageTemplate... templates) {
        return findWhichAreVisible(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static List<String> findWhichAreVisible(Rect region, double confidence, ImageTemplate... templates) {
        return new ArrayList<>(computeState(CaptureSource.screen(), region, confidence, templates).getVisibleIds());
    }

    public static boolean contains(List<String> visibleIds, String templateId) {
        return visibleIds.contains(templateId);
    }

    public static boolean contains(List<String> visibleIds, ImageTemplate template) {
        return visibleIds.contains(template.getId());
    }

    public static boolean containsAll(List<String> visibleIds, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (!visibleIds.contains(template.getId())) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsAll(List<String> visibleIds, String... templateIds) {
        for (String id : templateIds) {
            if (!visibleIds.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsAny(List<String> visibleIds, ImageTemplate... templates) {
        for (ImageTemplate template : templates) {
            if (visibleIds.contains(template.getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(List<String> visibleIds, String... templateIds) {
        for (String id : templateIds) {
            if (visibleIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, MatchResult> findWhichAreVisibleDetailed(ImageTemplate... templates) {
        return findWhichAreVisibleDetailed(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static Map<String, MatchResult> findWhichAreVisibleDetailed(Rect region, double confidence,
                                                                       ImageTemplate... templates) {
        return computeState(CaptureSource.screen(), region, confidence, templates).visibleResults;
    }

    /**
     * Capture {@code source} <b>once</b> and evaluate every template against it, returning the full
     * {@link ScreenState} (visible ids + detailed match results in the source's absolute coordinate
     * space). This is the single-capture core shared by {@link #checkState} and {@code Vision.evaluate};
     * it replaces the old two-capture path where visible-ids and detailed-results were computed by
     * two separate screen grabs.
     */
    static ScreenState computeState(CaptureSource source, Rect region, double confidence,
                                    ImageTemplate... templates) {
        List<String> ids = new ArrayList<>();
        Map<String, MatchResult> results = new HashMap<>();

        BufferedImage screenshot = source.capture();
        if (screenshot == null) {
            return new ScreenState(ids, results);
        }

        Mat background = OpencvManager.bufferedImageToMat(screenshot);

        Point origin = source.origin();
        int offsetX = (region != null ? region.x : 0) + (int) origin.x;
        int offsetY = (region != null ? region.y : 0) + (int) origin.y;

        try {
            for (ImageTemplate template : templates) {
                RawMatch match = OpencvManager.findBestMatch(template.getMat(), background, false, confidence);

                if (match != null) {
                    ids.add(template.getId());
                    Point location = new Point(match.x() + offsetX, match.y() + offsetY);
                    results.put(template.getId(), new MatchResult(
                            location,
                            match.width(),
                            match.height(),
                            match.score(),
                            template.getId()
                    ));
                }
            }
        } finally {
            background.release();
        }

        return new ScreenState(ids, results);
    }

    public static class ScreenState {
        private final List<String> visibleIds;
        private final Map<String, MatchResult> visibleResults;

        private ScreenState(List<String> ids, Map<String, MatchResult> results) {
            this.visibleIds = ids;
            this.visibleResults = results;
        }

        public boolean has(ImageTemplate template) {
            return visibleIds.contains(template.getId());
        }

        public boolean has(String templateId) {
            return visibleIds.contains(templateId);
        }

        public boolean hasAll(ImageTemplate... templates) {
            return containsAll(visibleIds, templates);
        }

        public boolean hasAny(ImageTemplate... templates) {
            return containsAny(visibleIds, templates);
        }

        public MatchResult get(ImageTemplate template) {
            return visibleResults.get(template.getId());
        }

        public boolean click(ImageTemplate template) {
            MatchResult result = visibleResults.get(template.getId());
            if (result == null) {
                return false;
            }

            Point clickPoint = ClickConfig.RANDOMIZE_CLICKS ?
                    result.getRandomClickPoint() :
                    result.getCenter();

            Mouse.click(clickPoint);
            Wait.milliseconds(ClickConfig.DEFAULT_FOUND_DELAY);
            return true;
        }

        public boolean clickUntilGone(ImageTemplate template) {
            if (!has(template)) {
                return false;
            }

            while (ImageClicker.click(template));
            return true;
        }

        public List<String> getVisibleIds() {
            return new ArrayList<>(visibleIds);
        }

        public int getVisibleCount() {
            return visibleIds.size();
        }

        @Override
        public String toString() {
            return "ScreenState[visible=" + visibleIds + "]";
        }
    }

    public static ScreenState checkState(ImageTemplate... templates) {
        return checkState((Rect) null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static ScreenState checkState(Rect region, ImageTemplate... templates) {
        return checkState(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static ScreenState checkState(Rect region, double confidence, ImageTemplate... templates) {
        return computeState(CaptureSource.screen(), region, confidence, templates);
    }

    // --- Source-targeted state (used by Vision.evaluate to check a specific window) ---

    public static ScreenState checkState(CaptureSource source, ImageTemplate... templates) {
        return computeState(source, null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static ScreenState checkState(CaptureSource source, double confidence, ImageTemplate... templates) {
        return computeState(source, null, confidence, templates);
    }
}