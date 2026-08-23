package com.avandocmsg.messenger.desktop.ui;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** QIP-style pulsing online status orb. */
public final class QipStatusOrb extends StackPane {

    public enum Mode {
        ONLINE,
        AWAY,
        BUSY,
        OFFLINE
    }

    private final Circle core = new Circle(5);
    private final Circle pulse = new Circle(7);
    private Timeline pulseAnim;
    private Mode mode = Mode.OFFLINE;

    public QipStatusOrb() {
        getStyleClass().add("qip-status-orb");
        pulse.setMouseTransparent(true);
        pulse.setOpacity(0);
        getChildren().addAll(pulse, core);
        setAlignment(Pos.CENTER);
        setMode(Mode.OFFLINE);
    }

    public void setMode(Mode next) {
        this.mode = next;
        core.getStyleClass().removeAll(
            "qip-status-online",
            "qip-status-away",
            "qip-status-offline",
            "qip-status-busy"
        );
        pulse.getStyleClass().removeAll("qip-status-pulse-online", "qip-status-pulse-away");
        stopPulse();
        switch (next) {
            case ONLINE -> {
                core.getStyleClass().add("qip-status-online");
                pulse.getStyleClass().add("qip-status-pulse-online");
                startPulse();
            }
            case AWAY -> {
                core.getStyleClass().add("qip-status-away");
                pulse.getStyleClass().add("qip-status-pulse-away");
                startPulse();
            }
            case BUSY -> core.getStyleClass().add("qip-status-busy");
            default -> core.getStyleClass().add("qip-status-offline");
        }
    }

    public Mode mode() {
        return mode;
    }

    private void startPulse() {
        pulse.setScaleX(1);
        pulse.setScaleY(1);
        pulse.setOpacity(0.55);
        pulseAnim = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(pulse.scaleXProperty(), 1), new KeyValue(pulse.scaleYProperty(), 1)),
            new KeyFrame(Duration.seconds(1.2), new KeyValue(pulse.scaleXProperty(), 2.2), new KeyValue(pulse.scaleYProperty(), 2.2)),
            new KeyFrame(Duration.seconds(1.2), new KeyValue(pulse.opacityProperty(), 0))
        );
        pulseAnim.setCycleCount(Timeline.INDEFINITE);
        pulseAnim.play();
    }

    private void stopPulse() {
        if (pulseAnim != null) {
            pulseAnim.stop();
            pulseAnim = null;
        }
        pulse.setOpacity(0);
        pulse.setScaleX(1);
        pulse.setScaleY(1);
    }
}
