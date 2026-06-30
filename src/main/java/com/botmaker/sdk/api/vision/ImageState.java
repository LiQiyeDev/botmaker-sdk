package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.capture.Screen;
import com.botmaker.sdk.api.interaction.Mouse;
import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.internal.opencv.InternalMatch;
import com.botmaker.sdk.internal.opencv.MatType;
import com.botmaker.sdk.internal.opencv.OpencvManager;
import com.botmaker.sdk.internal.opencv.Template;

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
        List<String> visibleIds = new ArrayList<>();

        BufferedImage screenshot = Screen.capture();

        if (screenshot == null) {
            return visibleIds;
        }

        Template background = new Template(
                OpencvManager.bufferedImageToMat(screenshot),
                "background"
        );

        for (ImageTemplate template : templates) {
            InternalMatch result =
                    OpencvManager.findBestMatch(
                            template.getInternalTemplate(),
                            background,
                            MatType.COLOR,
                            confidence
                    );

            if (result != null && result.isMatch()) {
                visibleIds.add(template.getId());
            }
        }

        return visibleIds;
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
        Map<String, MatchResult> results = new HashMap<>();

        BufferedImage screenshot = Screen.capture();

        if (screenshot == null) {
            return results;
        }

        Template background = new Template(
                OpencvManager.bufferedImageToMat(screenshot),
                "background"
        );

        int offsetX = region != null ? region.x : 0;
        int offsetY = region != null ? region.y : 0;

        for (ImageTemplate template : templates) {
            InternalMatch internalResult =
                    OpencvManager.findBestMatch(
                            template.getInternalTemplate(),
                            background,
                            MatType.COLOR,
                            confidence
                    );

            if (internalResult != null && internalResult.isMatch()) {
                org.opencv.core.Rect rect = internalResult.rectLocation;
                Point location = new Point(rect.x + offsetX, rect.y + offsetY);

                MatchResult result = new MatchResult(
                        location,
                        rect.width,
                        rect.height,
                        internalResult.getScore(),
                        template.getId()
                );

                results.put(template.getId(), result);
            }
        }

        return results;
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
        return checkState(null, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static ScreenState checkState(Rect region, ImageTemplate... templates) {
        return checkState(region, ClickConfig.DEFAULT_CONFIDENCE, templates);
    }

    public static ScreenState checkState(Rect region, double confidence, ImageTemplate... templates) {
        List<String> ids = findWhichAreVisible(region, confidence, templates);
        Map<String, MatchResult> results = findWhichAreVisibleDetailed(region, confidence, templates);
        return new ScreenState(ids, results);
    }
}