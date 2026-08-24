package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.DesktopRuntime;
import com.avandocmsg.messenger.desktop.sdk.capabilities.CapabilityGate;
import com.avandocmsg.messenger.desktop.sdk.model.CapabilitiesResponse;
import com.avandocmsg.messenger.desktop.sdk.model.ProfileSettings;
import com.avandocmsg.messenger.desktop.sdk.security.SecuritySelfCheck;
import com.avandocmsg.messenger.desktop.sdk.security.SecuritySettings;
import com.avandocmsg.messenger.desktop.sdk.security.SecuritySettingsStore;
import com.avandocmsg.messenger.desktop.sdk.storage.ProfileSettingsStore;
import com.avandocmsg.messenger.desktop.sdk.update.DesktopVersions;
import com.avandocmsg.messenger.desktop.sdk.update.UpdateCheckHelper;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Settings tabs aligned with web-client: general, profile, notifications, links, security. */
public final class SettingsView {

    private final TabPane root = new TabPane();

    public SettingsView(DesktopRuntime runtime, CapabilitiesResponse caps, Runnable onSaved) throws Exception {
        var profileId = runtime.activeProfile().profileId();
        var store = new ProfileSettingsStore(runtime.profileStore());
        var settings = store.read(profileId);
        var gate = new CapabilityGate(caps);
        var securityStore = new SecuritySettingsStore(runtime.profileStore(), profileId);
        var security = securityStore.read();

        root.setId(DesktopUiIds.SETTINGS_TABS);
        root.getStyleClass().add("qip-settings-tabs");
        root.getTabs().add(tabGeneral(store, profileId, settings, onSaved));
        root.getTabs().add(tabProfile(runtime, onSaved));
        root.getTabs().add(tabNotifications(store, profileId, settings, securityStore, security, onSaved));
        root.getTabs().add(tabLinks(store, profileId, settings, onSaved));
        root.getTabs().add(tabSecurity(runtime, gate, securityStore, security));
        root.setTabMinWidth(120);
    }

