package com.avandocmsg.messenger.api.calls;

import com.avandocmsg.messenger.common.dto.CallSessionEvent;

@FunctionalInterface
public interface CallSessionEventPublisher {
    CallSessionEventPublisher NOOP = event -> {};

    void publish(CallSessionEvent event);
}
