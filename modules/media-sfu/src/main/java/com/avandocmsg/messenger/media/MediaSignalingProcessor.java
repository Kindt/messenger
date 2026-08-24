package com.avandocmsg.messenger.media;

import java.util.UUID;

@FunctionalInterface
public interface MediaSignalingProcessor {
    MediaSignalingProcessor NOOP = sessionId -> {};

    void processPending(UUID sessionId);

    default void endSession(UUID sessionId) {}
}
