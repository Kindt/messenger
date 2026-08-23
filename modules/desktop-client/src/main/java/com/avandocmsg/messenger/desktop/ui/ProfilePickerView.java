package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.model.LocalProfile;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class ProfilePickerView {

    private final Parent root;

    public ProfilePickerView(
        List<LocalProfile> profiles,
        ProfileCreateHandler onCreate,
        ProfileSelectHandler onSelect,
        Runnable onDemo
    ) {
        var form = new VBox(10);
        form.setPadding(new Insets(0));
        var demo = new Button("Демо без сервера");
        demo.setId(DesktopUiIds.PROFILE_DEMO);
        demo.getStyleClass().add("qip-btn-send");
        demo.setOnAction(e -> onDemo.run());
        var name = new TextField();
        name.setId(DesktopUiIds.PROFILE_NAME);
        name.setPromptText("Имя профиля");
        var create = new Button("Создать профиль");
        create.setId(DesktopUiIds.PROFILE_CREATE);
        create.setOnAction(e -> {
            if (!name.getText().isBlank()) {
                onCreate.create(name.getText().trim());
            }
        });
        var list = new ListView<LocalProfile>();
        list.setId(DesktopUiIds.PROFILE_LIST);
        list.getItems().addAll(profiles);
        list.setPrefHeight(140);
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(LocalProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        var open = new Button("Войти в профиль");
        open.setId(DesktopUiIds.PROFILE_OPEN);
        open.setOnAction(e -> {
            var selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                onSelect.select(selected);
            }
        });
        form.getChildren().addAll(
            demo,
            new Label("Новый профиль"),
            name,
            create,
            new Label("Существующие профили"),
            list,
            open
        );
        root = QipAuthLayout.wrap(form, "Korus Messenger", "Выбор профиля — как в QIP, только современнее");
    }

    public Parent root() {
        return root;
    }

    @FunctionalInterface
    public interface ProfileCreateHandler {
        void create(String displayName);
    }

    @FunctionalInterface
    public interface ProfileSelectHandler {
        void select(LocalProfile profile);
    }
}
