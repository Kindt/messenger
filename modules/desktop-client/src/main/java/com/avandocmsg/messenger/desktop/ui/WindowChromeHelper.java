package com.avandocmsg.messenger.desktop.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class WindowChromeHelper {

    private WindowChromeHelper() {}

    public static void applyUndecorated(Stage stage) {
        if ("false".equalsIgnoreCase(System.getProperty("korus.desktop.undecorated", "true"))) {
            return;
        }
        if (!stage.isShowing()) {
            stage.initStyle(StageStyle.UNDECORATED);
        }
    }

    public static void wireDrag(Stage stage, Node dragRegion) {
        final double[] offset = new double[2];
        dragRegion.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            offset[0] = e.getScreenX() - stage.getX();
            offset[1] = e.getScreenY() - stage.getY();
        });
        dragRegion.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            stage.setX(e.getScreenX() - offset[0]);
            stage.setY(e.getScreenY() - offset[1]);
        });
    }

    public static Button minimizeButton(Stage stage) {
        var b = chromeBtn("—");
        b.setOnAction(e -> stage.setIconified(true));
        return b;
    }

    public static Button maximizeButton(Stage stage) {
        var b = chromeBtn("□");
        b.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        return b;
    }

    public static Button closeButton(Stage stage) {
        var b = chromeBtn("✕");
        b.getStyleClass().add("qip-win-close");
        b.setOnAction(e -> stage.close());
        return b;
    }

    private static Button chromeBtn(String text) {
        var b = new Button(text);
        b.getStyleClass().add("qip-win-btn");
        return b;
    }
}
