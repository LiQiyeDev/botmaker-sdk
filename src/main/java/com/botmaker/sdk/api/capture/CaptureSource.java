package com.botmaker.sdk.api.capture;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;

import java.awt.image.BufferedImage;

/**
 * <em>Where</em> the vision layer looks — exactly one of three things: a {@link Window}, a single
 * {@link #monitor(int) monitor}, or the whole {@link #desktop() desktop}. Every matcher
 * ({@code ImageFinder}/{@code ImageClicker}/{@code ImageWaiter}) takes a {@code CaptureSource} instead of a
 * loose screen rectangle, so a template is matched — and clicked — in the source's own coordinate space
 * (even off-screen or on a second monitor) without duplicating any matching logic: the matcher runs on
 * {@link #capture()} and converts in-image match coordinates to absolute screen coordinates by adding
 * {@link #origin()}.
 *
 * <p>A search <em>region</em> is not a separate concept — it is a {@link Rect} <em>of</em> a source: narrow
 * any of the three with {@link #region(Rect)} to get a sub-source that only captures (and therefore only
 * matches within) that rectangle. Regions compose, so
 * {@code CaptureSource.window("Game").region(topBar)} is itself just another {@code CaptureSource}.
 */
public interface CaptureSource {

    /** Pixels of this source. May return {@code null} if the capture failed. */
    BufferedImage capture();

    /**
     * Absolute screen coordinate of pixel (0,0) of the image returned by {@link #capture()}.
     * Add this to an in-image match location to obtain an absolute, clickable coordinate.
     */
    Point origin();

    // --- The three canonical sources ---

    /** The whole virtual desktop (all monitors). The default source for every matcher. */
    static CaptureSource desktop() {
        return Screen.asSource();
    }

    /**
     * A single monitor (0-based {@code index} into the OS screen-device list), so a bot can match against
     * just one screen on a multi-monitor desktop. An out-of-range index falls back to the whole desktop.
     */
    static CaptureSource monitor(int index) {
        return Screen.monitorSource(index);
    }

    /**
     * The first window whose title contains {@code titleSubstring} (case-insensitive) as a capture source,
     * or the whole {@link #desktop()} if none matches. A convenience so a capture-source slot can target a
     * window by title in one call without unwrapping the {@link Window#find(String)} {@code Optional}.
     */
    static CaptureSource window(String titleSubstring) {
        return Window.find(titleSubstring).map(w -> (CaptureSource) w).orElseGet(CaptureSource::desktop);
    }

    // --- Region: a Rect that belongs to THIS source ---

    /**
     * A sub-source that only captures the {@code sub} rectangle within this source's pixel space (its
     * top-left is {@code (0,0)} of {@link #capture()}). Matches are still reported in absolute screen
     * coordinates because {@link #origin()} shifts by {@code sub}'s top-left. Because it actually crops the
     * captured image, a region also restricts (and speeds up) the search area — not just the reported
     * coordinates. The rectangle is clamped to the source's bounds.
     */
    default CaptureSource region(Rect sub) {
        CaptureSource parent = this;
        return new CaptureSource() {
            @Override
            public BufferedImage capture() {
                BufferedImage img = parent.capture();
                if (img == null) return null;
                int x = Math.max(0, sub.x);
                int y = Math.max(0, sub.y);
                int w = Math.min(sub.width, img.getWidth() - x);
                int h = Math.min(sub.height, img.getHeight() - y);
                if (w <= 0 || h <= 0) return null;
                return img.getSubimage(x, y, w, h);
            }

            @Override
            public Point origin() {
                Point o = parent.origin();
                return new Point(o.x + Math.max(0, sub.x), o.y + Math.max(0, sub.y));
            }

            @Override
            public CaptureSource base() {
                return parent.base();
            }

            @Override
            public Rect subRegion() {
                Rect pr = parent.subRegion();
                int bx = (pr != null ? pr.x : 0) + Math.max(0, sub.x);
                int by = (pr != null ? pr.y : 0) + Math.max(0, sub.y);
                return new Rect(bx, by, sub.width, sub.height);
            }
        };
    }

    /** {@link #region(Rect)} from raw coordinates within this source. */
    default CaptureSource region(int x, int y, int width, int height) {
        return region(new Rect(x, y, width, height));
    }

    // --- Observability hooks (used by api.observe to describe the surface + searched region) ---

    /**
     * The underlying whole-surface source (a {@link Window} or the screen) this source draws from, unwrapping
     * any {@link #region(Rect)} narrowing. Defaults to {@code this}; only region sub-sources override it.
     */
    default CaptureSource base() {
        return this;
    }

    /**
     * The searched rectangle within {@link #base()} (in that surface's pixel space), or {@code null} when
     * this source captures the whole surface. Only region sub-sources return a non-null value.
     */
    default Rect subRegion() {
        return null;
    }
}
