package com.botmaker.sdk.internal.capture.core;

import com.botmaker.sdk.internal.capture.linux.LinuxController;
import com.botmaker.sdk.internal.capture.windows.WindowsController;
import com.sun.jna.Platform;

public class NativeControllerFactory {

	private static NativeController instance;

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