package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.mls.wire.MlsCommitPayload;
import com.avandocmsg.messenger.api.mls.wire.MlsEpochPayload;
import com.avandocmsg.messenger.api.mls.wire.MlsWelcomePayload;
import com.avandocmsg.messenger.api.mls.wire.MlsWireCodec;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Publishes RFC 9420 phase-1 MLS wire payloads to {@code mls.*} NATS subjects. */
public class MlsWirePublisher {

    private static final Logger log = LoggerFactory.getLogger(MlsWirePublisher.class);
    private static final String DEFAULT_CIPHER_SUITE = "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519";

    private final NatsOutboundPort nats;
    private final AppConfig appConfig;

    public MlsWirePublisher(NatsOutboundPort nats, AppConfig appConfig) {
        this.nats = nats;
        this.appConfig = appConfig;
    }

    public void publishWelcome(MlsGroupState state, List<UUID> memberUserIds, String cipherSuite) {
        if (!enabled() || state == null) {
            return;
        }
        try {
            var payload = new MlsWelcomePayload(
                state.groupId(),
                state.chatId(),
                state.epoch(),
                cipherSuite != null ? cipherSuite : DEFAULT_CIPHER_SUITE,
                MlsWireCodec.treeHash(state.treeData()),
                memberUserIds != null ? memberUserIds : List.of());
            publish(NatsSubjects.MLS_WELCOME, MlsWireCodec.encodeWelcome(payload));
        } catch (Exception e) {
            log.warn("Failed to publish {} for group {}", NatsSubjects.MLS_WELCOME, state.groupId(), e);
        }
    }

    public void publishCommit(MlsGroupState state, UUID memberUserId, MlsCommitPayload.Action action) {
        if (!enabled() || state == null || memberUserId == null || action == null) {
            return;
        }
        try {
            var payload = new MlsCommitPayload(
                state.groupId(),
                state.chatId(),
                state.epoch(),
                action,
                memberUserId,
                MlsWireCodec.treeHash(state.treeData()));
            publish(NatsSubjects.MLS_COMMIT, MlsWireCodec.encodeCommit(payload));
        } catch (Exception e) {
            log.warn("Failed to publish {} for group {}", NatsSubjects.MLS_COMMIT, state.groupId(), e);
        }
    }

    public void publishEpoch(MlsGroupState state) {
        if (!enabled() || state == null) {
            return;
        }
        try {
            var payload = new MlsEpochPayload(
                state.groupId(),
                state.chatId(),
                state.epoch(),
                MlsWireCodec.treeHash(state.treeData()));
            publish(NatsSubjects.MLS_EPOCH, MlsWireCodec.encodeEpoch(payload));
        } catch (Exception e) {
            log.warn("Failed to publish {} for group {}", NatsSubjects.MLS_EPOCH, state.groupId(), e);
        }
    }

    private void publish(String subject, byte[] payload) {
        if (nats == null) {
            return;
        }
        try {
            nats.publish(subject, payload);
            nats.flush(Duration.ofSeconds(2));
        } catch (Exception e) {
            log.warn("Failed to publish {}: {}", subject, e.getMessage());
        }
    }

    private boolean enabled() {
        return appConfig != null && appConfig.mlsWireEnabled();
    }
}
