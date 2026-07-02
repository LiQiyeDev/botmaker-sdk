package com.botmaker.sdk.api.launch;

import com.botmaker.sdk.internal.launch.UriLauncher;

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
}
