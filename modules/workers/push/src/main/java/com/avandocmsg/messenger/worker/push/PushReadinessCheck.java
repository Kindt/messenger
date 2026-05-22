package com.avandocmsg.messenger.worker.push;

@FunctionalInterface
interface PushReadinessCheck {
    boolean ready();
}
