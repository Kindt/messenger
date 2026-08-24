package com.avandocmsg.messenger.media;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.Objects;

public final class WebRtcSdpAnswerBuilder {

    private final String iceUfrag;
    private final String icePassword;
    private final String fingerprint;
    private final InetSocketAddress candidate;

    public WebRtcSdpAnswerBuilder(
        String iceUfrag,
        String icePassword,
        String fingerprint,
        InetSocketAddress candidate
    ) {
        this.iceUfrag = required(iceUfrag, "iceUfrag");
        this.icePassword = required(icePassword, "icePassword");
        this.fingerprint = required(fingerprint, "fingerprint");
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        if (!(candidate.getAddress() instanceof Inet4Address)) {
            throw new IllegalArgumentException("IPv4 media candidate required");
        }
    }

    public String answer(WebRtcSdpOffer offer) {
        return answer(offer, WebRtcSdpAnswerPolicy.OFFERED_MEDIA);
    }

    public String answer(WebRtcSdpOffer offer, WebRtcSdpAnswerPolicy policy) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(policy, "policy");
        var answer = new StringBuilder(2048);
        answer.append("v=0\r\n");
        answer.append("o=korus 1 1 IN IP4 ").append(candidate.getAddress().getHostAddress()).append("\r\n");
        answer.append("s=Korus Calls\r\n");
        answer.append("t=0 0\r\n");
        answer.append("a=ice-lite\r\n");
        answer.append("a=group:BUNDLE ");
        for (var i = 0; i < offer.media().size(); i++) {
            if (i > 0) answer.append(' ');
            answer.append(offer.media().get(i).mid());
        }
        answer.append("\r\n");
        answer.append("a=msid-semantic: WMS korus\r\n");
        for (var media : offer.media()) {
            appendMedia(answer, media, policy);
        }
        return answer.toString();
    }

    private void appendMedia(
        StringBuilder answer,
        WebRtcSdpOffer.MediaSection media,
        WebRtcSdpAnswerPolicy policy
    ) {
        var directPcmu = policy == WebRtcSdpAnswerPolicy.DIRECT_PCMU_AUDIO;
        var supported = directPcmu
            ? media.type().equals("audio") && media.payloadTypes().contains("0")
            : media.type().equals("audio") || media.type().equals("video");
        var payloadTypes = directPcmu && supported ? java.util.List.of("0") : media.payloadTypes();
        answer.append("m=").append(media.type()).append(supported ? " 9 " : " 0 ")
            .append(media.protocol());
        for (var payloadType : payloadTypes) {
            answer.append(' ').append(payloadType);
        }
        answer.append("\r\n");
        answer.append("c=IN IP4 0.0.0.0\r\n");
        answer.append("a=mid:").append(media.mid()).append("\r\n");
        if (!supported) {
            answer.append("a=inactive\r\n");
            return;
        }
        answer.append("a=rtcp:9 IN IP4 0.0.0.0\r\n");
        answer.append("a=ice-ufrag:").append(iceUfrag).append("\r\n");
        answer.append("a=ice-pwd:").append(icePassword).append("\r\n");
        answer.append("a=ice-options:trickle\r\n");
        answer.append("a=fingerprint:sha-256 ").append(fingerprint).append("\r\n");
        answer.append("a=setup:passive\r\n");
        answer.append("a=").append(answerDirection(media.direction())).append("\r\n");
        answer.append("a=rtcp-mux\r\n");
        answer.append("a=rtcp-rsize\r\n");
        for (var attribute : media.attributes()) {
            if (!directPcmu || isPcmuAttribute(attribute)) {
                answer.append(attribute).append("\r\n");
            }
        }
        answer.append("a=candidate:korus1 1 udp 2130706431 ")
            .append(candidate.getAddress().getHostAddress()).append(' ')
            .append(candidate.getPort()).append(" typ host generation 0\r\n");
        answer.append("a=end-of-candidates\r\n");
    }

    private static boolean isPcmuAttribute(String attribute) {
        return attribute.startsWith("a=rtpmap:0 ")
            || attribute.startsWith("a=fmtp:0 ")
            || attribute.startsWith("a=rtcp-fb:0 ")
            || attribute.startsWith("a=extmap:")
            || attribute.equals("a=extmap-allow-mixed");
    }

    private static String answerDirection(String offerDirection) {
        return switch (offerDirection) {
            case "sendonly" -> "recvonly";
            case "recvonly" -> "sendonly";
            case "inactive" -> "inactive";
            default -> "sendrecv";
        };
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        return value;
    }
}
