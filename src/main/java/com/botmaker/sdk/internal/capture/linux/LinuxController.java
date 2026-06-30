package com.botmaker.sdk.internal.capture.linux;

import com.botmaker.sdk.internal.capture.core.GenericWindow;
import com.botmaker.sdk.internal.capture.core.NativeController;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Linux implementation of NativeController using X11
 * Uses Robot for screen capture and XTest for mouse clicks
 *
 * Note: This class manages native X11 resources. While it implements AutoCloseable,
 * the display connection is typically kept open for the lifetime of the application.
 * Call close() explicitly if you need to release resources before application exit.
 */
public class LinuxController implements NativeController, AutoCloseable {

	private final Pointer display;
	private final boolean x11Available;
	private volatile boolean closed = false;

	public LinuxController() {
		// Try to open X11 display
		Pointer disp = null;
		boolean available = false;

		try {
			disp = X11.INSTANCE.XOpenDisplay(null);
			available = (disp != null);

			if (!available) {
				System.err.println("[Linux] Warning: Could not open X11 display. Falling back to Robot for all operations.");
				System.err.println("[Linux] Make sure DISPLAY environment variable is set and X11 is running.");
			}
		} catch (UnsatisfiedLinkError e) {
			System.err.println("[Linux] Warning: X11 libraries not found. Install libx11-6 and libxtst-6.");
			System.err.println("[Linux] Falling back to Robot for all operations.");
		} catch (Exception e) {
			System.err.println("[Linux] Warning: Error initializing X11: " + e.getMessage());
		}

		this.display = disp;
		this.x11Available = available;
	}

