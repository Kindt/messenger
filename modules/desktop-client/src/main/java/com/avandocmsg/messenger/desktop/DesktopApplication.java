package com.avandocmsg.messenger.desktop;

import com.avandocmsg.messenger.desktop.sdk.DesktopRuntime;
import com.avandocmsg.messenger.desktop.sdk.demo.DemoDataStore;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.LocalProfile;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.session.ApiDesktopSession;
import com.avandocmsg.messenger.desktop.sdk.session.DemoDesktopSession;
import com.avandocmsg.messenger.desktop.sdk.session.DesktopSession;
import com.avandocmsg.messenger.desktop.ui.ProfilePickerView;
import com.avandocmsg.messenger.desktop.ui.LoginView;
import com.avandocmsg.messenger.desktop.ui.MainShellView;
import com.avandocmsg.messenger.desktop.ui.WindowChromeHelper;
import com.avandocmsg.messenger.desktop.sdk.storage.ProfileSettingsStore;
import com.avandocmsg.messenger.desktop.ui.DesktopOsNotifications;
import com.avandocmsg.messenger.desktop.ui.DesktopThemeApplier;
import java.util.UUID;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class DesktopApplication extends Application {

    private final DesktopRuntime runtime = new DesktopRuntime();
    private DesktopSession session;
    private Stage primaryStage;
    private String loggedInUser = "demo-user";

    public static boolean isDemoMode() {
        return "1".equals(System.getenv("KORUS_DESKTOP_DEMO"))
            || "true".equalsIgnoreCase(System.getProperty("korus.desktop.demo"));
    }

    private static String autostartMode() {
        return System.getProperty("korus.desktop.autostart", "");
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        DesktopOsNotifications.init(() -> Platform.runLater(() -> {
            if (primaryStage != null) {
                primaryStage.setIconified(false);
                primaryStage.show();
                primaryStage.toFront();
                primaryStage.requestFocus();
            }
        }));
        stage.setTitle("Korus Messenger Desktop" + (isDemoMode() ? " [DEMO]" : ""));
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        if (!stage.isShowing()) {
            WindowChromeHelper.applyUndecorated(stage);
        }
        if ("demo".equalsIgnoreCase(autostartMode())) {
            try {
                openDemoProfile();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {
            showProfilePicker();
        }
        stage.show();
    }

    private void showProfilePicker() {
        try {
            var profiles = runtime.profileStore().listProfiles();
            var view = new ProfilePickerView(
                profiles,
                name -> {
                    try {
                        openProfile(runtime.profileStore().createProfile(name));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                profile -> {
                    try {
                        openProfile(profile);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                () -> {
                    try {
                        openDemoProfile();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            );
            setScene(new Scene(view.root(), 520, 460));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void openDemoProfile() throws Exception {
        var profiles = runtime.profileStore().listProfiles();
        LocalProfile profile;
        if (profiles.isEmpty()) {
            profile = runtime.profileStore().createProfile("Demo User");
        } else {
            profile = profiles.getFirst();
        }
        runtime.activateProfile(profile);
        session = new DemoDesktopSession(new DemoDataStore());
        loggedInUser = "demo-user";
        Runnable openShell = () -> {
            try {
                showShell();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        };
        if (Platform.isFxApplicationThread()) {
            openShell.run();
        } else if (primaryStage.isShowing()) {
            Platform.runLater(openShell);
        } else {
            openShell.run();
        }
    }

    private void openProfile(LocalProfile profile) throws Exception {
        if (isDemoMode()) {
            openDemoProfile();
            return;
        }
        runtime.activateProfile(profile);
        var servers = runtime.serverRegistry().load().servers();
        if (servers.isEmpty()) {
            showLogin(null);
        } else {
            var first = servers.getFirst();
            var token = runtime.sessions().token(new ServerId(first.serverId()), savedUser());
            if (token == null || token.isBlank()) {
                showLogin(first);
            } else {
                loggedInUser = savedUser();
                session = new ApiDesktopSession(runtime.sessions(), runtime.fileTransferService());
                runtime.connectCoordinator().resumeConnected(first, loggedInUser);
                showShell();
            }
        }
    }

    private String savedUser() {
        return System.getenv().getOrDefault("KORUS_DESKTOP_USER", "admin");
    }

    private void showLogin(ServerEntry prefill) {
        var view = new LoginView(
            prefill == null ? "http://127.0.0.1:18080" : prefill.apiBaseUrl(),
            prefill == null ? "Lab" : prefill.displayName(),
            (serverUrl, serverName, user, pass) -> {
                try {
                    var serverId = prefill == null ? UUID.randomUUID().toString() : prefill.serverId();
                    var entry = new ServerEntry(serverId, serverName, serverUrl);
                    var result = runtime.connectCoordinator().connect(entry, user, pass, null, false);
                    loggedInUser = user;
                    session = new ApiDesktopSession(runtime.sessions(), runtime.fileTransferService());
                    showShell();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex.getMessage(), ex);
                }
            },
            this::showProfilePicker,
            () -> {
                try {
                    openDemoProfile();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        );
        setScene(new Scene(view.root(), 520, 420));
    }

    private void showShell() {
        try {
            var view = new MainShellView(
                runtime,
                session,
                loggedInUser,
                () -> {
                    try {
                        runtime.wipeActiveMemory();
                    } catch (Exception ignored) {
                        // still return to profile picker
                    }
                    session = null;
                    showProfilePicker();
                },
                this::showShell
            );
            setScene(new Scene(view.root(), 1200, 760));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void setScene(Scene scene) {
        var sheet = getClass().getResource("/desktop.css");
        if (sheet != null) {
            scene.getStylesheets().add(sheet.toExternalForm());
        }
        primaryStage.setScene(scene);
        applyThemeFromProfile(scene);
    }

    private void applyThemeFromProfile(Scene scene) {
        try {
            var profile = runtime.activeProfile();
            if (profile == null) {
                DesktopThemeApplier.apply(scene, "light");
                return;
            }
            var settings = new ProfileSettingsStore(runtime.profileStore()).read(profile.profileId());
            DesktopThemeApplier.apply(scene, settings.theme());
        } catch (Exception ignored) {
            DesktopThemeApplier.apply(scene, "light");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        DesktopOsNotifications.dispose();
    }
}
