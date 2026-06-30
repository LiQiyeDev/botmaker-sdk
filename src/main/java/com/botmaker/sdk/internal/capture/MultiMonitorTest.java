package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.internal.capture.core.NativeController;
import com.botmaker.sdk.internal.capture.core.NativeControllerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Test to demonstrate multi-monitor capture capabilities
 */
public class MultiMonitorTest {

	public static void main(String[] args) throws Exception {
		System.out.println("=== Multi-Monitor Capture Test ===\n");

		// Display monitor information
		displayMonitorInfo();

		// Test 1: Capture all monitors
		testCaptureAllMonitors();

		// Test 2: Capture specific monitor
		testCaptureSpecificMonitor();

		// Test 3: Compare old vs new method
		testOldVsNewCapture();
	}

	private static void displayMonitorInfo() {
		System.out.println("Connected Monitors:");

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();
		GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();

		for (int i = 0; i < screens.length; i++) {
			GraphicsConfiguration config = screens[i].getDefaultConfiguration();
			Rectangle bounds = config.getBounds();
			boolean isPrimary = screens[i] == defaultScreen;

			System.out.printf("  Monitor %d: %dx%d at (%d, %d) %s%n",
				i,
				bounds.width, bounds.height,
				bounds.x, bounds.y,
				isPrimary ? "[PRIMARY]" : ""
			);
		}

		// Show virtual screen bounds
		Rectangle virtualBounds = getVirtualScreenBounds();
		System.out.printf("\nVirtual Screen: %dx%d at (%d, %d)%n",
			virtualBounds.width, virtualBounds.height,
			virtualBounds.x, virtualBounds.y
		);
		System.out.println();
	}

	private static void testCaptureAllMonitors() throws Exception {
		System.out.println("Test 1: Capturing all monitors...");

		NativeController controller = NativeControllerFactory.get();
		long startTime = System.nanoTime();
		BufferedImage image = controller.captureDesktop();
		long captureTime = System.nanoTime() - startTime;

		if (image != null) {
			File output = new File("multi_monitor_capture.png");
			ImageIO.write(image, "png", output);

			System.out.printf("  ✓ Captured: %dx%d in %dms%n",
				image.getWidth(), image.getHeight(),
				captureTime / 1_000_000
			);
			System.out.println("  Saved to: " + output.getAbsolutePath());
		} else {
			System.out.println("  ✗ Capture failed");
		}
		System.out.println();
	}

	private static void testCaptureSpecificMonitor() throws Exception {
		System.out.println("Test 2: Capturing individual monitors...");

		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = ge.getScreenDevices();

		for (int i = 0; i < screens.length; i++) {
			GraphicsConfiguration config = screens[i].getDefaultConfiguration();
			Rectangle bounds = config.getBounds();

			long startTime = System.nanoTime();
			BufferedImage image = new Robot().createScreenCapture(bounds);
			long captureTime = System.nanoTime() - startTime;

			File output = new File("monitor_" + i + "_capture.png");
			ImageIO.write(image, "png", output);

			System.out.printf("  Monitor %d: %dx%d captured in %dms%n",
				i, image.getWidth(), image.getHeight(),
				captureTime / 1_000_000
			);
		}
		System.out.println();
	}

	private static void testOldVsNewCapture() throws Exception {
		System.out.println("Test 3: Comparing capture methods...");

		Rectangle primaryScreen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		long startOld = System.nanoTime();
		BufferedImage imageOld = new Robot().createScreenCapture(primaryScreen);
		long timeOld = System.nanoTime() - startOld;

		System.out.printf("  Old method (primary only): %dx%d in %dms%n",
			imageOld.getWidth(), imageOld.getHeight(),
			timeOld / 1_000_000
		);

		// New method (all monitors)
		Rectangle virtualBounds = getVirtualScreenBounds();
		long startNew = System.nanoTime();
		BufferedImage imageNew = new Robot().createScreenCapture(virtualBounds);
		long timeNew = System.nanoTime() - startNew;

		System.out.printf("  New method (all monitors): %dx%d in %dms%n",
			imageNew.getWidth(), imageNew.getHeight(),
			timeNew / 1_000_000
		);

		int pixelDifference = (imageNew.getWidth() * imageNew.getHeight()) -
			(imageOld.getWidth() * imageOld.getHeight());
		System.out.printf("  Additional pixels captured: %,d (%.1f%% more)%n",
			pixelDifference,
			(pixelDifference * 100.0) / (imageOld.getWidth() * imageOld.getHeight())
		);
	}

	private static Rectangle getVirtualScreenBounds() {
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