package com.avandocmsg.messenger.desktop.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.FlowPane;
import javafx.stage.Popup;

public final class EmojiPickerPopup {

    private static final String[] EMOJI = {
        "😀", "😁", "😂", "😉", "😍", "😎", "👍", "👋", "🔥", "❤️",
        "🎉", "✅", "❌", "🤔", "😢", "😡", "🙏", "💡", "📎", "📞"
    };

    private static final String[] STICKERS = {
        "(·_·)", "(^_^)", "¯\\_(ツ)_/¯", "(╯°□°)╯", "\\o/", "<3", ";)", ":D", ":P", "O_o"
    };

    private EmojiPickerPopup() {}

    public static void attach(Button trigger, TextInputControl target) {
        var popup = new Popup();
        popup.setAutoHide(true);
        var emojiPane = grid(EMOJI, target, popup);
        var stickerPane = grid(STICKERS, target, popup);
        var tabs = new TabPane(
            tab("Смайлы", emojiPane),
            tab("Стикеры", stickerPane)
        );
        tabs.getStyleClass().add("qip-emoji-tabs");
        tabs.setTabMinWidth(80);
        var wrap = new javafx.scene.layout.VBox(tabs);
        wrap.setPadding(new Insets(6));
        wrap.getStyleClass().add("qip-emoji-popup");
        popup.getContent().add(wrap);
        trigger.setOnAction(e -> {
            if (popup.isShowing()) {
                popup.hide();
            } else {
                var b = trigger.localToScreen(trigger.getBoundsInLocal());
                popup.show(trigger, b.getMinX(), b.getMaxY() + 4);
            }
        });
    }

    private static Tab tab(String title, FlowPane pane) {
        var t = new Tab(title, pane);
        t.setClosable(false);
        return t;
    }

    private static FlowPane grid(String[] items, TextInputControl target, Popup popup) {
        var flow = new FlowPane(4, 4);
        flow.setPadding(new Insets(4));
        for (var item : items) {
            var btn = new Button(item);
            btn.getStyleClass().add("qip-emoji-btn");
            btn.setOnAction(e -> {
                target.appendText(item);
                target.requestFocus();
                popup.hide();
            });
            flow.getChildren().add(btn);
        }
        return flow;
    }
}
