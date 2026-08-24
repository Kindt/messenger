package com.avandocmsg.messenger.desktop.ui;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

/** Icon-only controls (Telegram / WhatsApp desktop patterns). */
public final class DesktopUiIcons {

    private DesktopUiIcons() {}

    public static Button button(String icon, String tooltip) {
        return button(icon, tooltip, "qip-btn-icon");
    }

    public static Button button(String icon, String tooltip, String styleClass) {
        var btn = new Button(icon);
        btn.getStyleClass().add(styleClass);
        if (tooltip != null && !tooltip.isBlank()) {
            btn.setTooltip(new Tooltip(tooltip));
            btn.setAccessibleText(tooltip);
        }
        return btn;
    }

    public static Tab tab(String id, String icon, String tooltip, Parent content) {
        var tab = new Tab();
        tab.setId(id);
        tab.setClosable(false);
        tab.setText("");
        var graphic = new Label(icon);
        graphic.getStyleClass().add("qip-tab-icon");
        tab.setGraphic(graphic);
        if (tooltip != null && !tooltip.isBlank()) {
            tab.setTooltip(new Tooltip(tooltip));
            graphic.setAccessibleText(tooltip);
        }
        tab.setContent(content);
        return tab;
    }
}
