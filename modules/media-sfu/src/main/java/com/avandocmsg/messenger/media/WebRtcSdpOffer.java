package com.avandocmsg.messenger.media;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public record WebRtcSdpOffer(
    String iceUfrag,
    String icePassword,
    String fingerprint,
    List<MediaSection> media
) {
    public static WebRtcSdpOffer parse(String sdp) {
        if (sdp == null || sdp.isBlank()) {
            throw new IllegalArgumentException("SDP offer required");
        }
        String sessionUfrag = null;
        String sessionPassword = null;
        String sessionFingerprint = null;
        String sessionDirection = "sendrecv";
        var media = new ArrayList<MutableMedia>();
        MutableMedia current = null;
        for (var rawLine : sdp.split("\\r?\\n")) {
            var line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("m=")) {
                var fields = line.substring(2).split(" +");
                if (fields.length < 4) {
                    throw new IllegalArgumentException("invalid SDP media line");
                }
                current = new MutableMedia(
                    fields[0],
                    fields[2],
                    List.of(fields).subList(3, fields.length),
                    Integer.toString(media.size())
                );
                media.add(current);
                continue;
            }
            if (line.startsWith("a=ice-ufrag:")) {
                var value = line.substring("a=ice-ufrag:".length());
                if (current == null) sessionUfrag = value; else current.iceUfrag = value;
            } else if (line.startsWith("a=ice-pwd:")) {
                var value = line.substring("a=ice-pwd:".length());
                if (current == null) sessionPassword = value; else current.icePassword = value;
            } else if (line.startsWith("a=fingerprint:")) {
                var value = line.substring("a=fingerprint:".length());
                if (current == null) sessionFingerprint = value; else current.fingerprint = value;
            } else if (isDirection(line)) {
                var value = line.substring("a=".length());
                if (current == null) sessionDirection = value; else current.direction = value;
            } else if (current != null && line.startsWith("a=mid:")) {
                current.mid = line.substring("a=mid:".length());
            } else if (current != null && copyToAnswer(line)) {
                current.attributes.add(line);
            }
        }
        if (media.isEmpty()) {
            throw new IllegalArgumentException("SDP offer has no media");
        }
        var resolvedUfrag = sessionUfrag != null ? sessionUfrag : media.getFirst().iceUfrag;
        var resolvedPassword = sessionPassword != null ? sessionPassword : media.getFirst().icePassword;
        var resolvedFingerprint = sessionFingerprint != null
            ? sessionFingerprint
            : media.getFirst().fingerprint;
        if (resolvedUfrag == null || resolvedPassword == null || resolvedFingerprint == null) {
            throw new IllegalArgumentException("SDP ICE credentials and fingerprint required");
        }
        var resolvedDirection = sessionDirection;
        return new WebRtcSdpOffer(
            resolvedUfrag,
            resolvedPassword,
            resolvedFingerprint,
            media.stream().map(item -> item.freeze(resolvedDirection)).toList()
        );
    }

    private static boolean isDirection(String line) {
        return line.equals("a=sendrecv")
            || line.equals("a=sendonly")
            || line.equals("a=recvonly")
            || line.equals("a=inactive");
    }

    private static boolean copyToAnswer(String line) {
        return line.startsWith("a=rtpmap:")
            || line.startsWith("a=fmtp:")
            || line.startsWith("a=rtcp-fb:")
            || line.startsWith("a=extmap:")
            || line.equals("a=extmap-allow-mixed");
    }

    public record MediaSection(
        String type,
        String protocol,
        List<String> payloadTypes,
        String mid,
        String direction,
        List<String> attributes
    ) {
        public List<RtpCodecDescriptor> codecs() {
            var rtpMaps = new LinkedHashMap<Integer, CodecMapping>();
            var formatParameters = new LinkedHashMap<Integer, String>();
            for (var attribute : attributes) {
                if (attribute.startsWith("a=rtpmap:")) {
                    parseRtpMap(attribute).ifPresent(mapping -> rtpMaps.put(mapping.payloadType(), mapping));
                } else if (attribute.startsWith("a=fmtp:")) {
                    parseFormatParameters(attribute)
                        .ifPresent(fmtp -> formatParameters.put(fmtp.payloadType(), fmtp.value()));
                }
            }
            var codecs = new ArrayList<RtpCodecDescriptor>();
            for (var rawPayloadType : payloadTypes) {
                var payloadType = parsePayloadType(rawPayloadType);
                if (payloadType == null) {
                    continue;
                }
                var mapping = rtpMaps.get(payloadType);
                if (mapping == null) {
                    mapping = staticMapping(payloadType);
                }
                if (mapping != null) {
                    codecs.add(new RtpCodecDescriptor(
                        payloadType,
                        mapping.name(),
                        mapping.clockRate(),
                        mapping.channels(),
                        formatParameters.get(payloadType)
                    ));
                }
            }
            return List.copyOf(codecs);
        }
    }

    private static java.util.Optional<CodecMapping> parseRtpMap(String attribute) {
        var value = attribute.substring("a=rtpmap:".length()).trim();
        var separator = value.indexOf(' ');
        if (separator < 1 || separator == value.length() - 1) {
            return java.util.Optional.empty();
        }
        var payloadType = parsePayloadType(value.substring(0, separator));
        var encoding = value.substring(separator + 1).trim().split("/");
        if (payloadType == null || encoding.length < 2 || encoding.length > 3) {
            return java.util.Optional.empty();
        }
        try {
            var clockRate = Integer.parseInt(encoding[1]);
            var channels = encoding.length == 3 ? Integer.parseInt(encoding[2]) : 1;
            return java.util.Optional.of(new CodecMapping(
                payloadType,
                encoding[0],
                clockRate,
                channels
            ));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<FormatParameters> parseFormatParameters(String attribute) {
        var value = attribute.substring("a=fmtp:".length()).trim();
        var separator = value.indexOf(' ');
        if (separator < 1 || separator == value.length() - 1) {
            return java.util.Optional.empty();
        }
        var payloadType = parsePayloadType(value.substring(0, separator));
        if (payloadType == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new FormatParameters(
            payloadType,
            value.substring(separator + 1).trim()
        ));
    }

    private static Integer parsePayloadType(String value) {
        try {
            var payloadType = Integer.parseInt(value);
            return payloadType >= 0 && payloadType <= 127 ? payloadType : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static CodecMapping staticMapping(int payloadType) {
        return switch (payloadType) {
            case 0 -> new CodecMapping(0, "PCMU", 8_000, 1);
            case 8 -> new CodecMapping(8, "PCMA", 8_000, 1);
            case 9 -> new CodecMapping(9, "G722", 8_000, 1);
            case 13 -> new CodecMapping(13, "CN", 8_000, 1);
            default -> null;
        };
    }

    private record CodecMapping(int payloadType, String name, int clockRate, int channels) {}

    private record FormatParameters(int payloadType, String value) {}

    private static final class MutableMedia {
        private final String type;
        private final String protocol;
        private final List<String> payloadTypes;
        private final List<String> attributes = new ArrayList<>();
        private String mid;
        private String iceUfrag;
        private String icePassword;
        private String fingerprint;
        private String direction;

        private MutableMedia(String type, String protocol, List<String> payloadTypes, String mid) {
            this.type = type;
            this.protocol = protocol;
            this.payloadTypes = List.copyOf(payloadTypes);
            this.mid = mid;
        }

        private MediaSection freeze(String defaultDirection) {
            return new MediaSection(
                type,
                protocol,
                payloadTypes,
                mid,
                direction != null ? direction : defaultDirection,
                List.copyOf(attributes)
            );
        }
    }
}
