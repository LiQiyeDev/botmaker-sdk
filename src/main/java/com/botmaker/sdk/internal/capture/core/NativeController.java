package com.botmaker.sdk.internal.capture.core;

import java.awt.image.BufferedImage;
import java.util.List;

public interface NativeController {
	GenericWindow getForegroundWindow();
	List<GenericWindow> getChildWindows(GenericWindow parent);
	List<GenericWindow> getAllWindows();

	BufferedImage captureWindow(GenericWindow window);
	BufferedImage captureDesktop();

	void postLeftClick(GenericWindow window, int relativeX, int relativeY);
	void postLeftClickScreen(int xAbs, int yAbs);
}