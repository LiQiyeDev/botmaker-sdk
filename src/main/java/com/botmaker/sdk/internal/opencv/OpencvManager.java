package com.botmaker.sdk.internal.opencv;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.opencv.imgproc.Imgproc.TM_CCOEFF_NORMED;
import static org.opencv.imgproc.Imgproc.matchTemplate;

/**
 * Template-matching engine. Operates directly on OpenCV {@link Mat}s and returns plain
 * {@link RawMatch} records (no OpenCV types leak out). The native library is guaranteed loaded by
 * the static initializer.
 */
public final class OpencvManager {

    static { OpenCvNative.ensureLoaded(); }

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.8;
    private static final double DEFAULT_OVERLAP_THRESHOLD = 0.5;

    private OpencvManager() {}

    // --- Conversion ------------------------------------------------------------------------------

    public static Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        converted.getGraphics().drawImage(image, 0, 0, null);

        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        byte[] pixels = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, pixels);
        return mat;
    }

    private static boolean isRGBA(Mat mat) { return mat.channels() == 4; }
    private static boolean isRGB(Mat mat)  { return mat.channels() == 3; }
    private static boolean isGray(Mat mat) { return mat.channels() == 1; }

    /** Normalises {@code mat} in place to either 3-channel BGR or single-channel gray. */
    private static void normalise(Mat mat, boolean grayscale) {
        if (grayscale) {
            if (isRGB(mat))       Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
            else if (isRGBA(mat)) Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
        } else {
            if (isGray(mat))      Imgproc.cvtColor(mat, mat, Imgproc.COLOR_GRAY2RGB);
            else if (isRGBA(mat)) Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB);
        }
    }

    // --- Matching --------------------------------------------------------------------------------

    public static RawMatch findBestMatch(Mat template, Mat background, boolean grayscale) {
        return findBestMatch(template, background, grayscale, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * Returns the single best match of {@code template} within {@code background} whose score meets
     * {@code confidenceThreshold}, or {@code null} if none qualifies.
     */
    public static RawMatch findBestMatch(Mat template, Mat background, boolean grayscale, double confidenceThreshold) {
        Mat localTemplate = template.clone();
        Mat localBackground = background.clone();
        Mat resultMat = new Mat();
        try {
            normalise(localTemplate, grayscale);
            normalise(localBackground, grayscale);

            if (localBackground.width() < localTemplate.width() || localBackground.height() < localTemplate.height()) {
                System.err.println("Error: Template dimensions are larger than the background image.");
                return null;
            }

            matchTemplate(localBackground, localTemplate, resultMat, TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(resultMat);

            if (mmr.maxVal < confidenceThreshold) {
                return null;
            }
            Point loc = mmr.maxLoc;
            return new RawMatch((int) loc.x, (int) loc.y, localTemplate.cols(), localTemplate.rows(), mmr.maxVal);
        } finally {
            localTemplate.release();
            localBackground.release();
            resultMat.release();
        }
    }

    public static List<RawMatch> findMultipleMatches(Mat template, Mat background, boolean grayscale) {
        return findMultipleMatches(template, background, grayscale, DEFAULT_CONFIDENCE_THRESHOLD, DEFAULT_OVERLAP_THRESHOLD);
    }

    public static List<RawMatch> findMultipleMatches(Mat template, Mat background, boolean grayscale, double confidenceThreshold) {
        return findMultipleMatches(template, background, grayscale, confidenceThreshold, DEFAULT_OVERLAP_THRESHOLD);
    }

    /**
     * Returns every non-overlapping match (via non-maximal suppression) at or above
     * {@code confidenceThreshold}.
     */
    public static List<RawMatch> findMultipleMatches(Mat template, Mat background, boolean grayscale,
                                                     double confidenceThreshold, double overlapThreshold) {
        if (template.empty() || background.empty()
                || background.width() < template.width() || background.height() < template.height()) {
            System.err.println("Error: Invalid input images for findMultipleMatches.");
            return new ArrayList<>();
        }

        Mat localTemplate = template.clone();
        Mat localBackground = background.clone();
        Mat resultMat = new Mat();
        try {
            normalise(localTemplate, grayscale);
            normalise(localBackground, grayscale);

            matchTemplate(localBackground, localTemplate, resultMat, TM_CCOEFF_NORMED);

            int w = localTemplate.cols();
            int h = localTemplate.rows();
            List<RawMatch> candidates = new ArrayList<>();
            for (int y = 0; y < resultMat.rows(); y++) {
                for (int x = 0; x < resultMat.cols(); x++) {
                    double score = resultMat.get(y, x)[0];
                    if (score >= confidenceThreshold) {
                        candidates.add(new RawMatch(x, y, w, h, score));
                    }
                }
            }
            if (candidates.isEmpty()) {
                return candidates;
            }

            // Non-maximal suppression: keep highest-scoring, drop overlapping competitors.
            candidates.sort(Comparator.comparingDouble(RawMatch::score).reversed());
            List<RawMatch> winners = new ArrayList<>();
            while (!candidates.isEmpty()) {
                RawMatch champion = candidates.removeFirst();
                winners.add(champion);
                candidates.removeIf(c -> intersectionOverUnion(champion, c) > overlapThreshold);
            }
            return winners;
        } finally {
            localTemplate.release();
            localBackground.release();
            resultMat.release();
        }
    }

    private static double intersectionOverUnion(RawMatch a, RawMatch b) {
        int xA = Math.max(a.x(), b.x());
        int yA = Math.max(a.y(), b.y());
        int xB = Math.min(a.x() + a.width(), b.x() + b.width());
        int yB = Math.min(a.y() + a.height(), b.y() + b.height());

        int intersection = Math.max(0, xB - xA) * Math.max(0, yB - yA);
        double union = (double) a.width() * a.height() + (double) b.width() * b.height() - intersection;
        return union <= 0 ? 0 : intersection / union;
    }
}
