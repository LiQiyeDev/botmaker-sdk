package com.botmaker.sdk.internal.interaction;


import com.botmaker.sdk.internal.capture.Clicker;
import com.botmaker.sdk.internal.capture.ScreenCapture;
import com.botmaker.sdk.internal.capture.WindowInfo;
import com.botmaker.sdk.internal.emulator.Emulator;
import com.botmaker.sdk.internal.opencv.InternalMatch;
import com.botmaker.sdk.internal.opencv.MatType;
import com.botmaker.sdk.internal.opencv.OpencvManager;
import com.botmaker.sdk.internal.opencv.Template;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Logger;

public class GameInteractor{

    private static final Logger LOGGER = Logger.getLogger(GameInteractor.class.getName());

    private final GameType gameType;
    private final Emulator emulator;
    private final WindowInfo gameWindow;

    public GameInteractor(GameType gameType, Emulator emulator, WindowInfo gameWindow) {
        this.gameType = gameType;
        this.emulator = emulator;
        this.gameWindow = gameWindow;
    }

    public Template getBackground() {
        try {
            BufferedImage screenshot = null;
            if (gameType == GameType.EMULATOR && emulator != null) {
                File tempFile = File.createTempFile("screenshot", ".png");
                tempFile.deleteOnExit(); // Ensure cleanup even if the program crashes later
                emulator.takeScreenshot(tempFile.toPath());
                screenshot = javax.imageio.ImageIO.read(tempFile);
                tempFile.delete(); // Clean up the temporary file immediately
            } else if (gameType == GameType.WINDOW && gameWindow != null) {
                screenshot = ScreenCapture.capture(gameWindow.getHWnd());
            } else if (gameType == GameType.SCREEN) {
                screenshot = ScreenCapture.captureDesktop();
            }

            if (screenshot != null) {
                return new Template(OpencvManager.bufferedImageToMat(screenshot), "screenshot");
            }
            return null;
        } catch (Exception e) {
            LOGGER.severe("Failed to get background screenshot: " + e.getMessage());
            e.printStackTrace(); // Also print stack trace for easier debugging
            return null; // Return null on any exception
        }
    }


    public InternalMatch findTemplateInGame(Template template, MatType matType, double confidenceThreshold) throws Exception {
        Template background = getBackground();
        if (background != null) {
            return OpencvManager.findBestMatch(template, background, matType, confidenceThreshold);
        }
        return InternalMatch.noMatch();
    }


    public void click(int x, int y) throws Exception {
        LOGGER.info("Clicking at: (" + x + ", " + y + ")");
        if (gameType == GameType.EMULATOR && emulator != null) {
            if (emulator.getDevice() == null) {
                LOGGER.severe("Emulator device not connected. Cannot perform click.");
                return;
            }
            emulator.getDevice().executeShellCommand("input tap " + x + " " + y, null);
        } else if (gameType == GameType.WINDOW && gameWindow != null) {
            Clicker.postLeftClick(gameWindow.getHWnd(), x, y);
        } else if (gameType == GameType.SCREEN) {
            Clicker.postLeftClickScreen(x, y);
        } else {
            LOGGER.warning("Cannot perform click. Game type not supported or game not initialized.");
        }
    }
}
