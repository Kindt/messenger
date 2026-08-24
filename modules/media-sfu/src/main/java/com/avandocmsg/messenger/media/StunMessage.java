package com.avandocmsg.messenger.media;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class StunMessage {

    public static final int BINDING_REQUEST = 0x0001;
    public static final int BINDING_SUCCESS = 0x0101;
    private static final int HEADER_BYTES = 20;
    private static final long MAGIC_COOKIE = 0x2112a442L;
    private static final int ATTR_USERNAME = 0x0006;
    private static final int ATTR_MESSAGE_INTEGRITY = 0x0008;
    private static final int ATTR_XOR_MAPPED_ADDRESS = 0x0020;
    private static final int ATTR_PRIORITY = 0x0024;
    private static final int ATTR_USE_CANDIDATE = 0x0025;
    private static final int ATTR_SOFTWARE = 0x8022;
    private static final int ATTR_FINGERPRINT = 0x8028;
    private static final long FINGERPRINT_XOR = 0x5354554eL;

    private final byte[] wire;
    private final int type;
    private final byte[] transactionId;
    private final List<Attribute> attributes;

    private StunMessage(byte[] wire, int type, byte[] transactionId, List<Attribute> attributes) {
        this.wire = wire;
        this.type = type;
        this.transactionId = transactionId;
        this.attributes = attributes;
    }

    public static boolean looksLike(byte[] packet, int length) {
        return packet != null
            && length >= HEADER_BYTES
            && (packet[0] & 0xc0) == 0
            && unsignedInt(packet, 4) == MAGIC_COOKIE;
    }

    public static StunMessage parse(byte[] source) {
        if (source == null || source.length < HEADER_BYTES) {
            throw new IllegalArgumentException("STUN message shorter than header");
        }
        var wire = source.clone();
        var type = unsignedShort(wire, 0);
        if ((type & 0xc000) != 0) {
            throw new IllegalArgumentException("invalid STUN message type");
        }
        if (unsignedInt(wire, 4) != MAGIC_COOKIE) {
            throw new IllegalArgumentException("invalid STUN magic cookie");
        }
        var bodyLength = unsignedShort(wire, 2);
        if ((bodyLength & 3) != 0 || HEADER_BYTES + bodyLength != wire.length) {
            throw new IllegalArgumentException("invalid STUN message length");
        }
        var attributes = new ArrayList<Attribute>();
        var offset = HEADER_BYTES;
        while (offset < wire.length) {
            if (offset + 4 > wire.length) {
                throw new IllegalArgumentException("truncated STUN attribute");
            }
            var attributeType = unsignedShort(wire, offset);
            var length = unsignedShort(wire, offset + 2);
            var valueOffset = offset + 4;
            var paddedLength = (length + 3) & ~3;
            if (valueOffset + paddedLength > wire.length) {
                throw new IllegalArgumentException("truncated STUN attribute value");
            }
            attributes.add(new Attribute(
                attributeType,
                offset,
                Arrays.copyOfRange(wire, valueOffset, valueOffset + length)
            ));
            offset = valueOffset + paddedLength;
        }
        return new StunMessage(
            wire,
            type,
            Arrays.copyOfRange(wire, 8, 20),
            List.copyOf(attributes)
        );
    }

    public int type() {
        return type;
    }

    public String username() {
        return attribute(ATTR_USERNAME)
            .map(attribute -> new String(attribute.value(), StandardCharsets.UTF_8))
            .orElse(null);
    }

    public boolean useCandidate() {
        return attribute(ATTR_USE_CANDIDATE).isPresent();
    }

    public boolean verifyMessageIntegrity(String password) {
        var integrity = attribute(ATTR_MESSAGE_INTEGRITY).orElse(null);
        if (integrity == null || integrity.value().length != 20) {
            return false;
        }
        var signed = Arrays.copyOf(wire, integrity.offset());
        setLength(signed, integrity.offset() + 24 - HEADER_BYTES);
        var actual = hmacSha1(password, signed);
        return MessageDigest.isEqual(integrity.value(), actual);
    }

    public boolean verifyFingerprint() {
        var fingerprint = attribute(ATTR_FINGERPRINT).orElse(null);
        if (fingerprint == null || fingerprint.value().length != 4) {
            return false;
        }
        var crc = new CRC32();
        crc.update(wire, 0, fingerprint.offset());
        var expected = unsignedInt(fingerprint.value(), 0);
        return expected == ((crc.getValue() ^ FINGERPRINT_XOR) & 0xffffffffL);
    }

    public static byte[] bindingRequest(byte[] transactionId, String username, String password) {
        if (transactionId == null || transactionId.length != 12) {
            throw new IllegalArgumentException("STUN transaction id must be 12 bytes");
        }
        required(username, "username");
        required(password, "password");
        var output = new ByteArrayOutputStream(128);
        writeShort(output, BINDING_REQUEST);
        writeShort(output, 0);
        writeInt(output, MAGIC_COOKIE);
        output.writeBytes(transactionId);
        writeAttribute(output, ATTR_USERNAME, username.getBytes(StandardCharsets.UTF_8), (byte) 0x20);
        writeAttribute(output, ATTR_PRIORITY, priorityBytes(), (byte) 0);
        writeAttribute(output, ATTR_USE_CANDIDATE, new byte[0], (byte) 0);

        var signed = output.toByteArray();
        setLength(signed, signed.length + 24 - HEADER_BYTES);
        output.reset();
        output.writeBytes(signed);
        writeAttribute(output, ATTR_MESSAGE_INTEGRITY, hmacSha1(password, signed), (byte) 0);

        var fingerprintInput = output.toByteArray();
        setLength(fingerprintInput, fingerprintInput.length + 8 - HEADER_BYTES);
        var crc = new CRC32();
        crc.update(fingerprintInput);
        output.reset();
        output.writeBytes(fingerprintInput);
        var fingerprint = new ByteArrayOutputStream(4);
        writeInt(fingerprint, crc.getValue() ^ FINGERPRINT_XOR);
        writeAttribute(output, ATTR_FINGERPRINT, fingerprint.toByteArray(), (byte) 0);
        return output.toByteArray();
    }

    public byte[] bindingSuccess(InetSocketAddress mappedAddress, String password, String software) {
        if (type != BINDING_REQUEST) {
            throw new IllegalStateException("binding success requires binding request");
        }
        if (!(mappedAddress.getAddress() instanceof Inet4Address)) {
            throw new IllegalArgumentException("only IPv4 mapped addresses are supported");
        }
        var output = new ByteArrayOutputStream(128);
        writeShort(output, BINDING_SUCCESS);
        writeShort(output, 0);
        writeInt(output, MAGIC_COOKIE);
        output.writeBytes(transactionId);
        writeAttribute(output, ATTR_SOFTWARE, software.getBytes(StandardCharsets.UTF_8), (byte) 0x20);
        writeAttribute(output, ATTR_XOR_MAPPED_ADDRESS, xorMappedIpv4(mappedAddress), (byte) 0);

        var signed = output.toByteArray();
        setLength(signed, signed.length + 24 - HEADER_BYTES);
        output.reset();
        output.writeBytes(signed);
        writeAttribute(output, ATTR_MESSAGE_INTEGRITY, hmacSha1(password, signed), (byte) 0);

        var fingerprintInput = output.toByteArray();
        setLength(fingerprintInput, fingerprintInput.length + 8 - HEADER_BYTES);
        var crc = new CRC32();
        crc.update(fingerprintInput);
        output.reset();
        output.writeBytes(fingerprintInput);
        var fingerprint = new ByteArrayOutputStream(4);
        writeInt(fingerprint, crc.getValue() ^ FINGERPRINT_XOR);
        writeAttribute(output, ATTR_FINGERPRINT, fingerprint.toByteArray(), (byte) 0);
        return output.toByteArray();
    }

    private java.util.Optional<Attribute> attribute(int attributeType) {
        return attributes.stream().filter(attribute -> attribute.type() == attributeType).findFirst();
    }

    private static byte[] priorityBytes() {
        var value = new byte[4];
        writeIntTo(value, 0, 0x6e0001ffL);
        return value;
    }

    private static void writeIntTo(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
    }

    private static byte[] xorMappedIpv4(InetSocketAddress mappedAddress) {
        var value = new byte[8];
        value[1] = 0x01;
        var xorPort = mappedAddress.getPort() ^ (int) (MAGIC_COOKIE >>> 16);
        value[2] = (byte) (xorPort >>> 8);
        value[3] = (byte) xorPort;
        var address = mappedAddress.getAddress().getAddress();
        for (var i = 0; i < 4; i++) {
            value[4 + i] = (byte) (address[i] ^ (byte) (MAGIC_COOKIE >>> (24 - i * 8)));
        }
        return value;
    }

    private static byte[] hmacSha1(String password, byte[] message) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA1 unavailable", error);
        }
    }

    private static void writeAttribute(
        ByteArrayOutputStream output,
        int type,
        byte[] value,
        byte padding
    ) {
        writeShort(output, type);
        writeShort(output, value.length);
        output.writeBytes(value);
        while ((output.size() & 3) != 0) {
            output.write(padding);
        }
    }

    private static void setLength(byte[] message, int bodyLength) {
        message[2] = (byte) (bodyLength >>> 8);
        message[3] = (byte) bodyLength;
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value >>> 8);
        output.write(value);
    }

    private static void writeInt(ByteArrayOutputStream output, long value) {
        output.write((int) (value >>> 24));
        output.write((int) (value >>> 16));
        output.write((int) (value >>> 8));
        output.write((int) value);
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

    private record Attribute(int type, int offset, byte[] value) {}
}
