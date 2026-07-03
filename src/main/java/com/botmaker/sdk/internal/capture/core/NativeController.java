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

	// --- Window management ---
	void focusWindow(GenericWindow window);
	void moveWindow(GenericWindow window, int x, int y);
	void resizeWindow(GenericWindow window, int width, int height);

	// --- Input synthesis ---
	// keyDown/keyUp take a per-OS native key code (X keysym on Linux, virtual-key code on Windows);
	// callers resolve it from api.interaction.Key so the public API stays platform-neutral.
	void keyDown(int nativeKeyCode);
	void keyUp(int nativeKeyCode);
	void typeText(String text);
	void mouseMove(int xAbs, int yAbs);
	void mouseButton(int button, boolean press); // 1=left, 2=middle, 3=right
	void scroll(int amount);                      // + = up/away, - = down/toward
}