package com.avandocmsg.messenger.core.bootstrap;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcFileMetadataAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrganizationRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserRepositoryAdapter;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import javax.sql.DataSource;

/** Composition-root helpers for hexagonal wiring (Phase 2a+). */
public final class CoreModule {
    private CoreModule() {
    }

    public static ChatRepositoryPort chatRepositoryPort(DataSource dataSource) {
        return new JdbcChatRepositoryAdapter(dataSource);
    }

    public static ChatApplicationService chatApplicationService(DataSource dataSource, ChatRepository legacy) {
        return new ChatApplicationService(chatRepositoryPort(dataSource), legacy);
    }

    public static MessageRepositoryPort messageRepositoryPort(DataSource dataSource) {
        return new JdbcMessageRepositoryAdapter(dataSource);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository legacy) {
        return new MessageApplicationService(messageRepositoryPort(dataSource), legacy);
    }

    public static UserRepositoryPort userRepositoryPort(DataSource dataSource) {
        return new JdbcUserRepositoryAdapter(dataSource);
    }

    public static UserApplicationService userApplicationService(DataSource dataSource) {
        return new UserApplicationService(userRepositoryPort(dataSource));
    }

    public static FileMetadataPort fileMetadataPort(DataSource dataSource) {
        return new JdbcFileMetadataAdapter(dataSource);
    }

    public static FileApplicationService fileApplicationService(DataSource dataSource, MessageRepository legacy) {
        return new FileApplicationService(fileMetadataPort(dataSource), legacy);
    }

    public static OrganizationRepositoryPort organizationRepositoryPort(DataSource dataSource) {
        return new JdbcOrganizationRepositoryAdapter(dataSource);
    }

    public static OrganizationApplicationService organizationApplicationService(DataSource dataSource) {
        return new OrganizationApplicationService(organizationRepositoryPort(dataSource));
    }
}
