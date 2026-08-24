package com.avandocmsg.messenger.media;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;

public final class MediaSfuApplication {

    private MediaSfuApplication() {}

    public static void main(String[] args) throws Exception {
        var config = MediaSfuConfiguration.fromEnvironment();
        if (config.mode() != MediaSfuMode.STANDALONE) {
            throw new IllegalStateException("MEDIA_SFU_MODE=standalone is required for the media-sfu process");
        }
        var rooms = new InMemoryMediaRoomService(Clock.systemUTC(), config.idleTimeout(), config.nodeId());
        var server = new MediaSfuHttpServer(config.port(), config.nodeId(), rooms);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "media-sfu-shutdown"));
        server.start();
        new CountDownLatch(1).await();
    }
}
