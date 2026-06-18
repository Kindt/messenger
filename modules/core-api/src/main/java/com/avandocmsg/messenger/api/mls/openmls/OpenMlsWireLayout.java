package com.avandocmsg.messenger.api.mls.openmls;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Hybrid OpenMLS wire layout (spec 020 Phase 1): AES-GCM nonce+ciphertext in one Base64 blob,
 * AAD {@code chatId:epoch}. Matches browser {@code korus-mls-wasm.js} until real OpenMLS ships.
 */
public final class OpenMlsWireLayout {

    public static final String WIRE_PROFILE = "openmls-stub-v1";
    public static final String LIBRARY_VERSION = "hybrid-webcrypto-stub";
    public static final String DEFAULT_CIPHER_SUITE = "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519";
    public static final int NONCE_BYTES = 12;
    public static final int GCM_TAG_BYTES = 16;
    public static final int MIN_COMBINED_BYTES = NONCE_BYTES + GCM_TAG_BYTES;

    private OpenMlsWireLayout() {
    }

    public static byte[] aadBytes(UUID chatId, long epoch) {
        return (chatId.toString() + ":" + epoch).getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isValidCombined(byte[] combined) {
        return combined != null && combined.length >= MIN_COMBINED_BYTES;
    }

    public static boolean isDefaultCipherSuite(String cipherSuite) {
        return cipherSuite != null && DEFAULT_CIPHER_SUITE.equals(cipherSuite);
    }
}
