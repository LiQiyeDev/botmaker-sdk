package com.botmaker.sdk.internal.capture;

import com.botmaker.sdk.internal.capture.core.GenericWindow;
import com.botmaker.sdk.internal.capture.core.NativeController;
import com.botmaker.sdk.internal.capture.core.NativeControllerFactory;
import com.botmaker.sdk.internal.opencv.*;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

// Make sure these utility methods are accessible or moved to a shared utility class
import static com.botmaker.sdk.internal.capture.ScreenCapture.matToBufferedImage;

import static com.botmaker.sdk.internal.opencv.OpencvManager.bufferedImageToMat;

public class CaptureTest {

	// Fetch the correct controller for the current OS (Windows or Linux)
	private static final NativeController osController = NativeControllerFactory.get();

	public static void main(String[] args){
		try {
			testLiveCapture();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void testLiveCapture() throws InterruptedException {
		// DPI awareness is a Windows-specific concept.
		// If you need it on Windows, it should be moved inside the WindowsController constructor.

		System.out.println("You have 5 seconds to bring the window you want to capture to the foreground...");
		Thread.sleep(5000);

		// Cross-platform window fetch
		GenericWindow selectedWindow = osController.getForegroundWindow();

		if (selectedWindow == null) {
			System.out.println("Could not get the foreground window.");
			return;
		}

		System.out.println("Capturing window: " + selectedWindow.getTitle());

		BufferedImage firstCapture = osController.captureWindow(selectedWindow);
		if (firstCapture != null) {
			try {
				ImageIO.write(firstCapture, "png", new File("capture.png"));
				System.out.println("Saved the first capture to capture.png");
			} catch (IOException e) {
				e.printStackTrace();
			}

			ImageDisplay display = new ImageDisplay();
			display.showImage(firstCapture);

			while (true) {
				BufferedImage screenshot = osController.captureWindow(selectedWindow);
				display.showImage(screenshot);
				Thread.sleep(100); // Capture roughly 10 times per second
			}
		}
	}

	public static void testCreateTemplateAndFind() throws IOException, InterruptedException {
		System.out.println("Press ENTER in the console to capture the first corner of the rectangle.");
		waitForKeyPress();
		Point p1 = getMousePosition();
		System.out.println("First corner captured at: " + p1);

		System.out.println("Press ENTER again to capture the second corner.");
		waitForKeyPress();
		Point p2 = getMousePosition();
		System.out.println("Second corner captured at: " + p2);

		Rectangle rect = new Rectangle(p1);
		rect.add(p2);

		BufferedImage desktop = osController.captureDesktop();
		if (desktop == null) {
			System.err.println("Failed to capture desktop.");
			return;
		}

		BufferedImage templateImage = desktop.getSubimage(rect.x, rect.y, rect.width, rect.height);

		Template backgroundTemplate = new Template(bufferedImageToMat(desktop), "background");
		Template template = new Template(bufferedImageToMat(templateImage), "template");

		InternalMatch result = OpencvManager.findBestMatch(template, backgroundTemplate, MatType.COLOR);

		if (result != null) {
			System.out.println("Match found at: " + result.rectLocation);
			System.out.println("Confidence: " + result.getScore());

			Mat drawnImage = OpencvManager.drawMatch(backgroundTemplate.mat, result, new Scalar(0, 255, 0));
			BufferedImage resultImage = matToBufferedImage(drawnImage);

			ImageDisplay display = new ImageDisplay();
			display.showImage(resultImage);
		} else {
			System.out.println("No match found.");
		}
	}

	private static void waitForKeyPress() {
		// Global keyhooks (GetAsyncKeyState) are extremely OS-dependent.
		// For a cross-platform test CLI, waiting for the user to press Enter is much safer.
		new Scanner(System.in).nextLine();
	}

	private static Point getMousePosition() {
		// Java standard library provides a cross-platform way to get the mouse position!
		return MouseInfo.getPointerInfo().getLocation();
	}

	public static void testCaptureChildWindow() throws InterruptedException {
		System.out.println("You have 5 seconds to bring the window you want to capture to the foreground...");
		Thread.sleep(5000);

		GenericWindow foregroundWindow = osController.getForegroundWindow();
		List<GenericWindow> childWindows = osController.getChildWindows(foregroundWindow);

		System.out.println("Available windows to capture:");
		System.out.println("0: " + foregroundWindow.getTitle() + " (Parent)");
		for (int i = 0; i < childWindows.size(); i++) {
			System.out.println((i + 1) + ": " + childWindows.get(i).getTitle());
		}

		System.out.print("Enter the number of the window to capture: ");
		Scanner scanner = new Scanner(System.in);
		int choice = scanner.nextInt();

		GenericWindow selectedWindow;
		if (choice == 0) {
			selectedWindow = foregroundWindow;
		} else if (choice > 0 && choice <= childWindows.size()) {
			selectedWindow = childWindows.get(choice - 1);
		} else {
			System.out.println("Invalid choice.");
			return;
		}

		BufferedImage image = osController.captureWindow(selectedWindow);
		if (image != null) {
			try {
				ImageIO.write(image, "png", new File("capture.png"));
				System.out.println("Saved the first capture to capture.png");
			} catch (IOException e) {
				e.printStackTrace();
			}

			ImageDisplay display = new ImageDisplay();
			while (true) {
				BufferedImage screenshot = osController.captureWindow(selectedWindow);
				display.showImage(screenshot);
			}
		}
	}

	public static void testPostLeftClick() throws InterruptedException {
		System.out.println("You have 5 seconds to bring the window you want to click on to the foreground...");
		Thread.sleep(5000);

		GenericWindow selectedWindow = osController.getForegroundWindow();

		if (selectedWindow == null) {
			System.out.println("Could not get the foreground window.");
			return;
		}

		System.out.println("Move your mouse to the desired click location, then press ENTER in the console.");
		waitForKeyPress();
		Point mousePos = getMousePosition();

		Rectangle windowRect = selectedWindow.getRect();

		int relativeX = mousePos.x - windowRect.x;
		int relativeY = mousePos.y - windowRect.y;

		System.out.println("Clicking on window: " + selectedWindow.getTitle() + " at relative coordinates (" + relativeX + ", " + relativeY + ") in 3 seconds...");
		Thread.sleep(3000);
		osController.postLeftClick(selectedWindow, relativeX, relativeY);
		System.out.println("Clicked!");
	}

	public static void testClickOnVirtualScreen() {
		System.out.println("Place le curseur, puis appuie sur ENTER dans la console");
		waitForKeyPress();
		Point p = getMousePosition();
		osController.postLeftClickScreen(p.x, p.y);
		System.out.println("Click envoyé à ("+p.x+", "+p.y+")");
	}
}