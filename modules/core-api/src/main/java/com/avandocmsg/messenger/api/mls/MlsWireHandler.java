package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.mls.wire.MlsCommitPayload;
import com.avandocmsg.messenger.api.mls.wire.MlsEpochPayload;
import com.avandocmsg.messenger.api.mls.wire.MlsWelcomePayload;
import com.avandocmsg.messenger.api.mls.wire.MlsWireCodec;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/**
 * Applies inbound KMLS wire payloads from NATS {@code mls.*} subjects (multi-instance sync).
 */
public final class MlsWireHandler {

    private static final Logger log = LoggerFactory.getLogger(MlsWireHandler.class);

    private final MlsGroupStateRepository groupStateRepository;
    private final MlsService mlsService;
    private final Clock clock;

    public MlsWireHandler(MlsGroupStateRepository groupStateRepository, MlsService mlsService, Clock clock) {
        this.groupStateRepository = groupStateRepository;
        this.mlsService = mlsService;
        this.clock = clock;
    }

    public void handle(String subject, byte[] payload) {
        if (payload == null || payload.length == 0 || subject == null) {
            return;
        }
        try {
            if (NatsSubjects.MLS_WELCOME.equals(subject)) {
                applyWelcome(MlsWireCodec.decodeWelcome(payload));
            } else if (NatsSubjects.MLS_COMMIT.equals(subject)) {
                applyCommit(MlsWireCodec.decodeCommit(payload));
            } else if (NatsSubjects.MLS_EPOCH.equals(subject)) {
                applyEpoch(MlsWireCodec.decodeEpoch(payload));
            }
        } catch (Exception e) {
            log.warn("Failed to handle {}: {}", subject, e.getMessage());
        }
    }

    void applyWelcome(MlsWelcomePayload welcome) {
        if (welcome == null || welcome.groupId() == null || welcome.chatId() == null) {
            return;
        }
        var existing = groupStateRepository.findByGroupId(welcome.groupId());
        if (existing.isPresent() && existing.get().epoch() >= welcome.epoch()) {
            return;
        }
        var now = clock.instant();
        var treeSeed = ("welcome:" + welcome.groupId() + ":members:" + welcome.memberUserIds().size())
            .getBytes(StandardCharsets.UTF_8);
        var state = new MlsGroupState(
            welcome.groupId(),
            welcome.chatId(),
            welcome.epoch(),
            treeSeed,
            existing.map(MlsGroupState::createdAt).orElse(now),
            now);
        if (groupStateRepository.save(state)) {
            mlsService.ensureSession(welcome.chatId());
            mlsService.syncEpoch(welcome.chatId(), welcome.epoch(), state.treeData());
            log.debug("Applied {} groupId={} epoch={}", NatsSubjects.MLS_WELCOME, welcome.groupId(), welcome.epoch());
        }
    }

    void applyCommit(MlsCommitPayload commit) {
        if (commit == null || commit.groupId() == null) {
            return;
        }
        var group = groupStateRepository.findByGroupId(commit.groupId()).orElse(null);
        if (group == null || group.epoch() >= commit.epoch()) {
            return;
        }
        var reason = commit.action() == MlsCommitPayload.Action.ADD ? "add:" : "remove:";
        var tree = (group.treeData() != null ? new String(group.treeData(), StandardCharsets.UTF_8) : "")
            + "|" + reason + commit.memberUserId();
        var updated = new MlsGroupState(
            group.groupId(),
            group.chatId(),
            commit.epoch(),
            tree.getBytes(StandardCharsets.UTF_8),
            group.createdAt(),
            clock.instant());
        if (groupStateRepository.save(updated)) {
            mlsService.syncEpoch(group.chatId(), updated.epoch(), updated.treeData());
            log.debug("Applied {} groupId={} epoch={}", NatsSubjects.MLS_COMMIT, commit.groupId(), commit.epoch());
        }
    }

    void applyEpoch(MlsEpochPayload epochPayload) {
        if (epochPayload == null || epochPayload.groupId() == null) {
            return;
        }
        var group = groupStateRepository.findByGroupId(epochPayload.groupId()).orElse(null);
        if (group == null || group.epoch() >= epochPayload.epoch()) {
            return;
        }
        var updated = new MlsGroupState(
            group.groupId(),
            group.chatId(),
            epochPayload.epoch(),
            group.treeData(),
            group.createdAt(),
            clock.instant());
        if (groupStateRepository.save(updated)) {
            mlsService.syncEpoch(group.chatId(), updated.epoch(), updated.treeData());
            log.debug("Applied {} groupId={} epoch={}", NatsSubjects.MLS_EPOCH, epochPayload.groupId(), epochPayload.epoch());
        }
    }
}
