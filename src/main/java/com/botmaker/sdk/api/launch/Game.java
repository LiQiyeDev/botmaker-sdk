package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.api.interaction.Wait;
import com.botmaker.sdk.internal.launch.UriLauncher;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeControllerFactory;

/**
 * Launches a game so a bot can automate it.
 *
 * <p>Two entry points, both exposed as visual blocks:
 * <ul>
 *   <li>{@link #launch(String, String...)} — run any executable/command directly.</li>
 *   <li>{@link #launchSteam(String)} — hand a Steam appId to the local Steam client.</li>
 * </ul>
 *
 * <p>Launching a Steam game requires the Steam client to be installed and signed in (the client owns that
 * session; this SDK never touches Steam credentials). If Steam is not running, invoking the launch starts it
 * and Steam prompts the user to sign in through its own UI.
 */
public class Game {

    private Game() {}

    /**
     * Starts an executable, optionally with arguments. The process is detached — its input/output is not tied
     * to the bot.
     *
     * @param executablePath path to the program to run (absolute, or resolvable on {@code PATH})
     * @param args           optional command-line arguments
     * @throws IllegalArgumentException if {@code executablePath} is null/blank
     * @throws RuntimeException         if the process could not be started
     */
    public static void launch(String executablePath, String... args) {
        if (executablePath == null || executablePath.isBlank()) {
            throw new IllegalArgumentException("executablePath must not be empty");
        }
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(executablePath);
        if (args != null) {
            for (String arg : args) {
                if (arg != null) command.add(arg);
            }
        }
        try {
            new ProcessBuilder(command).start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch '" + executablePath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Launches a Steam game by its appId (the number in the game's Steam store URL / SteamDB). Opens the
     * cross-platform {@code steam://rungameid/<appId>} URL and falls back to {@code steam -applaunch <appId>}.
     *
     * @param appId the Steam application id, e.g. {@code "570"}
     * @throws IllegalArgumentException if {@code appId} is null/blank
     * @throws RuntimeException         if neither the Steam URL nor the CLI fallback could be invoked
     */
    public static void launchSteam(String appId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId must not be empty");
        }
        String id = appId.trim();
        if (UriLauncher.open("steam://rungameid/" + id)) {
            return;
        }
        // Fallback: the Steam CLI (requires `steam` on PATH).
        try {
            new ProcessBuilder("steam", "-applaunch", id).start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch Steam game '" + id
                    + "'. Is Steam installed? " + e.getMessage(), e);
        }
    }

    /** Convenience overload accepting a numeric appId. */
    public static void launchSteam(int appId) {
        launchSteam(Integer.toString(appId));
    }

    // --- Running-detection & wait (window-title based) ---

    /**
     * Whether a window whose title contains {@code windowTitle} (case-insensitive) is currently open.
     * A cheap way to tell if a game is already running before deciding to launch it.
     *
     * @param windowTitle a substring of the game window's title (e.g. {@code "Notepad"})
     * @return true if any open window's title contains {@code windowTitle}
     * @throws IllegalArgumentException if {@code windowTitle} is null/blank
     */
    public static boolean isRunning(String windowTitle) {
        if (windowTitle == null || windowTitle.isBlank()) {
            throw new IllegalArgumentException("windowTitle must not be empty");
        }
        String needle = windowTitle.trim().toLowerCase();
        try {
            for (GenericWindow win : NativeControllerFactory.get().getAllWindows()) {
                String title = win.getTitle();
                if (title != null && title.toLowerCase().contains(needle)) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            // Native enumeration failed (e.g. unsupported platform) — treat as "not detected".
            return false;
        }
        return false;
    }

    /**
     * Blocks until a window matching {@code windowTitle} appears, or {@code timeoutMillis} elapses.
     * Poll interval is ~250ms. Use after {@link #launch} to wait for a game to finish starting up.
     *
     * @param windowTitle  a substring of the game window's title
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @return true if the window appeared within the timeout, false if it timed out
     * @throws IllegalArgumentException if {@code windowTitle} is null/blank
     */
    public static boolean waitForLaunch(String windowTitle, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        while (true) {
            if (isRunning(windowTitle)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Wait.milliseconds(250);
        }
    }

    /**
     * Launches {@code executablePath} only if no window matching {@code windowTitle} is already open —
     * avoids spawning a duplicate instance of an already-running game.
     *
     * @param windowTitle    a substring of the game window's title used to detect an existing instance
     * @param executablePath path to the program to run (see {@link #launch})
     * @param args           optional command-line arguments
     * @return true if the game was launched, false if it was already running
     */
    public static boolean launchIfNotRunning(String windowTitle, String executablePath, String... args) {
        if (isRunning(windowTitle)) {
            return false;
        }
        launch(executablePath, args);
        return true;
    }

    /**
     * Launches a Steam game by appId only if no window matching {@code windowTitle} is already open.
     *
     * @param windowTitle a substring of the game window's title used to detect an existing instance
     * @param appId       the Steam application id (see {@link #launchSteam(String)})
     * @return true if the game was launched, false if it was already running
     */
    public static boolean launchSteamIfNotRunning(String windowTitle, String appId) {
        if (isRunning(windowTitle)) {
            return false;
        }
        launchSteam(appId);
        return true;
    }

    /**
     * Launches {@code executablePath} (unless already running) and then blocks until its window appears
     * or {@code timeoutMillis} elapses.
     *
     * @param windowTitle    a substring of the game window's title
     * @param timeoutMillis  the maximum time to wait for the window, in milliseconds
     * @param executablePath path to the program to run
     * @param args           optional command-line arguments
     * @return true if the game's window was present within the timeout, false if it timed out
     */
    public static boolean launchAndWait(String windowTitle, long timeoutMillis, String executablePath,
                                        String... args) {
        launchIfNotRunning(windowTitle, executablePath, args);
        return waitForLaunch(windowTitle, timeoutMillis);
    }
}
