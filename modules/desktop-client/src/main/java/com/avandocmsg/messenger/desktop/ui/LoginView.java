package com.avandocmsg.messenger.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public final class LoginView {

    private final Parent root;

    public LoginView(
        String defaultUrl,
        String defaultName,
        LoginHandler onLogin,
        Runnable onSwitchProfile,
        Runnable onDemo
    ) {
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0));
        var url = new TextField(defaultUrl);
        url.setId(DesktopUiIds.LOGIN_URL);
        var name = new TextField(defaultName);
        name.setId(DesktopUiIds.LOGIN_NAME);
        var user = new TextField();
        user.setId(DesktopUiIds.LOGIN_USER);
        user.setPromptText("username");
        var pass = new PasswordField();
        pass.setId(DesktopUiIds.LOGIN_PASS);
        var status = new Label();
        status.setId(DesktopUiIds.LOGIN_STATUS);
        var login = new Button("Войти");
        login.setId(DesktopUiIds.LOGIN_SUBMIT);
        login.getStyleClass().add("qip-btn-send");
        login.setDefaultButton(true);
        login.setOnAction(e -> {
            char[] passChars = pass.getText().toCharArray();
            try {
                onLogin.login(url.getText().trim(), name.getText().trim(), user.getText(), pass.getText());
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            } finally {
                com.avandocmsg.messenger.desktop.sdk.secure.SecureMemory.wipe(passChars);
                pass.clear();
            }
        });
        var demo = new Button("Демо без сервера");
        demo.setId(DesktopUiIds.LOGIN_DEMO);
        demo.setOnAction(e -> onDemo.run());
        var back = new Button("Сменить профиль");
        back.setId(DesktopUiIds.LOGIN_BACK);
        back.setOnAction(e -> onSwitchProfile.run());
        int row = 0;
        grid.add(new Label("URL сервера"), 0, row);
        grid.add(url, 1, row++);
        grid.add(new Label("Название"), 0, row);
        grid.add(name, 1, row++);
        grid.add(new Label("Логин"), 0, row);
        grid.add(user, 1, row++);
        grid.add(new Label("Пароль"), 0, row);
        grid.add(pass, 1, row++);
        var actions = new VBox(8, login, demo, back, status);
        grid.add(actions, 1, row);
        root = QipAuthLayout.wrap(grid, "Подключение к серверу", "Введите учётные данные");
    }

    public Parent root() {
        return root;
    }

    @FunctionalInterface
    public interface LoginHandler {
        void login(String serverUrl, String serverName, String username, String password);
    }
}
