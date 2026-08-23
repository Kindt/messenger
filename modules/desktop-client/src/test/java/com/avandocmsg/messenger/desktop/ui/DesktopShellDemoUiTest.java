package com.avandocmsg.messenger.desktop.ui;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

import com.avandocmsg.messenger.desktop.DesktopApplication;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiRobot;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiTestSupport;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

@Tag("desktop-ui")
@ExtendWith(ApplicationExtension.class)
class DesktopShellDemoUiTest {

    @Start
    void start(Stage stage) {
        DesktopUiTestSupport.autostartDemoShell();
        new DesktopApplication().start(stage);
        stage.setWidth(1280);
        stage.setHeight(800);
        DesktopUiRobot.waitForFx();
    }

    @Test
    void demoShellShowsInboxAndWelcomeMessage(FxRobot robot) {
        verifyThat("#" + DesktopUiIds.SHELL_HEADER, isVisible());
        verifyThat("#" + DesktopUiIds.MESSAGES, isVisible());
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.MESSAGES, "Welcome", 8000);
    }

    @Test
    void sendMessageViaComposer(FxRobot robot) {
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.COMPOSER);
        robot.write("UI test message @all");
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SEND);
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.MESSAGES, "UI test message @all", 8000);
    }

    @Test
    void threadRepliesLoadWhenThreadIdSet(FxRobot robot) {
        robot.interact(() -> {
            javafx.scene.control.TitledPane pane = robot.lookup("#" + DesktopUiIds.THREAD_TOGGLE).query();
            pane.setExpanded(true);
        });
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.THREAD_ID);
        robot.write("m2");
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.THREAD_MESSAGES, "Reply in thread", 8000);
    }

    @Test
    void searchTabFindsDemoContent(FxRobot robot) {
        robot.clickOn("Поиск");
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SEARCH_FIELD);
        robot.write("deploy");
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SEARCH_BTN);
        verifyThat("#" + DesktopUiIds.SEARCH_RESULTS, isVisible());
    }

    @Test
    void settingsTabsClickThrough(FxRobot robot) {
        robot.clickOn("Настройки");
        robot.clickOn("Профиль");
        robot.clickOn("Уведомления");
        verifyThat("#" + DesktopUiIds.SETTINGS_SOUND, isVisible());
        robot.clickOn("Ссылки и файлы");
        verifyThat("#" + DesktopUiIds.SETTINGS_ATTACH_PATH, isVisible());
        robot.clickOn("Безопасность");
        robot.clickOn("Общие");
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SETTINGS_UPDATE_CHECK);
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.SETTINGS_UPDATE_STATUS, "0.0.2", 8000);
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SETTINGS_SAVE);
    }

    @Test
    void serversTabListsDemoServers(FxRobot robot) {
        robot.clickOn("Серверы");
        verifyThat("#" + DesktopUiIds.SERVERS_LIST, isVisible());
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.SERVERS_REFRESH);
    }

    @Test
    void demoCallButtonAppendsConferenceLine(FxRobot robot) {
        DesktopUiRobot.waitForFx();
        var call = robot.lookup("#" + DesktopUiIds.CALL_BTN).queryAs(Button.class);
        robot.interact(call::fire);
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.MESSAGES, "demo://conference", 8000);
    }

    @Test
    void attachFileCopiesToDownloadsPath(FxRobot robot) throws Exception {
        var attach = DesktopUiTestSupport.dataDir().resolve("sample.txt");
        java.nio.file.Files.writeString(attach, "attach-body");
        DesktopUiTestSupport.setTestAttachFile(attach);
        DesktopUiRobot.clickWhenReady(robot, DesktopUiIds.ATTACH);
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.MESSAGES, "[attach]", 8000);
        DesktopUiTestSupport.clearTestAttachFile();
    }

    @Test
    void logoutReturnsToProfilePicker(FxRobot robot) {
        robot.interact(() -> {
            var btn = (Button) robot.lookup("#" + DesktopUiIds.SHELL_LOGOUT).query();
            btn.fire();
        });
        DesktopUiRobot.waitForNode(robot, DesktopUiIds.PROFILE_DEMO, 8000);
        verifyThat("#" + DesktopUiIds.PROFILE_DEMO, isVisible());
    }
}
