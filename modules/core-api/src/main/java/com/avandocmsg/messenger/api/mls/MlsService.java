package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.dto.EncryptedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Серверная обвязка для типов сообщений {@code e2ee-*}: строка сессии в БД ({@link SessionRepository})
 * и симметричное шифрование открытого текста через {@link E2EEService} (ключ из сессии + чата, AAD с эпохой).
 * <p><b>Не</b> реализует полный протокол Messaging Layer Security по RFC&nbsp;9420: нет группового дерева ключей,
 * MLS Welcome/Commit, взаимодействия с key package по wire-формату MLS и стандартной машины состояний эпох.
 * Это сознательная заглушка для контура API и хранения ciphertext; настоящий MLS-handshake — отдельный
 * продуктовый и инженерный эпик (клиент + сервер + совместимая библиотека).
 */
public class MlsService {
    private static final Logger log = LoggerFactory.getLogger(MlsService.class);
    private static final String DEFAULT_CIPHER_SUITE = "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519";

    private final SessionRepository sessionRepository;
    private final E2EEService e2eeService;

    public MlsService(SessionRepository sessionRepository, E2EEService e2eeService) {
        this.sessionRepository = sessionRepository;
        this.e2eeService = e2eeService;
    }

    public Optional<String> ensureSession(UUID chatId) {
        var existing = sessionRepository.findByChatId(chatId, 0);
        if (existing.isPresent()) {
            return Optional.of(existing.get().id().toString());
        }
        var session = sessionRepository.create(chatId, DEFAULT_CIPHER_SUITE);
        if (session == null) {
            return Optional.empty();
        }
        log.info("Created MLS session {} for chat {}", session.id(), chatId);
        return Optional.of(session.id().toString());
    }

    public EncryptedMessage encrypt(UUID chatId, UUID senderId, String plaintext) {
        var sessionOpt = sessionRepository.findByChatId(chatId, 0);
        if (sessionOpt.isEmpty()) {
            var sid = ensureSession(chatId);
            if (sid.isEmpty()) return null;
            sessionOpt = sessionRepository.findByChatId(chatId, 0);
            if (sessionOpt.isEmpty()) return null;
        }
        var session = sessionOpt.get();
        var sessionKey = deriveSessionKey(session.id(), session.chatId());
        var aad = (session.chatId().toString() + ":" + session.epoch()).getBytes(StandardCharsets.UTF_8);
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
        var aad = (session.chatId().toString() + ":" + session.epoch()).getBytes(StandardCharsets.UTF_8);
        var fullCiphertext = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, fullCiphertext, 0, nonce.length);
        System.arraycopy(ciphertext, 0, fullCiphertext, nonce.length, ciphertext.length);
        var plaintext = e2eeService.decrypt(fullCiphertext, sessionKey, aad);
        if (plaintext == null) return null;
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public byte[] deriveSessionKey(UUID sessionId, UUID chatId) {
        var seed = (sessionId.toString() + ":" + chatId.toString()).getBytes(StandardCharsets.UTF_8);
        return e2eeService.deriveKey(e2eeService.randomBytes(32), seed, "mls-session-key", 32);
    }

    /**
     * Расшифровка содержимого сообщения (nonce + ciphertext в одном Base64), сохранённого в {@code messages.content}.
     */
    public String decryptContentBase64(UUID chatId, String contentBase64) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            return null;
        }
        var sessionOpt = sessionRepository.findByChatId(chatId, 0);
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
        var aad = (session.chatId().toString() + ":" + session.epoch()).getBytes(StandardCharsets.UTF_8);
        var plaintext = e2eeService.decrypt(full, sessionKey, aad);
        if (plaintext == null) {
            return null;
        }
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
