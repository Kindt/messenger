package com.avandocmsg.messenger.core.bootstrap;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.FilePublicLinkRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.adapter.persistence.FilePublicLinkPortAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcFileMetadataAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrganizationRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcSavedChatAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.storage.FileProxyObjectStorageAdapter;
import com.avandocmsg.messenger.core.adapter.storage.MinioObjectStorageAdapter;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import io.minio.MinioClient;

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

    public static SavedChatPort savedChatPort(DataSource dataSource) {
        return new JdbcSavedChatAdapter(dataSource);
    }

    public static UserApplicationService userApplicationService(DataSource dataSource) {
        return new UserApplicationService(userRepositoryPort(dataSource), savedChatPort(dataSource));
    }

    public static PublicLinkPort publicLinkPort(FilePublicLinkRepository legacy) {
        return new FilePublicLinkPortAdapter(legacy);
    }

    public static FileMetadataPort fileMetadataPort(DataSource dataSource) {
        return new JdbcFileMetadataAdapter(dataSource);
    }

    public static ObjectStoragePort objectStoragePort(AppConfig appConfig, MinioClient minioClient, FileProxy fileProxy) {
        if ("http".equalsIgnoreCase(appConfig.fileProxyMode())) {
            return new FileProxyObjectStorageAdapter(fileProxy);
        }
        return new MinioObjectStorageAdapter(minioClient, appConfig.minioBucket());
    }

    public static FileApplicationService fileApplicationService(DataSource dataSource,
                                                                MessageRepository legacy,
                                                                ObjectStoragePort objectStoragePort,
                                                                UuidGenerator uuidGenerator,
                                                                AppConfig appConfig) {
        return new FileApplicationService(
            fileMetadataPort(dataSource),
            legacy,
            objectStoragePort,
            uuidGenerator,
            appConfig.mediaMaxUploadBytes());
    }

    public static OrganizationRepositoryPort organizationRepositoryPort(DataSource dataSource,
                                                                      UuidGenerator uuidGenerator) {
        return new JdbcOrganizationRepositoryAdapter(dataSource, uuidGenerator);
    }

    public static OrganizationApplicationService organizationApplicationService(DataSource dataSource,
                                                                                UuidGenerator uuidGenerator) {
        return new OrganizationApplicationService(organizationRepositoryPort(dataSource, uuidGenerator));
    }
}
