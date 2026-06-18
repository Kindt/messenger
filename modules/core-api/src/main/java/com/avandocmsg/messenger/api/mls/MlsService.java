package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.dto.EncryptedMessage;
import com.avandocmsg.messenger.api.mls.openmls.OpenMlsWireLayout;
import com.avandocmsg.messenger.api.mls.wire.MlsWireCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Серверная MLS-обвязка (phase 2): KMLS wire + симметричное шифрование через {@link E2EEService}
 * с привязкой к эпохе группы ({@link SessionRepository}). Полный OpenMLS state machine deferred;
 * epoch rotation синхронизируется с {@link MlsGroupManager} и NATS {@code mls.*} consumer.
 */
public class MlsService {
    private static final Logger log = LoggerFactory.getLogger(MlsService.class);

    private final SessionRepository sessionRepository;
    private final E2EEService e2eeService;

    public MlsService(SessionRepository sessionRepository, E2EEService e2eeService) {
        this.sessionRepository = sessionRepository;
        this.e2eeService = e2eeService;
    }

    public Optional<String> ensureSession(UUID chatId) {
        var existing = sessionRepository.findLatestByChatId(chatId);
        if (existing.isPresent()) {
            return Optional.of(existing.get().id().toString());
        }
        var session = sessionRepository.create(chatId, OpenMlsWireLayout.DEFAULT_CIPHER_SUITE);
        if (session == null) {
            return Optional.empty();
        }
        log.info("Created MLS session {} for chat {}", session.id(), chatId);
        return Optional.of(session.id().toString());
    }

    public EncryptedMessage encrypt(UUID chatId, UUID senderId, String plaintext) {
        var sessionOpt = sessionRepository.findLatestByChatId(chatId);
        if (sessionOpt.isEmpty()) {
            var sid = ensureSession(chatId);
            if (sid.isEmpty()) return null;
            sessionOpt = sessionRepository.findLatestByChatId(chatId);
            if (sessionOpt.isEmpty()) return null;
        }
        var session = sessionOpt.get();
        var sessionKey = deriveSessionKey(session.id(), session.chatId());
        var aad = OpenMlsWireLayout.aadBytes(session.chatId(), session.epoch());
        var ciphertext = e2eeService.encrypt(
            plaintext.getBytes(StandardCharsets.UTF_8), sessionKey, aad);
        if (ciphertext == null) return null;
        return new EncryptedMessage(ciphertext, session.id().toString(), session.epoch());
    }

    public String decrypt(UUID chatId, long epoch, byte[] ciphertext, byte[] nonce) {
        var sessionOpt = sessionRepository.findByChatId(chatId, epoch);
        if (sessionOpt.isEmpty()) {
            log.warn("No session found for chat {} epoch {}", chatId, epoch);
            return null;
        }
        var session = sessionOpt.get();
        var sessionKey = deriveSessionKey(session.id(), session.chatId());
        var aad = OpenMlsWireLayout.aadBytes(session.chatId(), session.epoch());
        var fullCiphertext = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, fullCiphertext, 0, nonce.length);
        System.arraycopy(ciphertext, 0, fullCiphertext, nonce.length, ciphertext.length);
        var plaintext = e2eeService.decrypt(fullCiphertext, sessionKey, aad);
        if (plaintext == null) return null;
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public byte[] deriveSessionKey(UUID sessionId, UUID chatId) {
        var seed = (sessionId.toString() + ":" + chatId.toString()).getBytes(StandardCharsets.UTF_8);
        return e2eeService.deriveKey(seed, new byte[0], "mls-session-key", 32);
    }

    /**
     * Расшифровка содержимого сообщения (nonce + ciphertext в одном Base64), сохранённого в {@code messages.content}.
     */
    /**
     * Синхронизирует epoch сессии с групповой эпохой после membership rotation (add/remove).
     */
    public boolean syncEpoch(UUID chatId, long targetEpoch, byte[] treeData) {
        if (chatId == null || targetEpoch < 0 || sessionRepository == null) {
            return false;
        }
        var sessionOpt = sessionRepository.findLatestByChatId(chatId);
        if (sessionOpt.isEmpty()) {
            ensureSession(chatId);
            sessionOpt = sessionRepository.findLatestByChatId(chatId);
        }
        if (sessionOpt.isEmpty()) {
            return false;
        }
        var treeHash = MlsWireCodec.treeHash(treeData);
        var session = sessionOpt.get();
        while (session.epoch() < targetEpoch) {
            if (!sessionRepository.advanceEpoch(session.id(), treeHash, treeHash)) {
                return false;
            }
            sessionOpt = sessionRepository.findLatestByChatId(chatId);
            if (sessionOpt.isEmpty()) {
                return false;
            }
            session = sessionOpt.get();
        }
        return session.epoch() >= targetEpoch;
    }

    public String decryptContentBase64(UUID chatId, String contentBase64) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            return null;
        }
        var sessionOpt = sessionRepository.findLatestByChatId(chatId);
        if (sessionOpt.isEmpty()) {
            return null;
        }
        var session = sessionOpt.get();
        byte[] full;
        try {
            full = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            log.debug("Invalid content base64 for chat {}", chatId);
            return null;
        }
        var sessionKey = deriveSessionKey(session.id(), session.chatId());
        var aad = OpenMlsWireLayout.aadBytes(session.chatId(), session.epoch());
        var plaintext = e2eeService.decrypt(full, sessionKey, aad);
        if (plaintext == null) {
            return null;
        }
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
