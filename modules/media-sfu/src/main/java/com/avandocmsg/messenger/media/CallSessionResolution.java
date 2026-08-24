package com.avandocmsg.messenger.media;

import java.util.Objects;

public record CallSessionResolution(CallSession session, boolean created) {
    public CallSessionResolution {
        Objects.requireNonNull(session, "session");
    }
}
