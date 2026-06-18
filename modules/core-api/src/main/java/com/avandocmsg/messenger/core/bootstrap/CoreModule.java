package com.avandocmsg.messenger.core.bootstrap;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.core.application.MessageEditCoordinator;
import com.avandocmsg.messenger.core.application.MessageDeleteCoordinator;
import com.avandocmsg.messenger.core.application.MessagePinCoordinator;
import com.avandocmsg.messenger.core.application.MessageReactionCoordinator;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.api.repository.BlockRepository;
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
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.adapter.cache.RedisReadCacheAdapter;
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
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import io.lettuce.core.api.sync.RedisCommands;

import io.minio.MinioClient;

import java.time.Clock;

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

    public static MessageSendCoordinator messageSendCoordinator(
            DataSource dataSource,
            ChatRepository chatRepository,
            MlsService mlsService,
            MlsMigrationService mlsMigrationService,
            NatsOutboundPort natsOutbound,
            UuidGenerator uuidGenerator,
            ReadCachePort readCachePort) {
        return new MessageSendCoordinator(
            messageRepositoryPort(dataSource),
            chatRepository,
            mlsService,
            mlsMigrationService,
            natsOutbound,
            uuidGenerator,
            readCachePort);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository chatRepository) {
        return new MessageApplicationService(messageRepositoryPort(dataSource), chatRepository);
    }

    public static MessageEditCoordinator messageEditCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageEditCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static MessageDeleteCoordinator messageDeleteCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageDeleteCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static MessageReactionCoordinator messageReactionCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageReactionCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static MessagePinCoordinator messagePinCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessagePinCoordinator(new MessageRepository(dataSource, Clock.systemUTC()), natsOutbound);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository chatRepository,
                                                                      BlockRepository blockRepository,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator) {
        return messageApplicationService(dataSource, chatRepository, blockRepository, sendCoordinator,
            editCoordinator, null, null, null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository chatRepository,
                                                                      BlockRepository blockRepository,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator) {
        return messageApplicationService(dataSource, chatRepository, blockRepository, sendCoordinator,
            editCoordinator, deleteCoordinator, reactionCoordinator, null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository chatRepository,
                                                                      BlockRepository blockRepository,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator,
                                                                      MessagePinCoordinator pinCoordinator) {
        return new MessageApplicationService(messageRepositoryPort(dataSource), chatRepository, blockRepository,
            sendCoordinator, editCoordinator, deleteCoordinator, reactionCoordinator, pinCoordinator);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource, ChatRepository chatRepository,
                                                                      BlockRepository blockRepository,
                                                                      MessageSendCoordinator sendCoordinator) {
        return messageApplicationService(dataSource, chatRepository, blockRepository, sendCoordinator, null);
    }

    public static UserRepositoryPort userRepositoryPort(DataSource dataSource) {
        return new JdbcUserRepositoryAdapter(dataSource);
    }

    public static SavedChatPort savedChatPort(DataSource dataSource, UuidGenerator uuidGenerator) {
        return new JdbcSavedChatAdapter(dataSource, uuidGenerator);
    }

    public static UserApplicationService userApplicationService(DataSource dataSource,
                                                                UuidGenerator uuidGenerator,
                                                                ReadCachePort readCachePort,
                                                                AppConfig appConfig) {
        return new UserApplicationService(
            userRepositoryPort(dataSource), savedChatPort(dataSource, uuidGenerator), readCachePort, appConfig);
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
            appConfig.mediaMaxUploadBytes(),
            appConfig.fileDedupEnabled(),
            appConfig.fileUploadMaxConcurrent());
    }

    public static OrganizationRepositoryPort organizationRepositoryPort(DataSource dataSource,
                                                                      UuidGenerator uuidGenerator) {
        return new JdbcOrganizationRepositoryAdapter(dataSource, uuidGenerator);
    }

    public static OrganizationApplicationService organizationApplicationService(DataSource dataSource,
                                                                                UuidGenerator uuidGenerator) {
        return new OrganizationApplicationService(organizationRepositoryPort(dataSource, uuidGenerator));
    }

    public static ReadCachePort readCachePort(RedisCommands<String, String> redis, AppConfig appConfig) {
        if (!appConfig.redisReadCacheEnabled() || redis == null) {
            return NoOpReadCacheAdapter.INSTANCE;
        }
        return new RedisReadCacheAdapter(redis, appConfig);
    }
}
