package com.botmaker.sdk.internal.opencv;

import org.opencv.core.Rect;
import com.botmaker.sdk.internal.opencv.MatType;

public class InternalMatch {
    // Keep internal OpenCV Rect for OpencvManager logic
    public Rect rectLocation;
    public double score;
    public double confidenceThreshold;
    public String winningTemplateId;
    public String winningBackgroundId;
    public final MatType matType;

    public InternalMatch(Rect rect, double score, double threshold, String winningTemplateId, String winningBackgroundId, MatType matType) {
        this.rectLocation = rect;
        this.score = score;
        this.confidenceThreshold = threshold;
        this.winningTemplateId = winningTemplateId;
        this.winningBackgroundId = winningBackgroundId;
        this.matType = matType;
    }

    private InternalMatch() {
        this.rectLocation = new Rect();
        this.score = 0;
        this.confidenceThreshold = 0;
        this.winningTemplateId = null;
        this.winningBackgroundId = null;
        this.matType = null;
    }

    public static InternalMatch noMatch() {
        return new InternalMatch();
    }

    /**
     * Converts the internal OpenCV Rect to the API Rect.
     */
    public com.botmaker.sdk.api.Rect getRect() {
        if (rectLocation == null) {
            return new com.botmaker.sdk.api.Rect(0, 0, 0, 0);
        }
        return new com.botmaker.sdk.api.Rect(
                rectLocation.x,
                rectLocation.y,
                rectLocation.width,
                rectLocation.height
        );
    }

    public String getTemplateId() {
        return winningTemplateId;
    }

    public String getBackgroundId() {
        return winningBackgroundId;
    }

    public double getScore(){
        return score;
    }

    public Boolean isMatch(){
        if (winningTemplateId == null) {
            return false;
        }
        return score >= confidenceThreshold;
    }

    @Override
    public String toString() {
        if (winningTemplateId == null) {
            return "MatchResult [No Match]";
        }
        return String.format("MatchResult [Template: %s, Background: %s, Score: %.4f, Location: %s, Threshold: %.4f, MatType: %s, IsSignificant: %b]",
                winningTemplateId != null ? winningTemplateId : "N/A",
                winningBackgroundId != null ? winningBackgroundId : "N/A",
                score,
                rectLocation != null ? rectLocation.toString() : "N/A",
                confidenceThreshold,
                matType,
                isMatch());
    }
}