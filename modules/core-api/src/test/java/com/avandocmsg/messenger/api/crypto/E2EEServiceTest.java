package com.avandocmsg.messenger.api.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class E2EEServiceTest {

    private final E2EEService e2ee = new E2EEService();

    @Test
    void generateKeyPair_returnsNonNullKeys() {
        var kp = e2ee.generateKeyPair();

        assertNotNull(kp);
        assertNotNull(kp.privateKey());
        assertNotNull(kp.publicKey());
        assertTrue(kp.privateKey().length > 0);
        assertTrue(kp.publicKey().length > 0);
    }

    @Test
    void sharedSecret_derivesSameKey() {
        var alice = e2ee.generateKeyPair();
        var bob = e2ee.generateKeyPair();

        var secret1 = e2ee.deriveSharedSecret(alice.privateKey(), bob.publicKey());
        var secret2 = e2ee.deriveSharedSecret(bob.privateKey(), alice.publicKey());

        assertNotNull(secret1);
        assertNotNull(secret2);
        assertArrayEquals(secret1, secret2);
    }

    @Test
    void hkdfDerivesDeterministicKey() {
        var secret = "test-secret".getBytes(StandardCharsets.UTF_8);
        var salt = "test-salt".getBytes(StandardCharsets.UTF_8);

        var key1 = e2ee.deriveKey(secret, salt, "test-info", 32);
        var key2 = e2ee.deriveKey(secret, salt, "test-info", 32);

        assertNotNull(key1);
        assertEquals(32, key1.length);
        assertArrayEquals(key1, key2);
    }

    @Test
    void hkdfDerivesDifferentKeysWithDifferentInfo() {
        var secret = "test-secret".getBytes(StandardCharsets.UTF_8);
        var salt = "test-salt".getBytes(StandardCharsets.UTF_8);

        var key1 = e2ee.deriveKey(secret, salt, "info-1", 32);
        var key2 = e2ee.deriveKey(secret, salt, "info-2", 32);

        assertNotNull(key1);
        assertNotNull(key2);
        assertFalse(java.util.Arrays.equals(key1, key2));
    }

    @Test
    void encryptDecrypt_roundtrip() {
        var key = e2ee.randomBytes(32);
        var plaintext = "Hello, E2EE!".getBytes(StandardCharsets.UTF_8);
        var aad = "chat:0".getBytes(StandardCharsets.UTF_8);

        var ciphertext = e2ee.encrypt(plaintext, key, aad);
        assertNotNull(ciphertext);

        var decrypted = e2ee.decrypt(ciphertext, key, aad);
        assertNotNull(decrypted);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decrypt_failsWithWrongKey() {
        var key1 = e2ee.randomBytes(32);
        var key2 = e2ee.randomBytes(32);
        var plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        var ciphertext = e2ee.encrypt(plaintext, key1, "aad".getBytes(StandardCharsets.UTF_8));
        assertNotNull(ciphertext);

        var decrypted = e2ee.decrypt(ciphertext, key2, "aad".getBytes(StandardCharsets.UTF_8));
        assertNotNull(decrypted);
        assertEquals(0, decrypted.length);
    }

    @Test
    void decrypt_failsWithWrongAad() {
        var key = e2ee.randomBytes(32);
        var plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        var ciphertext = e2ee.encrypt(plaintext, key, "aad1".getBytes(StandardCharsets.UTF_8));
        assertNotNull(ciphertext);

        var decrypted = e2ee.decrypt(ciphertext, key, "aad2".getBytes(StandardCharsets.UTF_8));
        assertNotNull(decrypted);
        assertEquals(0, decrypted.length);
    }

    @Test
    void decrypt_failsWithTamperedCiphertext() {
        var key = e2ee.randomBytes(32);
        var plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        var ciphertext = e2ee.encrypt(plaintext, key, "aad".getBytes(StandardCharsets.UTF_8));
        assertNotNull(ciphertext);

        ciphertext[12] ^= 0x01;

        var decrypted = e2ee.decrypt(ciphertext, key, "aad".getBytes(StandardCharsets.UTF_8));
        assertNotNull(decrypted);
        assertEquals(0, decrypted.length);
    }

    @Test
    void sha256_returnsConsistentHash() {
        var data = "test-data".getBytes(StandardCharsets.UTF_8);

        var hash1 = e2ee.sha256(data);
        var hash2 = e2ee.sha256(data);

        assertNotNull(hash1);
        assertEquals(32, hash1.length);
        assertArrayEquals(hash1, hash2);
    }

    @Test
    void randomBytes_returnsDifferentValues() {
        var buf1 = e2ee.randomBytes(32);
        var buf2 = e2ee.randomBytes(32);

        assertEquals(32, buf1.length);
        assertEquals(32, buf2.length);
        assertFalse(java.util.Arrays.equals(buf1, buf2));
    }
}
