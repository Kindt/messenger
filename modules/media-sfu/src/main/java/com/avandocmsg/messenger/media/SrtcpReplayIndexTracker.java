package com.avandocmsg.messenger.media;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class SrtcpReplayIndexTracker {

    private final Map<Long, AtomicInteger> highestIndexes = new ConcurrentHashMap<>();

    boolean accept(long senderSsrc, int index) {
        if (index < 0) {
            return false;
        }
        var highest = highestIndexes.computeIfAbsent(senderSsrc, ignored -> new AtomicInteger(-1));
        while (true) {
            var previous = highest.get();
            if (index <= previous) {
                return false;
            }
            if (highest.compareAndSet(previous, index)) {
                return true;
            }
        }
    }
}
