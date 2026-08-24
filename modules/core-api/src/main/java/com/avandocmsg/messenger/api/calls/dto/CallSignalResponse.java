package com.avandocmsg.messenger.api.calls.dto;

import com.avandocmsg.messenger.media.MediaSignal;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Locale;

public record CallSignalResponse(
    String id,
    String type,
    String sdp,
    String candidate,
    @JsonProperty("error_code") String errorCode,
    Instant createdAt,
    String participantId
) {
    public static CallSignalResponse from(MediaSignal signal) {
        return new CallSignalResponse(
            signal.signalId().toString(),
            signal.type().name().toLowerCase(Locale.ROOT),
            signal.sdp(),
            signal.candidate(),
            signal.errorCode(),
            signal.createdAt(),
            signal.participantId().toString()
        );
    }
}
