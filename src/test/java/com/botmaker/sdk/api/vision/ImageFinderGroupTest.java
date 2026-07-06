package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Headless-safe checks for the group finder helpers. Anything that actually captures the screen /
 * loads OpenCV cannot run here (no display), so this only covers the pure guards that short-circuit
 * before any {@code find}/capture — currently the empty-input contract of {@link ImageFinder#existsAll}.
 * The screen-dependent group/loop paths are exercised by the manual {@code com.botmaker.sdk.Main} harness.
 */
class ImageFinderGroupTest {

    @Test
    void existsAllWithNoTemplatesIsFalse() {
        // Zero templates cannot all be "present" — must be false, and must not touch capture/OpenCV.
        assertFalse(ImageFinder.existsAll());
    }
}
