package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.dto.UserPresenceEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/** Publishes user presence/custom status to NATS for WS fan-out. */
public final class UserPresencePublisher {
    private static final Logger log = LoggerFactory.getLogger(UserPresencePublisher.class);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final NatsOutboundPort natsOutbound;

    public UserPresencePublisher(NatsOutboundPort natsOutbound) {
        this.natsOutbound = natsOutbound;
    }

    public void publish(UserProfile profile) {
        if (natsOutbound == null || profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return;
        }
        try {
            var evt = UserPresenceEvent.of(
                profile.id().value().toString(),
                profile.orgId(),
                profile.presenceStatus(),
                profile.customStatusText(),
                profile.dndUntil());
            var bytes = JSON.writeValueAsString(evt).getBytes(StandardCharsets.UTF_8);
            natsOutbound.publish(NatsSubjects.USER_PRESENCE, bytes);
        } catch (Exception e) {
            log.debug("presence publish failed: {}", e.getMessage());
        }
    }
}
