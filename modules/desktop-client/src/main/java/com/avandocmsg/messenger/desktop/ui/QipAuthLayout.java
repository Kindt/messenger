package com.avandocmsg.messenger.desktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Centers auth forms on QIP gradient background. */
public final class QipAuthLayout {

    private QipAuthLayout() {}

    public static BorderPane wrap(Node form, String title, String subtitle) {
        var card = new VBox(12);
        card.getStyleClass().add("qip-auth-card");
        card.setMaxWidth(420);
        if (title != null && !title.isBlank()) {
            var heading = new javafx.scene.control.Label(title);
            heading.getStyleClass().add("qip-auth-title");
            card.getChildren().add(heading);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            var sub = new javafx.scene.control.Label(subtitle);
            sub.getStyleClass().add("qip-auth-subtitle");
            card.getChildren().add(sub);
        }
        card.getChildren().add(form);
        var shell = new BorderPane();
        shell.getStyleClass().add("qip-auth-shell");
        BorderPane.setAlignment(card, Pos.CENTER);
        BorderPane.setMargin(card, new Insets(24));
        shell.setCenter(card);
        return shell;
    }
}
