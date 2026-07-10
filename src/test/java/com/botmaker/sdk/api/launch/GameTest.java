package com.botmaker.sdk.api.launch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract tests for {@link Game}'s argument validation. These deliberately do not exercise a real launch
 * (that would spawn Steam / a process on the test host) — they only pin the reject-empty-input behavior that
 * guards both {@code launch} and {@code launchSteam} (including the numeric {@code launchSteam(int)} overload,
 * which delegates to the String form).
 */
class GameTest {

    @Test
    void launchRejectsEmptyExecutable() {
        assertThrows(IllegalArgumentException.class, () -> Game.launch(null));
        assertThrows(IllegalArgumentException.class, () -> Game.launch("  "));
    }

    @Test
    void launchSteamRejectsEmptyAppId() {
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam((String) null));
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam(""));
        assertThrows(IllegalArgumentException.class, () -> Game.launchSteam("   "));
    }
}
