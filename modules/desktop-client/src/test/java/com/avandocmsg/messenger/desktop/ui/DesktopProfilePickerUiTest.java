package com.avandocmsg.messenger.desktop.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static com.avandocmsg.messenger.desktop.ui.test.DesktopUiRobot.waitForFx;

import com.avandocmsg.messenger.desktop.DesktopApplication;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiRobot;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiTestSupport;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

@Tag("desktop-ui")
@ExtendWith(ApplicationExtension.class)
class DesktopProfilePickerUiTest {

    @Start
    void start(Stage stage) {
        DesktopUiTestSupport.autostartProfilePicker();
        new DesktopApplication().start(stage);
        stage.setWidth(640);
        stage.setHeight(520);
        DesktopUiRobot.waitForFx();
    }

    @Test
    void profilePickerShowsDemoButton(FxRobot robot) {
        verifyThat("#" + DesktopUiIds.PROFILE_DEMO, isVisible());
    }

    @Test
    void clickDemoOpensMainShell(FxRobot robot) {
        waitForFx();
        var demo = robot.lookup("#" + DesktopUiIds.PROFILE_DEMO).queryButton();
        javafx.application.Platform.runLater(demo::fire);
        waitForFx();
        DesktopUiRobot.waitForNode(robot, DesktopUiIds.SHELL_HEADER, 15000);
        verifyThat("#" + DesktopUiIds.SHELL_HEADER, isVisible());
        verifyThat("#" + DesktopUiIds.INBOX_LIST, isVisible());
    }
}
