package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.DesktopRuntime;
import com.avandocmsg.messenger.desktop.sdk.identity.ServerId;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.session.DesktopSession;
import com.avandocmsg.messenger.desktop.sdk.vpn.ServerVpnBinding;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnAuthMethod;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnConnectMode;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfile;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnProfileValidator;
import com.avandocmsg.messenger.desktop.sdk.vpn.VpnProtocol;
import com.avandocmsg.messenger.desktop.ui.branding.DesktopBrandingApplier;
import java.util.HashMap;
import java.util.UUID;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Per-server VPN + branding settings (offline stubs, spec 031 W3). */
public final class ServerSettingsDialog {

    private ServerSettingsDialog() {}

    public static void show(
        Stage owner,
        DesktopRuntime runtime,
        DesktopSession session,
        ServerEntry server,
        String username,
        Runnable onChanged
    ) {
        var dialog = new javafx.stage.Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Сервер: " + server.displayName());

        var tabs = new TabPane();
        tabs.getTabs().add(vpnTab(runtime, server, dialog, onChanged));
        tabs.getTabs().add(brandingTab(runtime, session, server, username, dialog, onChanged));

        var root = new VBox(12, tabs, closeButton(dialog));
        root.setPadding(new Insets(12));
        var scene = new javafx.scene.Scene(root, 640, 520);
        DesktopBrandingApplier.apply(scene, null, true);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private static Tab vpnTab(DesktopRuntime runtime, ServerEntry server, Stage dialog, Runnable onChanged) {
        var store = runtime.vpnStore();
        var orchestrator = runtime.vpnOrchestrator();
        VpnProfile existing;
        ServerVpnBinding binding;
        try {
            existing = store.profileForServer(server.serverId()).orElse(null);
            binding = store.bindingForServer(server.serverId()).orElse(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        var profileId = existing == null ? UUID.randomUUID().toString() : existing.profileId();
        var name = new TextField(existing == null ? server.displayName() + " VPN" : existing.displayName());
        name.setId(DesktopUiIds.VPN_NAME);
        var protocol = new ComboBox<VpnProtocol>();
        protocol.getItems().addAll(VpnProtocol.values());
        protocol.setValue(existing == null ? VpnProtocol.WIREGUARD : existing.protocolEnum());
        protocol.setId(DesktopUiIds.VPN_PROTOCOL);
        var auth = new ComboBox<VpnAuthMethod>();
        auth.getItems().addAll(VpnAuthMethod.values());
        auth.setValue(existing == null ? VpnAuthMethod.PASSWORD : existing.authEnum());
        auth.setId(DesktopUiIds.VPN_AUTH);
        var connectMode = new ComboBox<VpnConnectMode>();
        connectMode.getItems().addAll(VpnConnectMode.values());
        connectMode.setValue(binding == null ? VpnConnectMode.MANUAL : binding.connectModeEnum());
        var host = new TextField(existing == null ? "vpn.example.com" : nullSafe(existing.serverHost()));
        host.setId(DesktopUiIds.VPN_HOST);
        var port = new TextField(existing != null && existing.serverPort() != null ? String.valueOf(existing.serverPort()) : "51820");
        var user = new TextField(existing == null ? "" : nullSafe(existing.username()));
        user.setId(DesktopUiIds.VPN_USER);
        var password = new PasswordField();
        password.setId(DesktopUiIds.VPN_PASSWORD);
        var totp = new CheckBox("2FA (TOTP)");
        totp.setSelected(existing != null && existing.totpEnabled());
        totp.setId(DesktopUiIds.VPN_TOTP);
        var totpSecret = new PasswordField();
        totpSecret.setPromptText("TOTP secret (base32)");
        var totpCode = new TextField();
        totpCode.setPromptText("Код 2FA при подключении");
        totpCode.setId(DesktopUiIds.VPN_TOTP_CODE);
        var wg = new TextArea(existing == null ? defaultWireGuard() : nullSafe(existing.wireguardConfig()));
        wg.setPrefRowCount(6);
        wg.setId(DesktopUiIds.VPN_WG_CONFIG);
        var enabled = new CheckBox("VPN включён для этого сервера");
        enabled.setSelected(binding == null || binding.enabled());
        var status = new Label();
        status.setId(DesktopUiIds.VPN_STATUS);

        Runnable refreshStatus = () -> status.setText(orchestrator.state(server.serverId()).message());
        refreshStatus.run();

        var save = new Button("Сохранить профиль");
        save.setId(DesktopUiIds.VPN_SAVE);
        save.setOnAction(e -> {
            try {
                var profile = new VpnProfile(
                    1,
                    profileId,
                    name.getText().trim(),
                    protocol.getValue().wireId(),
                    auth.getValue().wireId(),
                    connectMode.getValue().wireId(),
                    host.getText().trim(),
                    parsePort(port.getText()),
                    user.getText().trim(),
                    totp.isSelected() || auth.getValue().requiresTotp(),
                    true,
                    null,
                    wg.getText(),
                    null,
                    new HashMap<>(),
                    null
                );
                var errors = VpnProfileValidator.validate(profile);
                if (!errors.isEmpty()) {
                    status.setText(String.join("; ", errors));
                    return;
                }
                store.upsertProfile(profile);
                if (!password.getText().isBlank()) {
                    store.storePassword(profileId, password.getText());
                }
                if (!totpSecret.getText().isBlank()) {
                    store.storeTotpSecret(profileId, totpSecret.getText());
                }
                store.bindServer(new ServerVpnBinding(
                    server.serverId(),
                    profileId,
                    connectMode.getValue().wireId(),
                    enabled.isSelected()
                ));
                status.setText("Сохранено");
                onChanged.run();
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        });

        var connect = new Button("Подключить VPN");
        connect.setId(DesktopUiIds.VPN_CONNECT);
        connect.setOnAction(e -> {
            try {
                var state = orchestrator.connectServer(server.serverId(), totpCode.getText().trim());
                status.setText(state.message());
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        });

        var disconnect = new Button("Отключить");
        disconnect.setId(DesktopUiIds.VPN_DISCONNECT);
        disconnect.setOnAction(e -> {
            try {
                var state = orchestrator.disconnectServer(server.serverId());
                status.setText(state.message());
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        });

        var grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int r = 0;
        grid.add(new Label("Протокол"), 0, r);
        grid.add(protocol, 1, r++);
        grid.add(new Label("Аутентификация"), 0, r);
        grid.add(auth, 1, r++);
        grid.add(new Label("Режим"), 0, r);
        grid.add(connectMode, 1, r++);
        grid.add(new Label("Хост"), 0, r);
        grid.add(host, 1, r++);
        grid.add(new Label("Порт"), 0, r);
        grid.add(port, 1, r++);
        grid.add(new Label("Пользователь"), 0, r);
        grid.add(user, 1, r++);
        grid.add(new Label("Пароль"), 0, r);
        grid.add(password, 1, r++);
        grid.add(totp, 1, r++);
        grid.add(new Label("TOTP secret"), 0, r);
        grid.add(totpSecret, 1, r++);
        grid.add(new Label("TOTP код"), 0, r);
        grid.add(totpCode, 1, r++);
        grid.add(new Label("WireGuard"), 0, r);
        grid.add(wg, 1, r++);

        var box = new VBox(10, enabled, grid, new javafx.scene.layout.HBox(8, save, connect, disconnect), status);
        box.setPadding(new Insets(8));
        var tab = new Tab("VPN", box);
        tab.setClosable(false);
        tab.setId(DesktopUiIds.SERVER_TAB_VPN);
        return tab;
    }

    private static Tab brandingTab(
        DesktopRuntime runtime,
        DesktopSession session,
        ServerEntry server,
        String username,
        Stage dialog,
        Runnable onChanged
    ) {
        var status = new Label();
        status.setId(DesktopUiIds.BRANDING_STATUS);
        var preview = new Label();
        preview.setWrapText(true);
        var refresh = new Button("Загрузить брендинг (demo/API)");
        refresh.setId(DesktopUiIds.BRANDING_REFRESH);
        refresh.setOnAction(e -> {
            try {
                var sid = new ServerId(server.serverId());
                var token = session.isDemo() ? null : runtime.sessions().token(sid, username);
                var api = session.isDemo() ? null : runtime.sessions().clientFor(server);
                var snap = runtime.brandingService().fetchAndCache(session, server, username, api, token);
                preview.setText("palette=" + snap.palette() + ", title=" + snap.brandTitle());
                DesktopBrandingApplier.apply(dialog.getScene(), snap, true);
                status.setText("OK revision=" + snap.revision());
                onChanged.run();
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        });
        var box = new VBox(10, refresh, preview, status);
        box.setPadding(new Insets(8));
        var tab = new Tab("Брендинг", box);
        tab.setClosable(false);
        tab.setId(DesktopUiIds.SERVER_TAB_BRANDING);
        return tab;
    }

    private static Button closeButton(Stage dialog) {
        var close = new Button("Закрыть");
        close.setOnAction(e -> dialog.close());
        return close;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static Integer parsePort(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.parseInt(text.trim());
    }

    private static String defaultWireGuard() {
        return """
            [Interface]
            PrivateKey = demo-private-key
            Address = 10.8.0.2/32

            [Peer]
            PublicKey = demo-public-key
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0
            """;
    }
}
