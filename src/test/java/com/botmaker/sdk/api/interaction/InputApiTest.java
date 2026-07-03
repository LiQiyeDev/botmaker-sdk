package com.botmaker.sdk.api.interaction;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.internal.capture.core.NativeControllerFactory;
import com.botmaker.sdk.internal.capture.core.RecordingNativeController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InputApiTest {

    private RecordingNativeController fake;

    @BeforeEach
    void setUp() {
        fake = new RecordingNativeController();
        NativeControllerFactory.setForTesting(fake);
    }

    @AfterEach
    void tearDown() {
        NativeControllerFactory.setForTesting(null);
    }

    @Test
    void dragIssuesDownMoveUpInOrder() {
        Mouse.drag(new Point(0, 0), new Point(100, 100));
        assertEquals(List.of(
                "mouseMove(0,0)",
                "mouseButton(1,true)",
                "mouseMove(100,100)",
                "mouseButton(1,false)"
        ), fake.events);
    }

    @Test
    void rightClickMovesThenPressesRightButton() {
        Mouse.rightClick(new Point(7, 8));
        assertEquals(List.of(
                "mouseMove(7,8)",
                "mouseButton(3,true)",
                "mouseButton(3,false)"
        ), fake.events);
    }

    @Test
    void doubleClickIssuesTwoLeftPresses() {
        Mouse.doubleClick(new Point(1, 2));
        assertEquals(List.of(
                "mouseMove(1,2)",
                "mouseButton(1,true)", "mouseButton(1,false)",
                "mouseButton(1,true)", "mouseButton(1,false)"
        ), fake.events);
    }

    @Test
    void scrollDelegates() {
        Mouse.scroll(-3);
        assertEquals(List.of("scroll(-3)"), fake.events);
    }

    @Test
    void comboHoldsThenReleasesInReverse() {
        Keyboard.combo(Key.CTRL, Key.C);
        // hold CTRL, hold C, release C, release CTRL
        assertEquals(List.of(
                "keyDown(" + Key.CTRL.nativeCode() + ")",
                "keyDown(" + Key.C.nativeCode() + ")",
                "keyUp(" + Key.C.nativeCode() + ")",
                "keyUp(" + Key.CTRL.nativeCode() + ")"
        ), fake.events);
    }

    @Test
    void tapPressesThenReleases() {
        Keyboard.tap(Key.ENTER);
        assertEquals(List.of(
                "keyDown(" + Key.ENTER.nativeCode() + ")",
                "keyUp(" + Key.ENTER.nativeCode() + ")"
        ), fake.events);
    }

    @Test
    void typeDelegatesToController() {
        Keyboard.type("Hi");
        assertEquals(List.of("typeText(Hi)"), fake.events);
    }

    @Test
    void keyNativeCodeIsNonZeroForCommonKeys() {
        for (Key k : Key.values()) {
            assertNotEquals(0, k.nativeCode(), "Key " + k + " must map to a native code");
        }
    }
}
