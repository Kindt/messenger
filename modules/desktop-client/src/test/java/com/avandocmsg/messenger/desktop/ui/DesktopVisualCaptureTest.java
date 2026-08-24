package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.DesktopApplication;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiRobot;
import com.avandocmsg.messenger.desktop.ui.test.DesktopUiTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.TitledPane;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/** Captures desktop UI screenshots — FX-thread navigation only (no mouse). */
@Tag("desktop-visual-capture")
@ExtendWith(ApplicationExtension.class)
class DesktopVisualCaptureTest {

    @Start
    void start(Stage stage) {
        DesktopUiTestSupport.autostartDemoShell();
        new DesktopApplication().start(stage);
        stage.setX(80);
        stage.setY(40);
        stage.setWidth(1280);
        stage.setHeight(800);
        DesktopUiRobot.waitForFx();
    }

    @Test
    void captureDemoShellScreens(FxRobot robot) throws Exception {
        var out = outDir();
        Files.createDirectories(out);
        DesktopUiRobot.focusMainWindow(robot);
        DesktopUiRobot.waitForTextContains(robot, DesktopUiIds.MESSAGES, "Welcome", 12_000);
        capture(stage(robot), out.resolve("01-chats-main.png"));

        DesktopUiRobot.selectShellTab(robot, DesktopUiIds.TAB_SEARCH);
        capture(stage(robot), out.resolve("02-search-tab.png"));

        DesktopUiRobot.selectShellTab(robot, DesktopUiIds.TAB_SETTINGS);
        capture(stage(robot), out.resolve("03-settings-general.png"));
        for (var tabId : new String[] {
            DesktopUiIds.SETTINGS_PROFILE,
            DesktopUiIds.SETTINGS_NOTIFICATIONS,
            DesktopUiIds.SETTINGS_LINKS,
            DesktopUiIds.SETTINGS_SECURITY,
            DesktopUiIds.SETTINGS_GENERAL
        }) {
            DesktopUiRobot.selectSettingsTab(robot, tabId);
            capture(stage(robot), out.resolve("03-settings-" + tabId.replace("desktop-settings-", "") + ".png"));
        }

        DesktopUiRobot.selectShellTab(robot, DesktopUiIds.TAB_SERVERS);
        capture(stage(robot), out.resolve("04-servers-tab.png"));

        DesktopUiRobot.selectShellTab(robot, DesktopUiIds.TAB_CHATS);
        robot.interact(() -> {
            TitledPane pane = robot.lookup("#" + DesktopUiIds.THREAD_TOGGLE).query();
            pane.setExpanded(true);
        });
        DesktopUiRobot.waitForFx();
        capture(stage(robot), out.resolve("05-chats-thread-expanded.png"));
    }

    private static Stage stage(FxRobot robot) {
        return (Stage) robot.listWindows().getFirst();
    }

    private static void capture(Stage stage, Path file) throws Exception {
        var err = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Scene scene = stage.getScene();
                WritableImage shot = scene.snapshot(null);
                ImageIO.write(SwingFXUtils.fromFXImage(shot, null), "png", file.toFile());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("screenshot timeout");
        }
        if (err.get() != null) {
            if (err.get() instanceof Exception ex) {
                throw ex;
            }
            throw new IllegalStateException(err.get());
        }
    }

    private static Path outDir() {
        var env = System.getenv("KORUS_DESKTOP_VISUAL_OUT");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        var prop = System.getProperty("korus.desktop.visual.out");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        return Path.of("deploy/desktop/run/visual-audit/desktop");
    }
}
