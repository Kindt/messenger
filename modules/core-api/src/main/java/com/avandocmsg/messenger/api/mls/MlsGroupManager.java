package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.mls.dto.EncryptedMessage;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Group-level MLS facade: persists {@link MlsGroupState} and delegates crypto to {@link MlsService}.
 * Wire-format RFC 9420 Welcome/Commit remain deferred; epoch bumps on membership change.
 */
public class MlsGroupManager {
    private final MlsGroupStateRepository groupStateRepository;
    private final MlsService mlsService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public MlsGroupManager(MlsGroupStateRepository groupStateRepository,
                           MlsService mlsService,
                           UuidGenerator uuidGenerator,
                           Clock clock) {
        this.groupStateRepository = groupStateRepository;
        this.mlsService = mlsService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    public UUID createGroup(UUID chatId, List<UUID> members) {
        var existing = groupStateRepository.findByChatId(chatId);
        if (existing.isPresent()) {
            return existing.get().groupId();
        }
        var groupId = uuidGenerator.randomUuid();
        var now = clock.instant();
        var treeSeed = ("group:" + groupId + ":members:" + members.size()).getBytes(StandardCharsets.UTF_8);
        var state = new MlsGroupState(groupId, chatId, 0L, treeSeed, now, now);
        if (!groupStateRepository.save(state)) {
            return null;
        }
        mlsService.ensureSession(chatId);
        return groupId;
    }

    public boolean addMember(UUID groupId, UUID memberUserId) {
        return bumpEpoch(groupId, "add:" + memberUserId);
    }

    public boolean removeMember(UUID groupId, UUID memberUserId) {
        return bumpEpoch(groupId, "remove:" + memberUserId);
    }

    public EncryptedMessage encrypt(UUID groupId, UUID senderId, String plaintext) {
        var group = groupStateRepository.findByGroupId(groupId).orElse(null);
        if (group == null) {
            return null;
        }
        return mlsService.encrypt(group.chatId(), senderId, plaintext);
    }

    public String decrypt(UUID groupId, long epoch, byte[] ciphertext, byte[] nonce) {
        var group = groupStateRepository.findByGroupId(groupId).orElse(null);
        if (group == null || ciphertext == null || nonce == null) {
            return null;
        }
        var full = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, full, 0, nonce.length);
        System.arraycopy(ciphertext, 0, full, nonce.length, ciphertext.length);
        return mlsService.decryptContentBase64(group.chatId(), java.util.Base64.getEncoder().encodeToString(full));
    }

    public Optional<MlsGroupState> findGroup(UUID groupId) {
        return groupStateRepository.findByGroupId(groupId);
    }

    public long groupCount() {
        return groupStateRepository.countAll();
    }

    private boolean bumpEpoch(UUID groupId, String reason) {
        var group = groupStateRepository.findByGroupId(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        var tree = (group.treeData() != null ? new String(group.treeData(), StandardCharsets.UTF_8) : "")
            + "|" + reason;
        var updated = new MlsGroupState(
            group.groupId(),
            group.chatId(),
            group.epoch() + 1,
            tree.getBytes(StandardCharsets.UTF_8),
            group.createdAt(),
            clock.instant());
        return groupStateRepository.save(updated);
    }
}
