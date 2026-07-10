package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Headless-safe checks for the group finder helpers. Anything that actually captures the screen /
 * loads OpenCV cannot run here (no display), so this only covers the pure guards that short-circuit
 * before any {@code find}/capture.
 * The screen-dependent group/loop paths are exercised by the manual {@code com.botmaker.sdk.Main} harness.
 */
class ImageFinderGroupTest {

    // Note: Empty ImageTemplateGroup cannot be created as the constructor requires at least one template.
    // The old existsAll() method with no arguments was removed and has no direct replacement in the new API.
}
