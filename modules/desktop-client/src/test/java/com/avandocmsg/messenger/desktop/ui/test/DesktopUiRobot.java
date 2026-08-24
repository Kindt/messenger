package com.avandocmsg.messenger.desktop.ui.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.avandocmsg.messenger.desktop.ui.ChatMessagePane;
import com.avandocmsg.messenger.desktop.ui.DesktopUiIds;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/** TestFX helpers — programmatic focus/events, no screen-coordinate mouse moves. */
public final class DesktopUiRobot {

    private DesktopUiRobot() {}

    public static void waitForFx() {
        WaitForAsyncUtils.waitForFxEvents();
    }

    public static void focusMainWindow(FxRobot robot) {
        robot.interact(() -> {
            Stage stage = (Stage) robot.listWindows().getFirst();
            stage.toFront();
            stage.requestFocus();
        });
        waitForFx();
    }

    public static void selectShellTab(FxRobot robot, String tabId) {
        selectTab(robot, DesktopUiIds.SHELL_TABS, tabId);
    }

    public static void selectSettingsTab(FxRobot robot, String tabId) {
        selectTab(robot, DesktopUiIds.SETTINGS_TABS, tabId);
    }

    public static void selectTab(FxRobot robot, String tabPaneId, String tabId) {
        robot.interact(() -> {
            TabPane pane = robot.lookup("#" + tabPaneId).query();
            for (Tab tab : pane.getTabs()) {
                if (tabId.equals(tab.getId())) {
                    pane.getSelectionModel().select(tab);
                    return;
                }
            }
            throw new IllegalStateException("tab not found: " + tabId + " in #" + tabPaneId);
        });
        waitForFx();
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

    /** Focus control or fire button — does not move the OS mouse cursor. */
    public static void clickWhenReady(FxRobot robot, String fxId) {
        focusMainWindow(robot);
        robot.interact(() -> {
            var node = robot.lookup("#" + fxId).query();
            if (node instanceof Button button) {
                button.fire();
                return;
            }
            if (node instanceof TextInputControl input) {
                input.requestFocus();
                return;
            }
            node.requestFocus();
        });
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
