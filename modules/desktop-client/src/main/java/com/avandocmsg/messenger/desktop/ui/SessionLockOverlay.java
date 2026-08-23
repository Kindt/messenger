package com.avandocmsg.messenger.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Idle session lock overlay (FSTEC access control). */
public final class SessionLockOverlay extends StackPane {

    private final Runnable onUnlock;

    public SessionLockOverlay(Runnable onUnlock) {
        this.onUnlock = onUnlock;
        getStyleClass().add("qip-lock-overlay");
        setVisible(false);
        setManaged(false);
        setMouseTransparent(true);
        var card = new VBox(12);
        card.getStyleClass().add("qip-lock-card");
        card.setAlignment(Pos.CENTER);
        card.getChildren().addAll(
            new Label("Сессия заблокирована"),
            new Label("Нет активности — разблокируйте для продолжения"),
            unlockButton()
        );
        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    public void lock() {
        setMouseTransparent(false);
        setVisible(true);
        setManaged(true);
        toFront();
    }

    public void unlock() {
        setVisible(false);
        setManaged(false);
        setMouseTransparent(true);
    }

    private Button unlockButton() {
        var btn = new Button("Разблокировать");
        btn.setId(DesktopUiIds.SESSION_UNLOCK);
        btn.getStyleClass().add("qip-btn-send");
        btn.setOnAction(e -> {
            unlock();
            onUnlock.run();
        });
        return btn;
    }
}
