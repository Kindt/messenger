package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.api.params.ListPagination;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hexagonal application service for message reads and write-path (Phase 2b+). */
public final class MessageApplicationService {
    private final MessageRepositoryPort messageRepositoryPort;
    private final ChatRepositoryPort chatRepositoryPort;
    private final BlockRepositoryPort blockRepositoryPort;
    private final MessageSendCoordinator sendCoordinator;
    private final MessageEditCoordinator editCoordinator;
    private final MessageDeleteCoordinator deleteCoordinator;
    private final MessageReactionCoordinator reactionCoordinator;
    private final MessagePinCoordinator pinCoordinator;
    private final MessageQueryPort messageQueryPort;
    private final MlsService mlsService;
    private final com.avandocmsg.messenger.api.compliance.DlpBridgeGate dlpBridgeGate;

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort) {
        this(messageRepositoryPort, chatRepositoryPort, null, null, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, null, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, sendCoordinator, null, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, sendCoordinator, editCoordinator, null, null, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, null, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, pinCoordinator, null, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator,
                                     MessageQueryPort messageQueryPort,
                                     MlsService mlsService) {
        this(messageRepositoryPort, chatRepositoryPort, blockRepositoryPort, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, pinCoordinator, messageQueryPort, mlsService, null);
    }

    public MessageApplicationService(MessageRepositoryPort messageRepositoryPort, ChatRepositoryPort chatRepositoryPort,
                                     BlockRepositoryPort blockRepositoryPort, MessageSendCoordinator sendCoordinator,
                                     MessageEditCoordinator editCoordinator, MessageDeleteCoordinator deleteCoordinator,
                                     MessageReactionCoordinator reactionCoordinator,
                                     MessagePinCoordinator pinCoordinator,
                                     MessageQueryPort messageQueryPort,
                                     MlsService mlsService,
                                     com.avandocmsg.messenger.api.compliance.DlpBridgeGate dlpBridgeGate) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.chatRepositoryPort = chatRepositoryPort;
        this.blockRepositoryPort = blockRepositoryPort;
        this.sendCoordinator = sendCoordinator;
        this.editCoordinator = editCoordinator;
        this.deleteCoordinator = deleteCoordinator;
        this.reactionCoordinator = reactionCoordinator;
        this.pinCoordinator = pinCoordinator;
        this.messageQueryPort = messageQueryPort;
        this.mlsService = mlsService;
        this.dlpBridgeGate = dlpBridgeGate;
    }

    public Optional<Message> getMessageForMember(ChatId chatId, MessageId messageId, UserId viewerId) {
        if (chatRepositoryPort.memberRole(chatId, viewerId).isEmpty()) {
            return Optional.empty();
        }
        return messageRepositoryPort.findById(messageId)
            .filter(m -> m.chatId().equals(chatId));
    }

    public boolean isChatMember(ChatId chatId, UserId userId) {
        return chatRepositoryPort.memberRole(chatId, userId).isPresent();
    }

    /** Reader is a chat member and not banned. */
    public boolean canAccessChat(UUID chatId, UUID readerId) {
        var chat = ChatId.of(chatId);
        var reader = UserId.of(readerId);
        if (chatRepositoryPort.memberRole(chat, reader).isEmpty()) {
            return false;
        }
        return !chatRepositoryPort.isMemberBanned(chat, reader);
    }

    public List<MessageResponse> listMessages(UUID chatId, UUID userId, int limit, UUID before) {
        return listMessages(chatId, userId, limit, before, null);
    }

    public List<MessageResponse> listMessages(UUID chatId, UUID userId, int limit, UUID before, UUID threadId) {
        if (messageQueryPort == null || !canAccessChat(chatId, userId)) {
            return List.of();
        }
        if (limit <= 0 || limit > ListPagination.MAX_LIMIT) {
            limit = ListPagination.DEFAULT_MESSAGE_LIST_LIMIT;
        }
        return messageQueryPort.findByChatId(chatId, limit, before, userId, threadId);
    }

    /**
     * Bundle key {@code error.message.thread_invalid} for {@link com.avandocmsg.messenger.common.i18n.UserMessageSource}.
     */
    public Optional<String> threadInvalidReason(UUID chatId, SendMessageRequest request) {
        if (request == null || request.threadId() == null || request.threadId().isBlank()) {
            return Optional.empty();
        }
        try {
            var threadId = UUID.fromString(request.threadId().trim());
            var root = messageRepositoryPort.findById(MessageId.of(threadId));
            if (root.isEmpty()) {
                return Optional.of("error.message.thread_invalid");
            }
            var message = root.get();
            if (!message.chatId().value().equals(chatId) || message.deleted()) {
                return Optional.of("error.message.thread_invalid");
            }
            if (message.threadId() != null) {
                return Optional.of("error.message.thread_invalid");
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of("error.message.thread_invalid");
        }
    }

    public List<MessageVersionResponse> getMessageVersions(UUID chatId, UUID msgId, UUID userId) {
        if (messageQueryPort == null) {
            return List.of();
        }
        if (messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return List.of();
        }
        return messageQueryPort.findVersions(msgId);
    }

    public List<ReactionResponse> getReactions(UUID chatId, UUID msgId, UUID userId) {
        if (messageQueryPort == null) {
            return List.of();
        }
        if (messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return List.of();
        }
        return messageQueryPort.getReactions(msgId);
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId, UUID userId) {
        if (messageQueryPort == null || !canAccessChat(chatId, userId)) {
            return List.of();
        }
        return messageQueryPort.getPinnedMessages(chatId);
    }

    /** Server-side decrypt for e2ee-* messages (MLS stub on server). */
    public String plaintextPreview(UUID chatId, UUID msgId, UUID userId) {
        if (mlsService == null) {
            return null;
        }
        var visible = messageVisibleToMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId));
        if (visible.isEmpty()) {
            return null;
        }
        var message = visible.get();
        if (!MessageSendSupport.isE2eeType(message.type())) {
            return null;
        }
        return mlsService.decryptContentBase64(chatId, message.content());
    }

    /**
     * Bundle keys {@code error.message.send_denied.*} for {@link com.avandocmsg.messenger.common.i18n.UserMessageSource}.
     */
    public Optional<String> sendBlockedReason(UUID chatId, UUID senderId) {
        var chat = ChatId.of(chatId);
        var sender = UserId.of(senderId);
        if (chatRepositoryPort.memberRole(chat, sender).isEmpty()) {
            return Optional.of("error.message.send_denied.not_member");
        }
        if (chatRepositoryPort.isMemberBanned(chat, sender)) {
            return Optional.of("error.message.send_denied.banned");
        }
        if (isP2PMessagingBlocked(chatId, senderId)) {
            return Optional.of("error.message.send_denied.blocked");
        }
        if (isChannelMemberPostDenied(chatId, senderId)) {
            return Optional.of("error.message.send_denied.channel_readonly");
        }
        return Optional.empty();
    }

    public Optional<String> sendDeniedReason(UUID chatId, UUID senderId, SendMessageRequest request) {
        var acl = sendBlockedReason(chatId, senderId);
        if (acl.isPresent()) {
            return acl;
        }
        if (dlpBridgeGate != null && request != null) {
            return dlpBridgeGate.blockReason(senderId, chatId, request);
        }
        return Optional.empty();
    }

    private boolean isChannelMemberPostDenied(UUID chatId, UUID senderId) {
        if (!chatTypeWire(ChatId.of(chatId)).filter("channel"::equals).isPresent()) {
            return false;
        }
        var role = chatRepositoryPort.memberRole(ChatId.of(chatId), UserId.of(senderId)).orElse(null);
        return "member".equals(role);
    }

    public MessageResponse sendMessage(UUID chatId, UUID senderId, SendMessageRequest request, UUID replyToMsgId) {
        if (sendCoordinator == null) {
            throw new IllegalStateException("message write-path not wired");
        }
        if (sendBlockedReason(chatId, senderId).isPresent()) {
            return null;
        }
        if (dlpBridgeGate != null && request != null && dlpBridgeGate.blockReason(senderId, chatId, request).isPresent()) {
            return null;
        }
        return sendCoordinator.send(chatId, senderId, request, replyToMsgId);
    }

    public MessageResponse forwardMessage(UUID sourceChatId, UUID msgId, UUID userId, UUID targetChatId) {
        if (sendCoordinator == null) {
            throw new IllegalStateException("message write-path not wired");
        }
        if (sendBlockedReason(sourceChatId, userId).isPresent()
            || sendBlockedReason(targetChatId, userId).isPresent()) {
            return null;
        }
        return sendCoordinator.forward(sourceChatId, msgId, userId, targetChatId);
    }

    public MessageResponse editMessage(UUID chatId, UUID msgId, UUID userId, String newContent) {
        if (editCoordinator == null || newContent == null || newContent.isBlank()) {
            return null;
        }
        var chat = ChatId.of(chatId);
        var editor = UserId.of(userId);
        if (chatRepositoryPort.memberRole(chat, editor).isEmpty()) {
            return null;
        }
        if (chatTypeWire(chat).filter("saved"::equals).isPresent()) {
            return null;
        }
        var messageId = MessageId.of(msgId);
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().value().equals(chatId)) {
            return null;
        }
        if (!message.senderId().value().equals(userId) || message.deleted()) {
            return null;
        }
        return editCoordinator.edit(messageId, UserId.of(userId), newContent);
    }

    public boolean deleteMessage(UUID chatId, UUID msgId, UUID userId) {
        if (deleteCoordinator == null) {
            return false;
        }
        if (chatRepositoryPort.memberRole(ChatId.of(chatId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        var messageId = MessageId.of(msgId);
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().value().equals(chatId)) {
            return false;
        }
        return deleteCoordinator.delete(messageId, UserId.of(userId));
    }

    public boolean addReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (reactionCoordinator == null || reaction == null || reaction.isBlank()) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return reactionCoordinator.addReaction(chatId, MessageId.of(msgId), UserId.of(userId), reaction);
    }

    public boolean removeReaction(UUID chatId, UUID msgId, UUID userId, String reaction) {
        if (reactionCoordinator == null) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return reactionCoordinator.removeReaction(chatId, MessageId.of(msgId), UserId.of(userId), reaction);
    }

    public boolean pinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (pinCoordinator == null) {
            return false;
        }
        if (getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId)).isEmpty()) {
            return false;
        }
        return pinCoordinator.pin(chatId, msgId, userId);
    }

    public boolean unpinMessage(UUID chatId, UUID msgId, UUID userId) {
        if (pinCoordinator == null) {
            return false;
        }
        if (!isChatMember(ChatId.of(chatId), UserId.of(userId))) {
            return false;
        }
        return pinCoordinator.unpin(chatId, msgId, userId);
    }

    private Optional<Message> messageVisibleToMember(ChatId chatId, MessageId messageId, UserId viewerId) {
        if (!canAccessChat(chatId.value(), viewerId.value())) {
            return Optional.empty();
        }
        var message = messageRepositoryPort.findById(messageId).orElse(null);
        if (message == null || !message.chatId().equals(chatId)) {
            return Optional.empty();
        }
        if (isMutuallyBlocked(viewerId.value(), message.senderId().value())) {
            return Optional.empty();
        }
        return Optional.of(message);
    }

    private boolean isMutuallyBlocked(UUID a, UUID b) {
        if (blockRepositoryPort == null) {
            return false;
        }
        var userA = UserId.of(a);
        var userB = UserId.of(b);
        return blockRepositoryPort.exists(userA, userB) || blockRepositoryPort.exists(userB, userA);
    }

    private boolean isP2PMessagingBlocked(UUID chatId, UUID senderId) {
        if (blockRepositoryPort == null) {
            return false;
        }
        var chat = ChatId.of(chatId);
        var sender = UserId.of(senderId);
        if (!chatTypeWire(chat).filter("p2p"::equals).isPresent()) {
            return false;
        }
        return chatRepositoryPort.findOtherP2pMember(chat, sender)
            .map(peer -> blockRepositoryPort.exists(sender, peer)
                || blockRepositoryPort.exists(peer, sender))
            .orElse(false);
    }

    private Optional<String> chatTypeWire(ChatId chatId) {
        return chatRepositoryPort.findById(chatId).map(c -> wireChatType(c.type()));
    }

    private static String wireChatType(ChatType type) {
        return type.name().toLowerCase();
    }
}
