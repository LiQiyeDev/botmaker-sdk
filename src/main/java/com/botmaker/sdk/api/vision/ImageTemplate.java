package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.internal.opencv.Template;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Public API class for Image Templates.
 * It holds the configuration (Path, Threshold, ID) and lazily manages the internal OpenCV wrapper.
 */
public class ImageTemplate {

    private final String filePath;
    private final String id;
    private double threshold = 0.8; // Default confidence

    // The internal wrapper that holds the actual OpenCV Mat.
    // We do NOT import org.opencv.core.Mat here to keep the API clean.
    private Template internalTemplate;

    /**
     * Constructor using file path.
     * @param filePath Path to the image (e.g. "images/accept_button.png")
     */
    public ImageTemplate(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path cannot be empty");
        }
        this.filePath = filePath;

        // Extract ID from filename using java.nio.file.Path
        // e.g. "images/btn_ok.png" -> "btn_ok"
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        this.id = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Constructor with custom threshold.
     */
    public ImageTemplate(String filePath, double threshold) {
        this(filePath);
        this.threshold = threshold;
    }

    /**
     * Returns the ID derived from the filename.
     * Used by ImageState and logging.
     */
    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Retrieves the internal Template object.
     * Loads the image from disk into memory if strictly necessary.
     */
    public Template getInternalTemplate() {
        // Lazy loading: Only load the heavy OpenCV object when actually needed by ImageFinder
        if (internalTemplate == null || internalTemplate.empty()) {
            // This uses the internal Template(String path) constructor
            this.internalTemplate = new Template(filePath);
        }
        return internalTemplate;
    }

    /**
     * Explicitly releases the internal image memory.
     * Useful if you want to unload a specific template.
     */
    public void unload() {
        if (internalTemplate != null) {
            internalTemplate.close();
            internalTemplate = null;
        }
    }

    @Override
    public String toString() {
        return "ImageTemplate{id='" + id + "', path='" + filePath + "'}";
    }
}