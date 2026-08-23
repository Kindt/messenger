package com.avandocmsg.messenger.desktop.sdk.update;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/** Ed25519 manifest signature verification (update-manifest.schema.json). */
public final class UpdateVerifier {

    public boolean verify(PublicKey publicKey, byte[] payload, byte[] signatureBytes) {
        if (publicKey == null || payload == null || signatureBytes == null) {
            return false;
        }
        try {
            var sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(payload);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyBase64(PublicKey publicKey, byte[] payload, String signatureBase64) {
        if (signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        return verify(publicKey, payload, Base64.getDecoder().decode(signatureBase64));
    }
}
