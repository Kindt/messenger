package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MlsServiceDecryptContentTest {

    @Test
    void decryptContentBase64_roundTripsAfterEncrypt() {
        var chatId = UUID.randomUUID();
        var sessions = new MlsGroupManagerTest.StubSessionRepository();
        var mls = new MlsService(sessions, new E2EEService());
        mls.ensureSession(chatId);
        var enc = mls.encrypt(chatId, "hello");
        assertNotNull(enc);
        var nonce = Base64.getDecoder().decode(enc.nonceBase64());
        var body = Base64.getDecoder().decode(enc.ciphertextBase64());
        var full = new byte[nonce.length + body.length];
        System.arraycopy(nonce, 0, full, 0, nonce.length);
        System.arraycopy(body, 0, full, nonce.length, body.length);
        var plain = mls.decryptContentBase64(chatId, Base64.getEncoder().encodeToString(full));
        assertEquals("hello", plain);
    }
}