    private Tab tabGeneral(ProfileSettingsStore store, String profileId, ProfileSettings s, Runnable onSaved)
        throws Exception {
        var locale = new ComboBox<String>();
        locale.getItems().addAll("ru", "en", "de", "fr", "es", "zh");
        locale.setValue(s.locale() == null ? "ru" : s.locale());
        var theme = new ComboBox<String>();
        theme.getItems().addAll("system", "light", "dark");
        theme.setValue(s.theme() == null ? "system" : s.theme());
        var updateChannel = new ComboBox<String>();
        updateChannel.setId(DesktopUiIds.SETTINGS_UPDATE_CHANNEL);
        updateChannel.getItems().addAll("stable", "beta");
        updateChannel.setValue(s.updateChannel() == null ? "stable" : s.updateChannel());
        var updateFeed = new TextField(s.updateFeedUrl() == null ? "" : s.updateFeedUrl());
        updateFeed.setId(DesktopUiIds.SETTINGS_UPDATE_FEED);
        updateFeed.setPromptText("URL манифеста (пусто = demo-update-manifest.json)");
        var updateStatus = statusLabel("Версия клиента: " + DesktopVersions.CURRENT, DesktopUiIds.SETTINGS_UPDATE_STATUS);
        var checkUpdate = new javafx.scene.control.Button("Проверить обновления");
        checkUpdate.setId(DesktopUiIds.SETTINGS_UPDATE_CHECK);
        checkUpdate.getStyleClass().add("qip-settings-btn-secondary");
        checkUpdate.setOnAction(e -> {
            try {
                var feed = updateFeed.getText().isBlank() ? defaultDemoManifestUrl() : updateFeed.getText().trim();
                var result = UpdateCheckHelper.check(feed, DesktopVersions.CURRENT);
                updateStatus.setText(result.message());
            } catch (Exception ex) {
                updateStatus.setText("Ошибка: " + ex.getMessage());
            }
        });
        var save = new javafx.scene.control.Button("Сохранить");
        save.setId(DesktopUiIds.SETTINGS_SAVE);
        save.getStyleClass().add("qip-settings-btn-primary");
        save.setOnAction(e -> {
            try {
                store.write(profileId, new ProfileSettings(
                    locale.getValue(), theme.getValue(), s.attachmentsRoot(),
                    updateChannel.getValue(), s.updatePolicy(), updateFeed.getText().trim()
                ));
                onSaved.run();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        var box = new VBox(
            10,
            heading("Язык и тема"),
            grid(locale, theme),
            heading("Обновления клиента"),
            fieldLabel("Канал"),
            updateChannel,
            fieldLabel("Feed URL"),
            updateFeed,
            checkUpdate,
            updateStatus,
            save
        );
        box.setPadding(new Insets(12));
        box.getStyleClass().add("qip-settings-panel");
        var tab = new Tab("Общие", box);
        tab.setId(DesktopUiIds.SETTINGS_GENERAL);
        return tab;
    }

    private static String defaultDemoManifestUrl() throws Exception {
        var resource = SettingsView.class.getResource("/demo-update-manifest.json");
        if (resource == null) {
            throw new IllegalStateException("demo-update-manifest.json missing");
        }
        return resource.toURI().toString();
    }

    private Tab tabProfile(DesktopRuntime runtime, Runnable onSaved) {
        var name = new TextField(runtime.activeProfile().displayName());
        var save = new javafx.scene.control.Button("Сохранить имя профиля");
        save.setOnAction(e -> onSaved.run());
        var box = new VBox(10, fieldLabel("Отображаемое имя профиля"), name, save);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("qip-settings-panel");
        var tab = new Tab("Профиль", box);
        tab.setId(DesktopUiIds.SETTINGS_PROFILE);
        return tab;
    }

    private Tab tabNotifications(
        ProfileSettingsStore store,
        String profileId,
        ProfileSettings s,
        SecuritySettingsStore securityStore,
        SecuritySettings security,
        Runnable onSaved
    ) {
        var push = new CheckBox("Системные уведомления (OS)");
        push.setSelected(security.osNotificationsEnabled());
        if (!DesktopOsNotifications.isSupported()) {
            push.setDisable(true);
            push.setSelected(false);
        }
        var sound = new CheckBox("Звук входящих сообщений");
        sound.setId(DesktopUiIds.SETTINGS_SOUND);
        sound.setSelected(security.soundNotifications());
        var testPush = new javafx.scene.control.Button("Проверить уведомление");
        testPush.setId(DesktopUiIds.SETTINGS_NOTIFICATIONS_TEST);
        testPush.setDisable(!DesktopOsNotifications.isSupported());
        testPush.setOnAction(e -> DesktopOsNotifications.show(
            "Korus Messenger",
            "Тестовое уведомление — входящее сообщение"
        ));
        var policy = new ComboBox<String>();
        policy.getItems().addAll("notify", "auto", "disabled");
        policy.setValue(s.updatePolicy() == null ? "notify" : s.updatePolicy());
        var save = new javafx.scene.control.Button("Сохранить");
        save.setOnAction(e -> {
            try {
                store.write(profileId, new ProfileSettings(
                    s.locale(), s.theme(), s.attachmentsRoot(),
                    s.updateChannel(), policy.getValue(), s.updateFeedUrl()
                ));
                var current = securityStore.read();
                securityStore.write(new SecuritySettings(
                    current.tlsPinningRequired(),
                    current.idleLockMinutes(),
                    current.clipboardAutoClearSec(),
                    current.clearTokensOnExit(),
                    current.auditLogEnabled(),
                    sound.isSelected(),
                    push.isSelected(),
                    current.blockScreenshots(),
                    current.requireSecureUpdates()
                ));
                onSaved.run();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        var trayHint = DesktopOsNotifications.isSupported()
            ? hint("Уведомления в центре Windows при входящих сообщениях (если окно не в фокусе).")
            : hint("Системный трей недоступен в этой среде (headless / TestFX).");
        var box = new VBox(10, push, sound, trayHint, testPush, fieldLabel("Политика обновлений клиента"), policy, save);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("qip-settings-panel");
        var tab = new Tab("Уведомления", box);
        tab.setId(DesktopUiIds.SETTINGS_NOTIFICATIONS);
        return tab;
    }

    private Tab tabLinks(ProfileSettingsStore store, String profileId, ProfileSettings s, Runnable onSaved) {
        var defaultPath = com.avandocmsg.messenger.desktop.sdk.DesktopPaths.downloadsRoot()
            .resolve("KorusMessenger")
            .toString();
        var path = new TextField(s.attachmentsRoot() == null ? defaultPath : s.attachmentsRoot());
        path.setId(DesktopUiIds.SETTINGS_ATTACH_PATH);
        var save = new javafx.scene.control.Button("Сохранить путь вложений");
        save.setOnAction(e -> {
            try {
                store.write(profileId, new ProfileSettings(
                    s.locale(), s.theme(), path.getText().trim(),
                    s.updateChannel(), s.updatePolicy(), s.updateFeedUrl()
                ));
                onSaved.run();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        var box = new VBox(10, fieldLabel("Корень для вложений (как web Downloads/KorusMessenger)"), path, save);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("qip-settings-panel");
        var tab = new Tab("Ссылки и файлы", box);
        tab.setId(DesktopUiIds.SETTINGS_LINKS);
        return tab;
    }

    private Tab tabSecurity(
        DesktopRuntime runtime,
        CapabilityGate gate,
        SecuritySettingsStore securityStore,
        SecuritySettings initial
    ) throws Exception {
        var tlsPin = check("TLS pinning обязателен", initial.tlsPinningRequired());
        var idle = new Spinner<Integer>(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(
            0, 120, initial.idleLockMinutes()
        ));
        idle.setId(DesktopUiIds.SETTINGS_IDLE_LOCK);
        idle.setEditable(true);
        var clipboard = check("Автоочистка буфера обмена", initial.clipboardAutoClearSec());
        var clearExit = check("Очищать токены при выходе", initial.clearTokensOnExit());
        var audit = check("Журнал безопасности", initial.auditLogEnabled());
        var signedUpd = check("Только подписанные обновления", initial.requireSecureUpdates());
        var scoreLabel = statusLabel("", DesktopUiIds.SETTINGS_SECURITY_SCORE);
        Runnable refreshScore = () -> {
            try {
                var draft = draftSecurity(initial, tlsPin, idle, clipboard, clearExit, audit, signedUpd);
                var report = SecuritySelfCheck.run(
                    runtime.profileStore().stateDir(runtime.activeProfile().profileId()),
                    draft
                );
                scoreLabel.setText("Оценка ФСТЭК: " + report.grade() + " (" + report.score() + "/" + report.maxScore() + ")");
            } catch (Exception ex) {
                scoreLabel.setText("Оценка: " + ex.getMessage());
            }
        };
        refreshScore.run();
        var e2ee = hint(gate.isEnabled(CapabilityGate.Feature.E2EE)
            ? "E2EE (MLS): включено — AES-GCM + HKDF (parity web)"
            : "E2EE: отключено на сервере");
        var ent = hint(gate.isEnabled(CapabilityGate.Feature.ENTERPRISE_AUTH)
            ? "Enterprise auth: включено"
            : "Enterprise auth: не в составе сервера");
        var save = new javafx.scene.control.Button("Сохранить политику безопасности");
        save.setId(DesktopUiIds.SETTINGS_SECURITY_SAVE);
        save.setOnAction(e -> {
            try {
                var latest = securityStore.read();
                securityStore.write(draftSecurity(latest, tlsPin, idle, clipboard, clearExit, audit, signedUpd));
                refreshScore.run();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        var fstecMax = new javafx.scene.control.Button("Профиль «максимум ФСТЭК»");
        fstecMax.setOnAction(e -> {
            try {
                var max = SecuritySettings.fstecMaximum();
                securityStore.write(max);
                applySecurity(max, tlsPin, idle, clipboard, clearExit, audit, signedUpd);
                refreshScore.run();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        var box = new VBox(
            10,
            heading("Политика безопасности клиента"),
            scoreLabel,
            tlsPin,
            fieldLabel("Блокировка при бездействии (мин, 0=выкл)"),
            idle,
            clipboard,
            clearExit,
            audit,
            signedUpd,
            e2ee,
            ent,
            fstecMax,
            save
        );
        box.setPadding(new Insets(12));
        box.getStyleClass().add("qip-settings-panel");
        var tab = new Tab("Безопасность", box);
        tab.setId(DesktopUiIds.SETTINGS_SECURITY);
        return tab;
    }

    private static CheckBox check(String text, boolean selected) {
        var c = new CheckBox(text);
        c.setSelected(selected);
        return c;
    }

    private static SecuritySettings draftSecurity(
        SecuritySettings current,
        CheckBox tlsPin,
        Spinner<Integer> idle,
        CheckBox clipboard,
        CheckBox clearExit,
        CheckBox audit,
        CheckBox signedUpd
    ) {
        return new SecuritySettings(
            tlsPin.isSelected(),
            idle.getValue(),
            clipboard.isSelected(),
            clearExit.isSelected(),
            audit.isSelected(),
            current.soundNotifications(),
            current.osNotificationsEnabled(),
            current.blockScreenshots(),
            signedUpd.isSelected()
        );
    }

    private static void applySecurity(
        SecuritySettings s,
        CheckBox tlsPin,
        Spinner<Integer> idle,
        CheckBox clipboard,
        CheckBox clearExit,
        CheckBox audit,
        CheckBox signedUpd
    ) {
        tlsPin.setSelected(s.tlsPinningRequired());
        idle.getValueFactory().setValue(s.idleLockMinutes());
        clipboard.setSelected(s.clipboardAutoClearSec());
        clearExit.setSelected(s.clearTokensOnExit());
        audit.setSelected(s.auditLogEnabled());
        signedUpd.setSelected(s.requireSecureUpdates());
    }

    private GridPane grid(ComboBox<String> locale, ComboBox<String> theme) {
        var g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.getStyleClass().add("qip-settings-grid");
        g.add(fieldLabel("Язык"), 0, 0);
        g.add(locale, 1, 0);
        g.add(fieldLabel("Тема"), 0, 1);
        g.add(theme, 1, 1);
        return g;
    }

    private static Label heading(String text) {
        var label = new Label(text);
        label.getStyleClass().add("qip-settings-heading");
        label.setWrapText(true);
        return label;
    }

    private static Label fieldLabel(String text) {
        var label = new Label(text);
        label.getStyleClass().add("qip-settings-field-label");
        label.setWrapText(true);
        return label;
    }

    private static Label hint(String text) {
        var label = new Label(text);
        label.getStyleClass().add("qip-settings-hint");
        label.setWrapText(true);
        return label;
    }

    private static Label statusLabel(String text, String fxId) {
        var label = new Label(text);
        label.setId(fxId);
        label.getStyleClass().add("qip-settings-status");
        label.setWrapText(true);
        return label;
    }

    public Parent root() {
        return root;
    }
}
