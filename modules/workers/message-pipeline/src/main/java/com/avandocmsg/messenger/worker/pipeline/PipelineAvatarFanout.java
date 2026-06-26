package com.avandocmsg.messenger.worker.pipeline;

import com.avandocmsg.messenger.common.avatar.WorkerAvatarResizeUrl;
import com.avandocmsg.messenger.common.dto.ChatAvatarEvent;
import com.avandocmsg.messenger.common.dto.UserAvatarEvent;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.json.MessengerJson;
import com.avandocmsg.messenger.common.nats.DeliverFanout;
import com.avandocmsg.messenger.common.nats.FanoutDedup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/** Personalized avatar WS fan-out (spec 068 W7). */
final class PipelineAvatarFanout {
    private static final Logger log = LoggerFactory.getLogger(PipelineAvatarFanout.class);
    private static final ObjectMapper MAPPER = MessengerJson.mapper();

    private PipelineAvatarFanout() {
    }

    static void dispatch(
        byte[] raw,
        JsonNode root,
        DataSource dataSource,
        Connection natsConnection,
        DeliverFanout.Config deliverConfig,
        FanoutDedup fanoutDedup,
        WorkerAvatarResizeUrl.Config avatarConfig,
        UserMessageSource workerMessages
    ) throws Exception {
        var type = root.path("type").asText("");
        if (UserAvatarEvent.TYPE.equals(type)) {
            fanoutUserAvatar(root, dataSource, natsConnection, deliverConfig, fanoutDedup, avatarConfig,
                workerMessages);
            return;
        }
        if (ChatAvatarEvent.TYPE.equals(type)) {
            fanoutChatAvatar(raw, root, dataSource, natsConnection, deliverConfig, fanoutDedup, avatarConfig,
                workerMessages);
        }
    }

    private static void fanoutUserAvatar(
        JsonNode root,
        DataSource dataSource,
        Connection natsConnection,
        DeliverFanout.Config deliverConfig,
        FanoutDedup fanoutDedup,
        WorkerAvatarResizeUrl.Config avatarConfig,
        UserMessageSource workerMessages
    ) throws Exception {
        var userId = parseUuid(root.path("user_id").asText(null));
        if (userId == null) {
            return;
        }
        var orgId = root.path("org_id").asText(null);
        if (orgId == null || orgId.isBlank()) {
            return;
        }
        var fileId = parseUuid(root.path("avatar_file_id").asText(null));
        var ts = root.path("ts").asLong(System.currentTimeMillis());
        var members = PipelineFanoutLogic.loadPresenceRecipientUserIds(dataSource, userId, workerMessages);
        members.add(userId.toString());
        publishPersonalizedUserAvatar(natsConnection, members, userId, orgId, fileId, ts, deliverConfig, fanoutDedup,
            avatarConfig, dedupId("avatar", userId.toString(), Long.toString(ts)));
    }

    private static void fanoutChatAvatar(
        byte[] raw,
        JsonNode root,
        DataSource dataSource,
        Connection natsConnection,
        DeliverFanout.Config deliverConfig,
        FanoutDedup fanoutDedup,
        WorkerAvatarResizeUrl.Config avatarConfig,
        UserMessageSource workerMessages
    ) throws Exception {
        var chatId = parseUuid(root.path("chat_id").asText(null));
        if (chatId == null) {
            return;
        }
        var fileId = parseUuid(root.path("avatar_file_id").asText(null));
        var ts = root.path("ts").asLong(System.currentTimeMillis());
        var members = PipelineFanoutLogic.loadAllChatMemberUserIds(dataSource, chatId, workerMessages);
        if (members.isEmpty()) {
            return;
        }
        var chatType = loadChatType(dataSource, chatId);
        for (var memberId : members) {
            var viewerId = parseUuid(memberId);
            if (viewerId == null) {
                continue;
            }
            var displayUrl = resolveDisplayAvatarUrl(dataSource, chatId, chatType, viewerId, fileId, avatarConfig);
            var evt = ChatAvatarEvent.of(chatId.toString(),
                fileId != null ? fileId.toString() : null,
                displayUrl,
                ts);
            var payload = MAPPER.writeValueAsBytes(evt);
            DeliverFanout.publish(natsConnection, List.of(memberId), chatId.toString(), payload, deliverConfig,
                fanoutDedup, dedupId("chat.avatar", chatId.toString(), memberId, Long.toString(ts)));
        }
    }

    private static void publishPersonalizedUserAvatar(
        Connection natsConnection,
        List<String> members,
        UUID userId,
        String orgId,
        UUID fileId,
        long ts,
        DeliverFanout.Config deliverConfig,
        FanoutDedup fanoutDedup,
        WorkerAvatarResizeUrl.Config avatarConfig,
        String dedupBase
    ) throws Exception {
        for (var memberId : members) {
            var viewerId = parseUuid(memberId);
            if (viewerId == null) {
                continue;
            }
            var url = fileId != null ? WorkerAvatarResizeUrl.resizePath(avatarConfig, viewerId, fileId) : null;
            var evt = UserAvatarEvent.of(userId.toString(), orgId,
                fileId != null ? fileId.toString() : null,
                url,
                ts);
            var payload = MAPPER.writeValueAsBytes(evt);
            DeliverFanout.publish(natsConnection, List.of(memberId), userId.toString(), payload, deliverConfig,
                fanoutDedup, dedupBase + "|" + memberId);
        }
    }

    private static String resolveDisplayAvatarUrl(
        DataSource dataSource,
        UUID chatId,
        String chatType,
        UUID viewerId,
        UUID chatAvatarFileId,
        WorkerAvatarResizeUrl.Config avatarConfig
    ) {
        if ("p2p".equals(chatType)) {
            var peerId = findOtherP2PMember(dataSource, chatId, viewerId);
            if (peerId != null) {
                var peerAvatar = findUserAvatarFileId(dataSource, peerId);
                if (peerAvatar != null) {
                    return WorkerAvatarResizeUrl.resizePath(avatarConfig, viewerId, peerAvatar);
                }
            }
        }
        if (chatAvatarFileId == null) {
            return null;
        }
        return WorkerAvatarResizeUrl.resizePath(avatarConfig, viewerId, chatAvatarFileId);
    }

    private static String loadChatType(DataSource dataSource, UUID chatId) {
        var sql = "SELECT type FROM chats WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            log.debug("chat type load failed chat={}: {}", chatId, e.getMessage());
        }
        return null;
    }

    private static UUID findOtherP2PMember(DataSource dataSource, UUID chatId, UUID viewerId) {
        var sql = """
            SELECT user_id FROM chat_members
            WHERE chat_id = ? AND user_id <> ? AND banned = false
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, viewerId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject(1, UUID.class);
                }
            }
        } catch (Exception e) {
            log.debug("p2p peer lookup failed: {}", e.getMessage());
        }
        return null;
    }

    private static UUID findUserAvatarFileId(DataSource dataSource, UUID userId) {
        var sql = "SELECT avatar_file_id FROM users WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject(1, UUID.class);
                }
            }
        } catch (Exception e) {
            log.debug("user avatar lookup failed: {}", e.getMessage());
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String dedupId(String... parts) {
        return String.join("|", parts);
    }
}
