package com.avandocmsg.messenger.media;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.regex.Pattern;

public record WebRtcSdpAnswer(
    String iceUfrag,
    String icePassword,
    String fingerprint,
    InetSocketAddress candidate
) {
    private static final Pattern CANDIDATE = Pattern.compile(
        "^a=candidate:\\S+ \\d+ \\S+ \\d+ ([0-9.]+) (\\d+) typ host"
    );

    public WebRtcSdpAnswer {
        Objects.requireNonNull(iceUfrag, "iceUfrag");
        Objects.requireNonNull(icePassword, "icePassword");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(candidate, "candidate");
    }

    public static WebRtcSdpAnswer parse(String sdp) {
        if (sdp == null || sdp.isBlank()) {
            throw new IllegalArgumentException("SDP answer required");
        }
        String ufrag = null;
        String password = null;
        String fingerprint = null;
        InetSocketAddress candidate = null;
        for (var rawLine : sdp.split("\\r?\\n")) {
            var line = rawLine.strip();
            if (line.startsWith("a=ice-ufrag:")) {
                ufrag = line.substring("a=ice-ufrag:".length());
            } else if (line.startsWith("a=ice-pwd:")) {
                password = line.substring("a=ice-pwd:".length());
            } else if (line.startsWith("a=fingerprint:")) {
                fingerprint = line.substring("a=fingerprint:".length());
            } else if (line.startsWith("a=candidate:")) {
                var match = CANDIDATE.matcher(line);
                if (match.find()) {
                    candidate = new InetSocketAddress(match.group(1), Integer.parseInt(match.group(2)));
                }
            }
        }
        if (ufrag == null || password == null || fingerprint == null || candidate == null) {
            throw new IllegalArgumentException("SDP answer is missing ICE credentials or host candidate");
        }
        return new WebRtcSdpAnswer(ufrag, password, fingerprint, candidate);
    }
}
