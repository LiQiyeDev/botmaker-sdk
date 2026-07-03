package com.botmaker.sdk.internal.capture.core;

import com.botmaker.sdk.internal.capture.linux.LinuxController;
import com.botmaker.sdk.internal.capture.windows.WindowsController;
import com.sun.jna.Platform;

public class NativeControllerFactory {

	private static NativeController instance;

	/**
	 * Override the controller — used by tests to inject a fake so input/window operations can be
	 * asserted without a live X11/Windows session. Pass {@code null} to reset to auto-detection.
	 */
	public static void setForTesting(NativeController controller) {
		instance = controller;
	}

	public static NativeController get() {
		if (instance == null) {
			if (Platform.isWindows()) {
				instance = new WindowsController();
			} else if (Platform.isLinux()) {
				instance = new LinuxController();
			} else if (Platform.isMac()) {
				throw new UnsupportedOperationException("macOS is not yet supported.");
			} else {
				throw new UnsupportedOperationException("Unsupported Operating System.");
			}
		}
		return instance;
	}
}