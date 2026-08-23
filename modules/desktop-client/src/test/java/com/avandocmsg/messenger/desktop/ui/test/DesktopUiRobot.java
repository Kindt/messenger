package com.avandocmsg.messenger.desktop.ui.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import com.avandocmsg.messenger.desktop.ui.ChatMessagePane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

public final class DesktopUiRobot {

    private DesktopUiRobot() {}

    public static void waitForFx() {
        WaitForAsyncUtils.waitForFxEvents();
    }

    public static void waitForTextContains(FxRobot robot, String fxId, String substring, long timeoutMs) {
        try {
            WaitForAsyncUtils.waitFor(timeoutMs, TimeUnit.MILLISECONDS, () -> {
                waitForFx();
                try {
                    var node = robot.lookup("#" + fxId).query();
                    if (node instanceof ChatMessagePane pane) {
                        return pane.getText().contains(substring);
                    }
                    if (node instanceof TextArea area) {
                        return area.getText().contains(substring);
                    }
                    if (node instanceof Label label) {
                        return label.getText().contains(substring);
                    }
                    if (node instanceof TextInputControl input) {
                        return input.getText().contains(substring);
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (java.util.concurrent.TimeoutException e) {
            // fall through to assertion with current text
        }
        var node = robot.lookup("#" + fxId).query();
        var text = switch (node) {
            case ChatMessagePane pane -> pane.getText();
            case TextArea area -> area.getText();
            case Label label -> label.getText();
            case TextInputControl input -> input.getText();
            default -> "";
        };
        assertTrue(text.contains(substring), "expected '" + substring + "' in: " + text);
    }

    public static void clickWhenReady(FxRobot robot, String fxId) {
        waitForFx();
        robot.clickOn("#" + fxId);
        waitForFx();
    }

    public static void waitForNode(FxRobot robot, String fxId, long timeoutMs) {
        try {
            WaitForAsyncUtils.waitFor(timeoutMs, TimeUnit.MILLISECONDS, () -> {
                waitForFx();
                return !robot.lookup("#" + fxId).queryAll().isEmpty();
            });
        } catch (java.util.concurrent.TimeoutException e) {
            assertTrue(false, "node #" + fxId + " not found within " + timeoutMs + "ms");
        }
    }
}
