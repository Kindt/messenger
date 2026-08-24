package com.avandocmsg.messenger.media;

import java.util.Arrays;

public final class RtpPacket {

    private static final int FIXED_HEADER_BYTES = 12;

    private final boolean marker;
    private final int payloadType;
    private final int sequenceNumber;
    private final long timestamp;
    private final long ssrc;
    private final byte[] payload;
    private final byte[] wireBytes;

    private RtpPacket(
        boolean marker,
        int payloadType,
        int sequenceNumber,
        long timestamp,
        long ssrc,
        byte[] payload,
        byte[] wireBytes
    ) {
        this.marker = marker;
        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;
        this.wireBytes = wireBytes;
    }

    public static RtpPacket of(
        int payloadType,
        int sequenceNumber,
        long timestamp,
        long ssrc,
        byte[] payload
    ) {
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("payloadType must be between 0 and 127");
        }
        if (sequenceNumber < 0 || sequenceNumber > 0xffff) {
            throw new IllegalArgumentException("sequenceNumber must be between 0 and 65535");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload required");
        }
        var wire = new byte[FIXED_HEADER_BYTES + payload.length];
        wire[0] = (byte) 0x80;
        wire[1] = (byte) payloadType;
        wire[2] = (byte) (sequenceNumber >>> 8);
        wire[3] = (byte) sequenceNumber;
        writeInt(wire, 4, timestamp);
        writeInt(wire, 8, ssrc);
        System.arraycopy(payload, 0, wire, FIXED_HEADER_BYTES, payload.length);
        return new RtpPacket(false, payloadType, sequenceNumber, timestamp, ssrc, payload.clone(), wire);
    }

    public static RtpPacket parse(byte[] source) {
        if (source == null || source.length < FIXED_HEADER_BYTES) {
            throw new IllegalArgumentException("RTP packet shorter than fixed header");
        }
        var wire = source.clone();
        var first = Byte.toUnsignedInt(wire[0]);
        if ((first >>> 6) != 2) {
            throw new IllegalArgumentException("unsupported RTP version");
        }
        var padding = (first & 0x20) != 0;
        var extension = (first & 0x10) != 0;
        var csrcCount = first & 0x0f;
        var headerLength = FIXED_HEADER_BYTES + csrcCount * 4;
        if (headerLength > wire.length) {
            throw new IllegalArgumentException("truncated RTP CSRC list");
        }
        if (extension) {
            if (headerLength + 4 > wire.length) {
                throw new IllegalArgumentException("truncated RTP extension");
            }
            var extensionWords = unsignedShort(wire, headerLength + 2);
            headerLength += 4 + extensionWords * 4;
            if (headerLength > wire.length) {
                throw new IllegalArgumentException("truncated RTP extension payload");
            }
        }
        var paddingLength = padding ? Byte.toUnsignedInt(wire[wire.length - 1]) : 0;
        if (paddingLength > wire.length - headerLength) {
            throw new IllegalArgumentException("invalid RTP padding");
        }
        var payloadEnd = wire.length - paddingLength;
        return new RtpPacket(
            (Byte.toUnsignedInt(wire[1]) & 0x80) != 0,
            Byte.toUnsignedInt(wire[1]) & 0x7f,
            unsignedShort(wire, 2),
            unsignedInt(wire, 4),
            unsignedInt(wire, 8),
            Arrays.copyOfRange(wire, headerLength, payloadEnd),
            wire
        );
    }

    public boolean marker() {
        return marker;
    }

    public int payloadType() {
        return payloadType;
    }

    public int sequenceNumber() {
        return sequenceNumber;
    }

    public long timestamp() {
        return timestamp;
    }

    public long ssrc() {
        return ssrc;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public byte[] wireBytes() {
        return wireBytes.clone();
    }

    private static void writeInt(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) Byte.toUnsignedInt(bytes[offset]) << 24)
            | ((long) Byte.toUnsignedInt(bytes[offset + 1]) << 16)
            | ((long) Byte.toUnsignedInt(bytes[offset + 2]) << 8)
            | Byte.toUnsignedInt(bytes[offset + 3]);
    }
}
