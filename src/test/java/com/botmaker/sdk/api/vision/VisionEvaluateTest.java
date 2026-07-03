package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VisionEvaluateTest {

    /** A source that counts how many times it is captured; returns null so matching short-circuits
     *  (keeps the test free of OpenCV natives and template image files). */
    private static class CountingSource implements CaptureSource {
        final AtomicInteger captures = new AtomicInteger();
        @Override public BufferedImage capture() { captures.incrementAndGet(); return null; }
        @Override public Point origin() { return new Point(0, 0); }
    }

    @Test
    void evaluateCapturesOnceAndInvokesCallbackOnce() {
        CountingSource source = new CountingSource();
        AtomicInteger callbackCount = new AtomicInteger();

        Vision.evaluate(source, state -> {
            callbackCount.incrementAndGet();
            assertNotNull(state);
            assertEquals(0, state.getVisibleCount()); // null capture => nothing visible
        });

        assertEquals(1, source.captures.get(), "evaluate must capture the source exactly once");
        assertEquals(1, callbackCount.get(), "callback must be invoked exactly once");
    }

    @Test
    void snapshotReturnsStateWithoutCallback() {
        CountingSource source = new CountingSource();
        ImageState.ScreenState state = Vision.snapshot(source);
        assertNotNull(state);
        assertEquals(1, source.captures.get());
    }
}
