package com.avandocmsg.messenger.media;

import java.util.Objects;
import java.util.Optional;

public record MediaNegotiationResult(
    RtpCodecDescriptor codec,
    MediaErrorCode error
) {
    public MediaNegotiationResult {
        if ((codec == null) == (error == null)) {
            throw new IllegalArgumentException("exactly one of codec or error is required");
        }
    }

    public static MediaNegotiationResult accepted(RtpCodecDescriptor codec) {
        return new MediaNegotiationResult(Objects.requireNonNull(codec, "codec"), null);
    }

    public static MediaNegotiationResult rejected(MediaErrorCode error) {
        return new MediaNegotiationResult(null, Objects.requireNonNull(error, "error"));
    }

    public boolean accepted() {
        return codec != null;
    }

    public Optional<RtpCodecDescriptor> selectedCodec() {
        return Optional.ofNullable(codec);
    }
}
