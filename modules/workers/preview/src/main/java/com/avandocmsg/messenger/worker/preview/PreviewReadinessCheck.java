package com.avandocmsg.messenger.worker.preview;

@FunctionalInterface
interface PreviewReadinessCheck {
    boolean ready();
}
