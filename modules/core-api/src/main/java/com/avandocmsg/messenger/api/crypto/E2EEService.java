package com.avandocmsg.messenger.api.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyAgreement;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

public class E2EEService {
    private static final Logger log = LoggerFactory.getLogger(E2EEService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_SIZE = 12;
    private static final int TAG_SIZE = 16;
    private static final String X25519 = "X25519";
    private static final byte[] EMPTY = new byte[0];

    static {
        CryptoProvider.ensureLoaded();
    }

    public record KeyPair(byte[] privateKey, byte[] publicKey) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof KeyPair that)) {
                return false;
            }
            return Arrays.equals(privateKey, that.privateKey)
                && Arrays.equals(publicKey, that.publicKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(privateKey), Arrays.hashCode(publicKey));
        }

        @Override
        public String toString() {
            return "KeyPair[privateKey=" + Arrays.toString(privateKey)
                + ", publicKey=" + Arrays.toString(publicKey) + "]";
        }
    }

    public KeyPair generateKeyPair() {
        try {
            var kpg = KeyPairGenerator.getInstance(X25519, "BC");
            var pair = kpg.generateKeyPair();
            return new KeyPair(pair.getPrivate().getEncoded(), pair.getPublic().getEncoded());
        } catch (Exception e) {
            log.error("Failed to generate X25519 key pair", e);
            return null;
        }
    }

    public byte[] deriveSharedSecret(byte[] privateKey, byte[] publicKey) {
        try {
            var kf = java.security.KeyFactory.getInstance(X25519, "BC");
            var privSpec = new java.security.spec.PKCS8EncodedKeySpec(privateKey);
            var priv = kf.generatePrivate(privSpec);
            var pubSpec = new java.security.spec.X509EncodedKeySpec(publicKey);
            var pub = kf.generatePublic(pubSpec);
            var ka = KeyAgreement.getInstance(X25519, "BC");
            ka.init(priv);
            ka.doPhase(pub, true);
            return ka.generateSecret();
        } catch (Exception e) {
            log.error("Failed to derive shared secret", e);
            return EMPTY;
        }
    }

    public byte[] deriveKey(byte[] secret, byte[] salt, String info, int length) {
        var hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(secret, salt, info.getBytes()));
        var key = new byte[length];
        hkdf.generateBytes(key, 0, length);
        return key;
    }

    public byte[] encrypt(byte[] plaintext, byte[] key, byte[] aad) {
        var nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(nonce);
        var cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(true, new AEADParameters(new KeyParameter(key), TAG_SIZE * 8, nonce, aad));
        var out = new byte[cipher.getOutputSize(plaintext.length)];
        var len = cipher.processBytes(plaintext, 0, plaintext.length, out, 0);
        try {
            cipher.doFinal(out, len);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            return EMPTY;
        }
        var result = new byte[NONCE_SIZE + out.length];
        System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
        System.arraycopy(out, 0, result, NONCE_SIZE, out.length);
        return result;
    }

    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad) {
        if (ciphertext.length < NONCE_SIZE + TAG_SIZE) {
            return EMPTY;
        }
        var nonce = Arrays.copyOfRange(ciphertext, 0, NONCE_SIZE);
        var ct = Arrays.copyOfRange(ciphertext, NONCE_SIZE, ciphertext.length);
        var cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(false, new AEADParameters(new KeyParameter(key), TAG_SIZE * 8, nonce, aad));
        var out = new byte[cipher.getOutputSize(ct.length)];
        var len = cipher.processBytes(ct, 0, ct.length, out, 0);
        try {
            len += cipher.doFinal(out, len);
        } catch (Exception e) {
            log.warn("Decryption failed", e);
            return EMPTY;
        }
        return Arrays.copyOf(out, len);
    }

    public byte[] randomBytes(int size) {
        var buf = new byte[size];
        RANDOM.nextBytes(buf);
        return buf;
    }

    public byte[] sha256(byte[] data) {
        try {
            var md = MessageDigest.getInstance("SHA-256", "BC");
            return md.digest(data);
        } catch (Exception e) {
            log.error("SHA-256 failed", e);
            return EMPTY;
        }
    }
}
