package com.botmaker.sdk.internal.capture.windows;

import com.botmaker.sdk.internal.capture.Clicker;
import com.botmaker.sdk.internal.capture.ScreenCapture;
import com.botmaker.sdk.internal.capture.User32;
import com.botmaker.sdk.internal.capture.WindowFinder;
import com.botmaker.sdk.internal.capture.core.GenericWindow;
import com.botmaker.sdk.internal.capture.core.NativeController;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

public class WindowsController implements NativeController {

	@Override
	public GenericWindow getForegroundWindow() {
		HWND hwnd = User32.INSTANCE.GetForegroundWindow();
		return toGenericWindow(hwnd);
	}

	@Override
	public List<GenericWindow> getChildWindows(GenericWindow parent) {
		HWND parentHwnd = (HWND) parent.getNativeHandle();
		return WindowFinder.getChildWindows(parentHwnd).stream()
			.map(info -> toGenericWindow(info.getHWnd()))
			.collect(Collectors.toList());
	}

	@Override
	public List<GenericWindow> getAllWindows() {
		return WindowFinder.getAllWindows().stream()
			.map(info -> toGenericWindow(info.getHWnd()))
			.collect(Collectors.toList());
	}

	@Override
	public BufferedImage captureWindow(GenericWindow window) {
		return ScreenCapture.capture((HWND) window.getNativeHandle());
	}

	@Override
	public BufferedImage captureDesktop() {
		return ScreenCapture.captureDesktop();
	}

	@Override
	public void postLeftClick(GenericWindow window, int relativeX, int relativeY) {
		Clicker.postLeftClick((HWND) window.getNativeHandle(), relativeX, relativeY);
	}

	@Override
	public void postLeftClickScreen(int xAbs, int yAbs) {
		Clicker.postLeftClickScreen(xAbs, yAbs);
	}

	// --- Helper to convert Windows HWND to GenericWindow ---
	private GenericWindow toGenericWindow(HWND hwnd) {
		if (hwnd == null) return null;

		byte[] windowText = new byte[512];
		User32.INSTANCE.GetWindowTextA(hwnd.getPointer(), windowText, 512);
		String title = new String(windowText).trim();

		RECT winRect = new RECT();
		User32.INSTANCE.GetWindowRect(hwnd.getPointer(), winRect);
		Rectangle rect = new Rectangle(winRect.left, winRect.top,
			winRect.right - winRect.left,
			winRect.bottom - winRect.top);

		return new GenericWindow(hwnd, title, rect);
	}
}