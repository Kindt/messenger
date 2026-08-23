package com.avandocmsg.messenger.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** QIP-style contact row: animated status orb, avatar initial, name + server line. */
final class ContactListCell extends ListCell<InboxRow> {

    private final HBox row = new HBox(8);
    private final QipStatusOrb statusOrb = new QipStatusOrb();
    private final Label avatar = new Label();
    private final Label name = new Label();
    private final Label statusLine = new Label();
    private final VBox textCol = new VBox(1, name, statusLine);

    ContactListCell() {
        row.getStyleClass().add("qip-contact-row");
        row.setAlignment(Pos.CENTER_LEFT);
        avatar.getStyleClass().add("qip-contact-avatar");
        name.getStyleClass().add("qip-contact-name");
        statusLine.getStyleClass().add("qip-contact-status-line");
        HBox.setHgrow(textCol, Priority.ALWAYS);
        row.getChildren().addAll(statusOrb, avatar, textCol);
    }

    @Override
    protected void updateItem(InboxRow item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            return;
        }
        var title = item.chat().title();
        if (title == null || title.isBlank()) {
            title = item.chat().resolvedId();
        }
        name.setText(title);
        statusLine.setText(item.server().displayName());
        avatar.setText(initials(title));
        applyStatus(item);
        setGraphic(row);
        setText(null);
    }

    private void applyStatus(InboxRow item) {
        var mode = Math.floorMod(item.chat().resolvedId().hashCode(), 3);
        if (mode == 0) {
            statusOrb.setMode(QipStatusOrb.Mode.ONLINE);
        } else if (mode == 1) {
            statusOrb.setMode(QipStatusOrb.Mode.AWAY);
        } else {
            statusOrb.setMode(QipStatusOrb.Mode.OFFLINE);
        }
    }

    private static String initials(String title) {
        if (title == null || title.isBlank()) {
            return "?";
        }
        var parts = title.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return title.substring(0, Math.min(2, title.length())).toUpperCase();
    }
}