	@Override
	public GenericWindow getForegroundWindow() {
		checkNotClosed();

		if (!x11Available) {
			System.out.println("[Linux] X11 not available, returning mock window.");
			return new GenericWindow(-1, "Mock Linux Window", new Rectangle(0, 0, 800, 600));
		}

		try {
			// Try to get active window via _NET_ACTIVE_WINDOW (EWMH)
			Pointer activeWindow = X11Utils.getActiveWindow(display);

			if (activeWindow == null || Pointer.nativeValue(activeWindow) == 0) {
				// Fallback to XGetInputFocus
				PointerByReference focusReturn = new PointerByReference();
				IntByReference revertToReturn = new IntByReference();
				X11.INSTANCE.XGetInputFocus(display, focusReturn, revertToReturn);
				activeWindow = focusReturn.getValue();
			}

			if (activeWindow == null || Pointer.nativeValue(activeWindow) == 0 || Pointer.nativeValue(activeWindow) == 1) {
				System.out.println("[Linux] No active window found.");
				return null;
			}

			return toGenericWindow(activeWindow);
		} catch (Exception e) {
			System.err.println("[Linux] Error getting foreground window: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public List<GenericWindow> getChildWindows(GenericWindow parent) {
		checkNotClosed();

		List<GenericWindow> result = new ArrayList<>();

		if (!x11Available || parent == null) {
			return result;
		}

		try {
			Pointer parentWindow = (Pointer) parent.getNativeHandle();

			PointerByReference rootReturn = new PointerByReference();
			PointerByReference parentReturn = new PointerByReference();
			PointerByReference childrenReturn = new PointerByReference();
			IntByReference nChildrenReturn = new IntByReference();

			int status = X11.INSTANCE.XQueryTree(
				display, parentWindow,
				rootReturn, parentReturn,
				childrenReturn, nChildrenReturn
			);

			if (status == 0) {
				return result;
			}

			Pointer children = childrenReturn.getValue();
			int nChildren = nChildrenReturn.getValue();

			if (children != null && nChildren > 0) {
				long[] childIds = children.getLongArray(0, nChildren);

				for (long childId : childIds) {
					Pointer childWindow = new Pointer(childId);

					// Only include viewable windows with titles
					if (X11Utils.isWindowViewable(display, childWindow)) {
						String title = X11Utils.getWindowTitle(display, childWindow);
						if (title != null && !title.isEmpty()) {
							GenericWindow gw = toGenericWindow(childWindow);
							if (gw != null) {
								result.add(gw);
							}
						}
					}
				}

				X11.INSTANCE.XFree(children);
			}
		} catch (Exception e) {
			System.err.println("[Linux] Error getting child windows: " + e.getMessage());
			e.printStackTrace();
		}

		return result;
	}

	@Override
	public List<GenericWindow> getAllWindows() {
		checkNotClosed();

		List<GenericWindow> result = new ArrayList<>();

		if (!x11Available) {
			return result;
		}

		try {
			// Get all client windows from window manager
			Pointer[] windows = X11Utils.getClientList(display);

			if (windows.length == 0) {
				// Fallback: enumerate from root window
				Pointer root = X11.INSTANCE.XDefaultRootWindow(display);
				windows = enumerateWindowsRecursive(root);
			}

			for (Pointer window : windows) {
				if (X11Utils.isWindowViewable(display, window) &&
					!X11Utils.hasOverrideRedirect(display, window)) {

					String title = X11Utils.getWindowTitle(display, window);
					if (title != null && !title.isEmpty()) {
						GenericWindow gw = toGenericWindow(window);
						if (gw != null) {
							result.add(gw);
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Linux] Error getting all windows: " + e.getMessage());
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Recursively enumerate windows (fallback method)
	 */
	private Pointer[] enumerateWindowsRecursive(Pointer window) {
		List<Pointer> allWindows = new ArrayList<>();

		try {
			PointerByReference rootReturn = new PointerByReference();
			PointerByReference parentReturn = new PointerByReference();
			PointerByReference childrenReturn = new PointerByReference();
			IntByReference nChildrenReturn = new IntByReference();

			int status = X11.INSTANCE.XQueryTree(
				display, window,
				rootReturn, parentReturn,
				childrenReturn, nChildrenReturn
			);

			if (status != 0) {
				Pointer children = childrenReturn.getValue();
				int nChildren = nChildrenReturn.getValue();

				if (children != null && nChildren > 0) {
					long[] childIds = children.getLongArray(0, nChildren);

					for (long childId : childIds) {
						Pointer childWindow = new Pointer(childId);
						allWindows.add(childWindow);

						// Recurse into children
						Pointer[] subWindows = enumerateWindowsRecursive(childWindow);
						for (Pointer sw : subWindows) {
							allWindows.add(sw);
						}
					}

					X11.INSTANCE.XFree(children);
				}
			}
		} catch (Exception e) {
			// Ignore errors during recursive enumeration
		}

		return allWindows.toArray(new Pointer[0]);
	}

	/**
	 * Capture window using Robot (simple and reliable)
	 */
	@Override
	public BufferedImage captureWindow(GenericWindow window) {
		checkNotClosed();

		if (!x11Available || window == null) {
			System.out.println("[Linux] Falling back to Robot desktop capture.");
			return captureDesktop();
		}

		try {
			Pointer x11Window = (Pointer) window.getNativeHandle();

			// Get window geometry
			Rectangle rect = X11Utils.getWindowGeometry(display, x11Window);
			if (rect == null || rect.width <= 0 || rect.height <= 0) {
				System.err.println("[Linux] Invalid window geometry, falling back to desktop capture.");
				return captureDesktop();
			}

			// Simple and reliable - use Robot for screen capture
			return new Robot().createScreenCapture(rect);

		} catch (Exception e) {
			System.err.println("[Linux] Error capturing window: " + e.getMessage());
			e.printStackTrace();
			return captureDesktop();
		}
	}

	/**
	 * Capture all monitors as a single image
	 * This captures the virtual screen bounds that encompasses all monitors
	 */
	@Override
	public BufferedImage captureDesktop() {
		try {
			// Get the bounding rectangle of all monitors
			Rectangle virtualBounds = getVirtualScreenBounds();
			return new Robot().createScreenCapture(virtualBounds);
		} catch (AWTException e) {
			System.err.println("[Linux] Desktop capture failed: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get the virtual screen bounds that encompasses all monitors
	 */
	private Rectangle getVirtualScreenBounds() {
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
	 * Post click using window-relative coordinates
	 * Converts to screen coordinates and uses XTest
	 */
	@Override
	public void postLeftClick(GenericWindow window, int relativeX, int relativeY) {
		checkNotClosed();

		if (!x11Available) {
			System.out.println("[Linux] X11 not available, cannot post click.");
			return;
		}

		try {
			Pointer x11Window = (Pointer) window.getNativeHandle();

			// Get window geometry to convert to screen coordinates
			Rectangle rect = X11Utils.getWindowGeometry(display, x11Window);
			if (rect == null) {
				System.err.println("[Linux] Could not get window geometry for click.");
				return;
			}

			// Convert to absolute screen coordinates
			int screenX = rect.x + relativeX;
			int screenY = rect.y + relativeY;

			// Use XTest to simulate click
			postLeftClickScreen(screenX, screenY);
		} catch (Exception e) {
			System.err.println("[Linux] Error posting click: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Post click using absolute screen coordinates
	 * Uses XTest to avoid moving the visible cursor (like Windows PostMessage)
	 */
	@Override
	public void postLeftClickScreen(int xAbs, int yAbs) {
		checkNotClosed();

		if (!x11Available) {
			System.out.println("[Linux] X11 not available, attempting Robot click.");
			try {
				Robot robot = new Robot();
				Point current = MouseInfo.getPointerInfo().getLocation();
				robot.mouseMove(xAbs, yAbs);
				Thread.sleep(10);
				robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
				robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
				Thread.sleep(10);
				robot.mouseMove(current.x, current.y);
			} catch (Exception e) {
				System.err.println("[Linux] Robot click failed: " + e.getMessage());
			}
			return;
		}

		try {
			// Move mouse to position (fake motion - doesn't move visible cursor)
			XTest.INSTANCE.XTestFakeMotionEvent(display, -1, xAbs, yAbs, 0);
			X11.INSTANCE.XFlush(display);

			// Small delay to ensure motion is processed
			Thread.sleep(10);

			// Press left button
			XTest.INSTANCE.XTestFakeButtonEvent(display, XTest.Button1, true, 0);
			X11.INSTANCE.XFlush(display);

			// Small delay between press and release
			Thread.sleep(10);

			// Release left button
			XTest.INSTANCE.XTestFakeButtonEvent(display, XTest.Button1, false, 0);
			X11.INSTANCE.XFlush(display);

			System.out.println("[Linux] Click sent to (" + xAbs + ", " + yAbs + ")");
		} catch (Exception e) {
			System.err.println("[Linux] Error posting screen click: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Convert X11 window to GenericWindow
	 */
	private GenericWindow toGenericWindow(Pointer window) {
		if (window == null || Pointer.nativeValue(window) == 0) {
			return null;
		}

		try {
			String title = X11Utils.getWindowTitle(display, window);
			Rectangle rect = X11Utils.getWindowGeometry(display, window);

			if (rect == null) {
				rect = new Rectangle(0, 0, 0, 0);
			}

			return new GenericWindow(window, title != null ? title : "", rect);
		} catch (Exception e) {
			System.err.println("[Linux] Error converting to GenericWindow: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Cleanup X11 resources
	 * This method is safe to call multiple times.
	 */
	@Override
	public void close() {
		if (!closed && x11Available && display != null) {
			synchronized (this) {
				if (!closed) {
					try {
						X11.INSTANCE.XCloseDisplay(display);
					} catch (Exception e) {
						System.err.println("[Linux] Error closing X11 display: " + e.getMessage());
					} finally {
						closed = true;
					}
				}
			}
		}
	}

	/**
	 * Check if resources have been closed
	 */
	private void checkNotClosed() {
		if (closed) {
			throw new IllegalStateException("LinuxController has been closed and cannot be used");
		}
	}
}