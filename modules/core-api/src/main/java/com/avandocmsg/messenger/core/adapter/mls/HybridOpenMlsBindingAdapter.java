package com.avandocmsg.messenger.core.adapter.mls;

import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;

import java.util.Base64;
import java.util.UUID;

/** Hybrid Web Crypto stub binding via {@link MlsService} (spec 020 Phase 1–2 default). */
public final class HybridOpenMlsBindingAdapter implements OpenMlsBindingPort {

    private final MlsService mlsService;

    public HybridOpenMlsBindingAdapter(MlsService mlsService) {
        this.mlsService = mlsService;
    }

    @Override
    public String wireProfile() {
        return OpenMlsWireLayout.WIRE_PROFILE;
    }

    @Override
    public String libraryVersion() {
        return OpenMlsWireLayout.LIBRARY_VERSION;
    }

    @Override
    public boolean nativeBindingAvailable() {
        return false;
    }

    @Override
    public byte[] encryptWire(UUID chatId, UUID senderId, String plaintext) {
        if (mlsService == null || chatId == null || senderId == null || plaintext == null) {
            return null;
        }
        var encrypted = mlsService.encrypt(chatId, senderId, plaintext);
        if (encrypted == null) {
            return null;
        }
        byte[] nonce;
        byte[] ciphertext;
        try {
            nonce = Base64.getDecoder().decode(encrypted.nonceBase64());
            ciphertext = Base64.getDecoder().decode(encrypted.ciphertextBase64());
        } catch (IllegalArgumentException e) {
            return null;
        }
        var combined = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
        return combined;
    }

    @Override
    public String decryptWireBase64(UUID chatId, String contentBase64) {
        if (mlsService == null || chatId == null) {
            return null;
        }
        return mlsService.decryptContentBase64(chatId, contentBase64);
    }
}
