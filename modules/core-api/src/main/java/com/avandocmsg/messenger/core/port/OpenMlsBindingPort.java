package com.avandocmsg.messenger.core.port;

import java.util.UUID;

/** SPI for OpenMLS binding (spec 020): wire metadata + encrypt/decrypt for interop. */
public interface OpenMlsBindingPort {

    String wireProfile();

    String libraryVersion();

    /** {@code true} when a native OpenMLS JNI/FFI library is loaded; {@code false} for hybrid stub. */
    boolean nativeBindingAvailable();

    /** Combined nonce+ciphertext for wire interop; {@code null} on failure. */
    byte[] encryptWire(UUID chatId, UUID senderId, String plaintext);

    /** Decrypt Base64 combined blob from {@code messages.content}; {@code null} on failure. */
    String decryptWireBase64(UUID chatId, String contentBase64);
}
