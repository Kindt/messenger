package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** JDBC adapter for {@link ChatRepositoryPort} (reads {@code chats} without membership filter). */
public final class JdbcChatRepositoryAdapter implements ChatRepositoryPort {
    private final JdbcChatJdbcRepository jdbc;

    public JdbcChatRepositoryAdapter(DataSource dataSource) {
        this(new JdbcChatJdbcRepository(dataSource, null, Clock.systemUTC(),
            com.avandocmsg.messenger.core.port.UuidGenerator.standard(), 0));
    }

    public JdbcChatRepositoryAdapter(JdbcChatJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Chat> findById(ChatId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.findByIdBasic(id.value()).map(JdbcChatRepositoryAdapter::mapRow);
    }

    @Override
    public boolean isMember(ChatId chatId, UserId userId) {
        return memberRole(chatId, userId).isPresent();
    }

    @Override
    public Optional<String> memberRole(ChatId chatId, UserId userId) {
        if (chatId == null || userId == null) {
            return Optional.empty();
        }
        var role = jdbc.getMemberRole(chatId.value(), userId.value());
        return role != null ? Optional.of(role) : Optional.empty();
    }

    @Override
    public boolean isMemberBanned(ChatId chatId, UserId userId) {
        if (chatId == null || userId == null) {
            return false;
        }
        return jdbc.isMemberBanned(chatId.value(), userId.value());
    }

    @Override
    public List<UserId> listMemberUserIds(ChatId chatId) {
        if (chatId == null) {
            return List.of();
        }
        return jdbc.listMemberUserIds(chatId.value()).stream().map(UserId::of).toList();
    }

    @Override
    public Optional<UserId> findOtherP2pMember(ChatId chatId, UserId userId) {
        if (chatId == null || userId == null) {
            return Optional.empty();
        }
        return jdbc.findOtherP2PMember(chatId.value(), userId.value()).map(UserId::of);
    }

    private static Chat mapRow(JdbcChatJdbcRepository.BasicChatRow row) {
        return new Chat(
            ChatId.of(row.id()),
            row.title(),
            mapChatType(row.type()),
            row.createdAt());
    }

    private static ChatType mapChatType(String typeRaw) {
        if (typeRaw == null) {
            return ChatType.GROUP;
        }
        if ("p2p".equalsIgnoreCase(typeRaw)) {
            return ChatType.P2P;
        }
        if ("saved".equalsIgnoreCase(typeRaw)) {
            return ChatType.SAVED;
        }
        if ("channel".equalsIgnoreCase(typeRaw)) {
            return ChatType.CHANNEL;
        }
        return ChatType.GROUP;
    }
}
