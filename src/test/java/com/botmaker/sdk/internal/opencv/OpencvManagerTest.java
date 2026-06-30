package com.botmaker.sdk.internal.opencv;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the template-matching engine on the real {@code accept_button.png}, embedding it into
 * synthetic backgrounds so the expected match location is known exactly. These are deterministic and
 * do not require a display.
 */
class OpencvManagerTest {

    private static final Path IMAGE = Path.of("src/main/resources/images/accept_button.png");

    private static BufferedImage template;

    @BeforeAll
    static void load() throws IOException {
        assumeTrue(Files.exists(IMAGE), "accept_button.png is required for these tests");
        template = ImageIO.read(IMAGE.toFile());
    }

    /** Fills an image with deterministic pseudo-random noise (non-uniform, so correlation is defined). */
    private static BufferedImage noise(int width, int height, long seed) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        return img;
    }

    /** Background of noise with the template pasted at the given offset. */
    private static BufferedImage backgroundWithTemplate(int offsetX, int offsetY) {
        BufferedImage bg = noise(template.getWidth() + 400, template.getHeight() + 300, 7);
        bg.getGraphics().drawImage(template, offsetX, offsetY, null);
        return bg;
    }

    @Test
    void bufferedImageToMatPreservesDimensions() {
        Mat mat = OpencvManager.bufferedImageToMat(template);
        try {
            assertEquals(template.getWidth(), mat.cols());
            assertEquals(template.getHeight(), mat.rows());
            assertEquals(3, mat.channels());
            assertFalse(mat.empty());
        } finally {
            mat.release();
        }
    }

    @Test
    void findBestMatchLocatesEmbeddedTemplate() {
        int offX = 120, offY = 90;
        Mat tpl = OpencvManager.bufferedImageToMat(template);
        Mat bg = OpencvManager.bufferedImageToMat(backgroundWithTemplate(offX, offY));
        try {
            RawMatch m = OpencvManager.findBestMatch(tpl, bg, false, 0.9);

            assertNotNull(m, "the template was embedded, so it must be found");
            assertEquals(offX, m.x());
            assertEquals(offY, m.y());
            assertEquals(template.getWidth(), m.width());
            assertEquals(template.getHeight(), m.height());
            assertTrue(m.score() > 0.95, "exact paste should score near 1.0, was " + m.score());
        } finally {
            tpl.release();
            bg.release();
        }
    }

    @Test
    void findBestMatchWorksInGrayscale() {
        int offX = 60, offY = 40;
        Mat tpl = OpencvManager.bufferedImageToMat(template);
        Mat bg = OpencvManager.bufferedImageToMat(backgroundWithTemplate(offX, offY));
        try {
            RawMatch m = OpencvManager.findBestMatch(tpl, bg, true, 0.9);

            assertNotNull(m);
            assertEquals(offX, m.x());
            assertEquals(offY, m.y());
        } finally {
            tpl.release();
            bg.release();
        }
    }

    @Test
    void findBestMatchReturnsNullWhenAbsent() {
        Mat tpl = OpencvManager.bufferedImageToMat(template);
        Mat bg = OpencvManager.bufferedImageToMat(noise(template.getWidth() + 400, template.getHeight() + 300, 99));
        try {
            // Pure noise contains no button; a high threshold must reject any spurious peak.
            assertNull(OpencvManager.findBestMatch(tpl, bg, false, 0.95));
        } finally {
            tpl.release();
            bg.release();
        }
    }

    @Test
    void findBestMatchReturnsNullWhenBackgroundSmallerThanTemplate() {
        Mat tpl = OpencvManager.bufferedImageToMat(template);
        Mat bg = OpencvManager.bufferedImageToMat(noise(10, 10, 1));
        try {
            assertNull(OpencvManager.findBestMatch(tpl, bg, false, 0.9));
        } finally {
            tpl.release();
            bg.release();
        }
    }

    @Test
    void findMultipleMatchesReturnsOnePerOccurrence() {
        Mat tpl = OpencvManager.bufferedImageToMat(template);
        // Two well-separated copies of the template on a noise canvas.
        BufferedImage canvas = noise(template.getWidth() * 2 + 300, template.getHeight() + 200, 3);
        canvas.getGraphics().drawImage(template, 20, 20, null);
        canvas.getGraphics().drawImage(template, template.getWidth() + 200, 120, null);
        Mat bg = OpencvManager.bufferedImageToMat(canvas);
        try {
            List<RawMatch> matches = OpencvManager.findMultipleMatches(tpl, bg, false, 0.9);

            assertEquals(2, matches.size(), "non-maximal suppression should yield one match per occurrence");
            assertTrue(matches.stream().allMatch(m -> m.score() > 0.9));
        } finally {
            tpl.release();
            bg.release();
        }
    }
}
