package com.botmaker.sdk.internal.capture;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Utility for capturing screens across multiple monitors
 */
public class MultiMonitorCapture {

	/**
	 * Get the bounding rectangle that encompasses all monitors
	 * This works on Windows, Linux, and macOS
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

	/**
	 * Capture all monitors as a single image
	 * The image will include all screens in their relative positions
	 */
	public static BufferedImage captureAllMonitors() throws AWTException {
		Rectangle virtualBounds = getVirtualScreenBounds();
		return new Robot().createScreenCapture(virtualBounds);
	}

	/**
	 * Get information about all connected monitors
	 */
	public static MonitorInfo[] getMonitorInfo() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();

		MonitorInfo[] monitors = new MonitorInfo[screens.length];

		for (int i = 0; i < screens.length; i++) {
			GraphicsConfiguration config = screens[i].getDefaultConfiguration();
			Rectangle bounds = config.getBounds();

			monitors[i] = new MonitorInfo(
				i,
				bounds,
				screens[i].getIDstring(),
				screens[i] == ge.getDefaultScreenDevice()
			);
		}

		return monitors;
	}

	/**
	 * Capture a specific monitor by index
	 */
	public static BufferedImage captureMonitor(int monitorIndex) throws AWTException {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();

		if (monitorIndex < 0 || monitorIndex >= screens.length) {
			throw new IllegalArgumentException("Monitor index " + monitorIndex +
				" out of range (0-" + (screens.length - 1) + ")");
		}

		GraphicsConfiguration config = screens[monitorIndex].getDefaultConfiguration();
		Rectangle bounds = config.getBounds();

		return new Robot().createScreenCapture(bounds);
	}

	/**
	 * Information about a monitor
	 */
	public static class MonitorInfo {
		public final int index;
		public final Rectangle bounds;
		public final String id;
		public final boolean isPrimary;

		public MonitorInfo(int index, Rectangle bounds, String id, boolean isPrimary) {
			this.index = index;
			this.bounds = bounds;
			this.id = id;
			this.isPrimary = isPrimary;
		}

		@Override
		public String toString() {
			return String.format("Monitor %d: %dx%d at (%d,%d) [%s]%s",
				index,
				bounds.width, bounds.height,
				bounds.x, bounds.y,
				id,
				isPrimary ? " PRIMARY" : ""
			);
		}
	}
}