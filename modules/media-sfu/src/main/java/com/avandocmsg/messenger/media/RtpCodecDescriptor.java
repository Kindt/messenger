package com.avandocmsg.messenger.media;

import java.util.Locale;

public record RtpCodecDescriptor(
    int payloadType,
    String name,
    int clockRate,
    int channels,
    String fmtp
) {
    public RtpCodecDescriptor {
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("payloadType must be between 0 and 127");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("codec name required");
        }
        if (clockRate < 1) {
            throw new IllegalArgumentException("clockRate must be positive");
        }
        if (channels < 1) {
            throw new IllegalArgumentException("channels must be positive");
        }
        name = name.trim().toUpperCase(Locale.ROOT);
        fmtp = fmtp == null || fmtp.isBlank() ? null : fmtp.trim();
    }

    public static RtpCodecDescriptor pcmu() {
        return new RtpCodecDescriptor(0, "PCMU", 8_000, 1, null);
    }

    public boolean isPcmuPtZero() {
        return payloadType == 0
            && name.equals("PCMU")
            && clockRate == 8_000
            && channels == 1;
    }
}
