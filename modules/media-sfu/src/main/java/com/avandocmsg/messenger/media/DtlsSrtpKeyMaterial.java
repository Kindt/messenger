package com.avandocmsg.messenger.media;

import java.util.Arrays;

public record DtlsSrtpKeyMaterial(
    byte[] clientWriteKey,
    byte[] serverWriteKey,
    byte[] clientWriteSalt,
    byte[] serverWriteSalt
) {
    private static final int KEY_BYTES = 16;
    private static final int SALT_BYTES = 14;
    public static final int EXPORTED_BYTES = 2 * (KEY_BYTES + SALT_BYTES);

    public DtlsSrtpKeyMaterial {
        clientWriteKey = copyWithLength(clientWriteKey, KEY_BYTES, "clientWriteKey");
        serverWriteKey = copyWithLength(serverWriteKey, KEY_BYTES, "serverWriteKey");
        clientWriteSalt = copyWithLength(clientWriteSalt, SALT_BYTES, "clientWriteSalt");
        serverWriteSalt = copyWithLength(serverWriteSalt, SALT_BYTES, "serverWriteSalt");
    }

    public static DtlsSrtpKeyMaterial fromExporter(byte[] exported) {
        if (exported == null || exported.length != EXPORTED_BYTES) {
            throw new IllegalArgumentException("invalid DTLS-SRTP exporter length");
        }
        var offset = 0;
        var clientKey = Arrays.copyOfRange(exported, offset, offset += KEY_BYTES);
        var serverKey = Arrays.copyOfRange(exported, offset, offset += KEY_BYTES);
        var clientSalt = Arrays.copyOfRange(exported, offset, offset += SALT_BYTES);
        var serverSalt = Arrays.copyOfRange(exported, offset, offset + SALT_BYTES);
        return new DtlsSrtpKeyMaterial(clientKey, serverKey, clientSalt, serverSalt);
    }

    @Override
    public byte[] clientWriteKey() {
        return clientWriteKey.clone();
    }

    @Override
    public byte[] serverWriteKey() {
        return serverWriteKey.clone();
    }

    @Override
    public byte[] clientWriteSalt() {
        return clientWriteSalt.clone();
    }

    @Override
    public byte[] serverWriteSalt() {
        return serverWriteSalt.clone();
    }

    private static byte[] copyWithLength(byte[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return value.clone();
    }
}
