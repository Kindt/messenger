package com.avandocmsg.messenger.desktop.ui;

import com.avandocmsg.messenger.desktop.sdk.model.MessageDto;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** QIP-style bubbles: incoming left, outgoing right, system center. */
public final class ChatMessagePane extends ScrollPane {

    private final VBox bubbles = new VBox(8);
    private final StringBuilder textMirror = new StringBuilder();
    private String currentUserId = "";
    private Runnable retryAction = () -> {};

    public ChatMessagePane() {
        setId(DesktopUiIds.MESSAGES);
        setFitToWidth(true);
        getStyleClass().add("qip-message-scroll");
        bubbles.setFillWidth(true);
        bubbles.setPadding(new Insets(10));
        setContent(bubbles);
        setHbarPolicy(ScrollBarPolicy.NEVER);
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId == null ? "" : userId;
    }

    public void setRetryAction(Runnable retryAction) {
        this.retryAction = retryAction == null ? () -> {} : retryAction;
    }

    public void showEmptyState(String message) {
        bubbles.getChildren().clear();
        textMirror.setLength(0);
        var label = new Label(message == null || message.isBlank() ? "Выберите контакт" : message);
        label.getStyleClass().add("qip-empty-state");
        label.setWrapText(true);
        label.setMaxWidth(360);
        var box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));
        bubbles.getChildren().add(box);
    }

    public void showLoading() {
        bubbles.getChildren().clear();
        textMirror.setLength(0);
        var spinner = new ProgressIndicator();
        spinner.setId(DesktopUiIds.MESSAGES_LOADING);
        spinner.setMaxSize(28, 28);
        var label = new Label("Загрузка сообщений…");
        label.getStyleClass().add("qip-empty-state");
        var box = new VBox(8, spinner, label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));
        bubbles.getChildren().add(box);
    }

    public void showError(String message) {
        bubbles.getChildren().clear();
        textMirror.setLength(0);
        var label = new Label(message == null ? "Ошибка загрузки" : message);
        label.getStyleClass().addAll("qip-empty-state", "qip-error-state");
        label.setWrapText(true);
        var retry = new Button("Повторить");
        retry.setId(DesktopUiIds.CHAT_RETRY);
        retry.getStyleClass().add("qip-btn-send");
        retry.setOnAction(e -> retryAction.run());
        var box = new VBox(10, label, retry);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));
        bubbles.getChildren().add(box);
        textMirror.append(message).append('\n');
    }

    public void setMessages(List<MessageDto> messages) {
        bubbles.getChildren().clear();
        textMirror.setLength(0);
        if (messages == null || messages.isEmpty()) {
            showEmptyState("Начните переписку");
            textMirror.setLength(0);
            return;
        }
        for (var m : messages) {
            var line = bulletLine(m.content(), m.threadId());
            textMirror.append(line).append('\n');
            addMessageBubble(m.content(), resolveOutgoing(m), m.threadId() != null);
        }
        scrollToBottom();
    }

    public void setText(String text) {
        bubbles.getChildren().clear();
        textMirror.setLength(0);
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.startsWith("Ошибка:")) {
            showError(text);
            return;
        }
        for (var line : text.split("\n")) {
            if (!line.isBlank()) {
                appendLine(line);
            }
        }
        scrollToBottom();
    }

    public void appendText(String extra) {
        if (extra == null || extra.isBlank()) {
            return;
        }
        if (bubbles.getChildren().size() == 1) {
            var first = bubbles.getChildren().getFirst();
            if (first instanceof VBox box && !box.getChildren().isEmpty()) {
                var child = box.getChildren().getFirst();
                if (child instanceof Label label && label.getStyleClass().contains("qip-empty-state")) {
                    bubbles.getChildren().clear();
                    textMirror.setLength(0);
                }
            }
        }
        for (var line : extra.split("\n")) {
            if (!line.isBlank()) {
                appendLine(line.startsWith("\n") ? line.substring(1) : line);
            }
        }
        scrollToBottom();
    }

    public String getText() {
        return textMirror.toString();
    }

    private void appendLine(String line) {
        textMirror.append(line);
        if (!line.endsWith("\n")) {
            textMirror.append('\n');
        }
        addMessageBubble(line, false, false);
    }

    private boolean resolveOutgoing(MessageDto m) {
        if (m.senderId() == null || m.senderId().isBlank()) {
            return false;
        }
        return m.senderId().equalsIgnoreCase(currentUserId);
    }

    private static String bulletLine(String content, String threadId) {
        var line = "• " + content;
        if (threadId != null) {
            line += " [тред " + threadId + "]";
        }
        return line;
    }

    private void addMessageBubble(String text, boolean outgoing, boolean thread) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(420);
        if (outgoing) {
            label.getStyleClass().add("qip-bubble-out");
        } else if (text.startsWith("[") || text.startsWith("Ошибка")) {
            label.getStyleClass().add("qip-bubble-system");
        } else if (thread) {
            label.getStyleClass().add("qip-bubble-thread");
        } else {
            label.getStyleClass().add("qip-bubble-in");
        }
        var row = new HBox(label);
        if (outgoing) {
            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(0, spacer);
            row.setAlignment(Pos.CENTER_RIGHT);
        } else if (text.startsWith("[") || text.startsWith("Ошибка")) {
            row.setAlignment(Pos.CENTER);
        } else {
            row.setAlignment(Pos.CENTER_LEFT);
        }
        bubbles.getChildren().add(row);
    }

    private void scrollToBottom() {
        javafx.application.Platform.runLater(() -> setVvalue(1.0));
    }
}
