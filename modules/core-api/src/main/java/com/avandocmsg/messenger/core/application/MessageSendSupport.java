package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.mls.MlsMessageTypes;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;

import java.util.Base64;
import java.util.UUID;

/** Pure helpers for message send path (hex Phase 2b+). */
public final class MessageSendSupport {

    private static final String E2EE_PREFIX = "e2ee-";

    private MessageSendSupport() {
    }

    public static boolean usesMlsScheme(SendMessageRequest request) {
        return request != null
            && request.e2eeScheme() != null
            && MlsMessageTypes.SCHEME_MLS.equalsIgnoreCase(request.e2eeScheme());
    }

    public static boolean shouldServerEncrypt(SendMessageRequest request) {
        if (request == null) {
            return true;
        }
        if (request.e2eeScheme() == null || request.e2eeScheme().isBlank()) {
            return true;
        }
        if (MlsMessageTypes.SCHEME_LEGACY.equalsIgnoreCase(request.e2eeScheme())) {
            return false;
        }
        if (MlsMessageTypes.SCHEME_MLS.equalsIgnoreCase(request.e2eeScheme())) {
            return !looksClientEncrypted(request.content());
        }
        return false;
    }

    public static boolean looksClientEncrypted(String content) {
        if (content == null || content.isBlank() || content.length() < 32) {
            return false;
        }
        try {
            var decoded = Base64.getDecoder().decode(content.trim());
            return decoded.length >= 28;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isE2eeType(String type) {
        return type != null && type.startsWith(E2EE_PREFIX);
    }

    public static UUID parseAttachmentFileId(String type, String content) {
        if (content == null || content.isBlank() || type == null) {
            return null;
        }
        var base = type.startsWith(E2EE_PREFIX) ? type.substring(E2EE_PREFIX.length()) : type;
        if (!"file".equals(base) && !"image".equals(base) && !"video".equals(base)
            && !"voice".equals(base) && !"audio".equals(base)) {
            return null;
        }
        try {
            return UUID.fromString(content.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String typeForEncrypted(String type, com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        if (encrypted == null) {
            return type != null ? type : "text";
        }
        return e2eeType(type);
    }

    public static String typeForSend(SendMessageRequest request, com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        var type = request != null ? request.type() : null;
        if (encrypted != null || (usesMlsScheme(request) && looksClientEncrypted(request.content()))) {
            return e2eeType(type);
        }
        return type != null ? type : "text";
    }

    private static String e2eeType(String type) {
        var base = type != null && !type.isBlank() ? type : "text";
        return base.startsWith(E2EE_PREFIX) ? base : E2EE_PREFIX + base;
    }

    public static String combinedCiphertextBase64(com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypted) {
        var nonce = Base64.getDecoder().decode(encrypted.nonceBase64());
        var ct = Base64.getDecoder().decode(encrypted.ciphertextBase64());
        var combined = new byte[nonce.length + ct.length];
        System.arraycopy(nonce, 0, combined, 0, nonce.length);
        System.arraycopy(ct, 0, combined, nonce.length, ct.length);
        return Base64.getEncoder().encodeToString(combined);
    }
}
