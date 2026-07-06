package com.botmaker.sdk.internal.capture;

import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * Central full-desktop capture facade, delegated to a {@link CaptureBackend} selected for the current
 * platform (Robot on X11/Windows, Spectacle on KDE Wayland). This is the single entry point for
 * whole-desktop capture ({@link com.botmaker.sdk.api.capture.Screen} routes through it). Per-window
 * capture lives in the shared module ({@code com.botmaker.shared.capture.windows.WindowCapture} and the
 * Linux controller).
 */
public class ScreenCapture {

	/**
	 * Capture all monitors as a single image
	 * This captures the virtual screen bounds that encompasses all monitors
	 */
	public static BufferedImage captureDesktop() {
		return CaptureBackend.select().captureDesktop();
	}

	/**
	 * Get the virtual screen bounds that encompasses all monitors.
	 * Single source of truth for multi-monitor bounds, shared by the capture backends.
	 */
	public static Rectangle getVirtualScreenBounds() {
		Rectangle virtualBounds = new Rectangle();

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();

		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration config = screen.getDefaultConfiguration();
			Rectangle bounds = config.getBounds();
			virtualBounds = virtualBounds.union(bounds);
		}

		return virtualBounds;
	}
}