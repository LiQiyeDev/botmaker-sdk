package com.botmaker.sdk.internal.opencv;

import com.botmaker.sdk.api.vision.ImageTemplate;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Internal class that holds the Native OpenCV Mat.
 * Handles the actual loading of files and BufferedImage conversion.
 */
public class Template implements AutoCloseable {

    public final Mat mat;
    public final String id;

    // 1. Constructor for Raw Mats (used by ScreenCapture)
    public Template(Mat mat, String id) {
        this.mat = mat;
        this.id = id;
    }

    // 2. Constructor for String Path
    public Template(String path) {
        this.id = path;

        // Ensure absolute path for OpenCV loader
        String absPath = new File(path).getAbsolutePath();

        // Load immediately
        this.mat = Imgcodecs.imread(absPath);

        if (this.mat.empty()) {
            throw new RuntimeException("Failed to load image template. Path: " + absPath);
        }
    }

    // 3. Constructor for API Bridge (Delegates to String Path)
    public Template(ImageTemplate apiTemplate) {
        this(apiTemplate.getFilePath());
    }

    // 4. NEW: Constructor for BufferedImage
    public Template(BufferedImage image, String id) {
        this.id = id;
        if (image == null) {
            throw new IllegalArgumentException("BufferedImage cannot be null");
        }
        this.mat = OpencvManager.bufferedImageToMat(image);
    }

    public int width() {
        return mat.cols();
    }

    public int height() {
        return mat.rows();
    }

    public boolean empty() {
        return mat.empty();
    }

    public org.opencv.core.Size size() {
        return mat.size();
    }

    public int cols() {
        return mat.cols();
    }

    public int rows() {
        return mat.rows();
    }

    public Template clone() {
        return new Template(mat.clone(), id);
    }

    @Override
    public void close() {
        if (mat != null) {
            mat.release();
        }
    }
}