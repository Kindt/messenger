package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.common.dto.ChatAvatarEvent;
import com.avandocmsg.messenger.common.dto.UserAvatarEvent;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Publishes avatar change events to NATS (spec 068 W7 prep). */
public final class AvatarUpdatePublisher {
    private static final Logger log = LoggerFactory.getLogger(AvatarUpdatePublisher.class);
    private static final ObjectMapper JSON = MessengerJson.mapper();

    private final NatsOutboundPort natsOutbound;
    private final AvatarUrlBuilder urlBuilder;

    public AvatarUpdatePublisher(NatsOutboundPort natsOutbound, AvatarUrlBuilder urlBuilder) {
        this.natsOutbound = natsOutbound;
        this.urlBuilder = urlBuilder;
    }

    public void publishUserAvatar(UserProfile profile, UserId viewerId) {
        if (natsOutbound == null || profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return;
        }
        try {
            var fileId = profile.avatarFileId() != null ? profile.avatarFileId().value().toString() : null;
            var url = profile.avatarFileId() != null && viewerId != null
                ? urlBuilder.resizeUrl(viewerId, profile.avatarFileId()) : null;
            var evt = UserAvatarEvent.of(
                profile.id().value().toString(),
                profile.orgId(),
                fileId,
                url,
                System.currentTimeMillis());
            natsOutbound.publish(NatsSubjects.USER_PRESENCE, JSON.writeValueAsBytes(evt));
        } catch (Exception e) {
            log.debug("user avatar publish failed: {}", e.getMessage());
        }
    }

    public void publishChatAvatar(ChatId chatId, FileId avatarFileId, UserId viewerId) {
        if (natsOutbound == null || chatId == null) {
            return;
        }
        try {
            var fileId = avatarFileId != null ? avatarFileId.value().toString() : null;
            var url = avatarFileId != null && viewerId != null
                ? urlBuilder.resizeUrl(viewerId, avatarFileId) : null;
            var evt = ChatAvatarEvent.of(chatId.value().toString(), fileId, url, System.currentTimeMillis());
            natsOutbound.publish(NatsSubjects.USER_PRESENCE, JSON.writeValueAsBytes(evt));
        } catch (Exception e) {
            log.debug("chat avatar publish failed: {}", e.getMessage());
        }
    }
}
