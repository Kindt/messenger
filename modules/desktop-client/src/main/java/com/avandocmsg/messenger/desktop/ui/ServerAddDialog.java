package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.DesktopRuntime;
import com.avandocmsg.messenger.desktop.sdk.model.ServerEntry;
import com.avandocmsg.messenger.desktop.sdk.secure.SecureMemory;
import java.util.UUID;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public final class ServerAddDialog {

    private ServerAddDialog() {}

    public static void show(DesktopRuntime runtime, String username, Runnable onAdded) {
        var dialog = new Dialog<Void>();
        dialog.setTitle("Добавить сервер");
        dialog.getDialogPane().getButtonTypes().addAll(
            new javafx.scene.control.ButtonType("Подключить", ButtonBar.ButtonData.OK_DONE),
            new javafx.scene.control.ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE)
        );
        var grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        var url = new TextField("http://127.0.0.1:18080");
        url.setId(DesktopUiIds.SERVER_ADD_URL);
        var name = new TextField("Lab");
        name.setId(DesktopUiIds.SERVER_ADD_NAME);
        var user = new TextField(username == null ? "" : username);
        user.setId(DesktopUiIds.SERVER_ADD_USER);
        user.setPromptText("username");
        var pass = new PasswordField();
        pass.setId(DesktopUiIds.SERVER_ADD_PASS);
        var status = new Label();
        status.setId(DesktopUiIds.SERVER_ADD_STATUS);
        status.setWrapText(true);
        status.getStyleClass().add("qip-error-text");
        int row = 0;
        grid.add(new Label("URL"), 0, row);
        grid.add(url, 1, row++);
        grid.add(new Label("Название"), 0, row);
        grid.add(name, 1, row++);
        grid.add(new Label("Логин"), 0, row);
        grid.add(user, 1, row++);
        grid.add(new Label("Пароль"), 0, row);
        grid.add(pass, 1, row++);
        grid.add(status, 1, row);
        dialog.getDialogPane().setContent(grid);
        var connectBtn = dialog.getDialogPane().lookupButton(
            dialog.getDialogPane().getButtonTypes().getFirst()
        );
        connectBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            status.setText("");
            var serverUrl = url.getText().trim();
            var serverName = name.getText().trim();
            var loginUser = user.getText().trim();
            if (serverUrl.isBlank() || serverName.isBlank() || loginUser.isBlank()) {
                status.setText("Заполните URL, название и логин");
                event.consume();
                return;
            }
            char[] passChars = pass.getText().toCharArray();
            try {
                var entry = new ServerEntry(UUID.randomUUID().toString(), serverName, serverUrl);
                runtime.connectCoordinator().connect(entry, loginUser, pass.getText(), null, false);
                onAdded.run();
            } catch (Exception ex) {
                status.setText(ex.getMessage() == null ? "Ошибка подключения" : ex.getMessage());
                event.consume();
            } finally {
                SecureMemory.wipe(passChars);
                pass.clear();
            }
        });
        dialog.showAndWait();
    }
}
