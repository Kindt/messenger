package com.avandocmsg.messenger.core.bootstrap;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.core.application.IndexerEventPublisher;
import com.avandocmsg.messenger.core.application.MessageEditCoordinator;
import com.avandocmsg.messenger.core.application.MessageDeleteCoordinator;
import com.avandocmsg.messenger.core.application.MessagePinCoordinator;
import com.avandocmsg.messenger.core.application.MessageReactionCoordinator;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatBanAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPersistenceAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcConferenceAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcDeviceAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcLiveSessionAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatReadStateAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcLegalHoldAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReadReceiptAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMigrationImportJobAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrganizationLookupAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcUserLookupAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcBlockRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcContactRepositoryAdapter;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.core.adapter.persistence.FilePublicLinkPortAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcFileMetadataAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrganizationRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcDirectorySyncRunJdbcRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcDirectorySyncRunRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcOrgUserDirectoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcScimGroupRepositoryAdapter;
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
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.DirectorySyncRunRepositoryPort;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import io.lettuce.core.api.sync.RedisCommands;

import io.minio.MinioClient;

import java.time.Clock;

import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

/** Composition-root helpers for hexagonal wiring (Phase 2a+). */
public final class CoreModule {
    private CoreModule() {
    }

    public static ChatRepositoryPort chatRepositoryPort(DataSource dataSource) {
        return new JdbcChatRepositoryAdapter(dataSource);
    }

    public static ChatApplicationService chatApplicationService(DataSource dataSource) {
        return new ChatApplicationService(chatRepositoryPort(dataSource));
    }

    public static MessageRepositoryPort messageRepositoryPort(DataSource dataSource) {
        return messageRepositoryAdapter(dataSource);
    }

    public static MessageQueryPort messageQueryPort(DataSource dataSource) {
        return messageRepositoryAdapter(dataSource);
    }

    private static JdbcMessageRepositoryAdapter messageRepositoryAdapter(DataSource dataSource) {
        return new JdbcMessageRepositoryAdapter(dataSource);
    }

    public static MessageSendCoordinator messageSendCoordinator(
            DataSource dataSource,
            MlsService mlsService,
            MlsMigrationService mlsMigrationService,
            NatsOutboundPort natsOutbound,
            UuidGenerator uuidGenerator,
            ReadCachePort readCachePort) {
        return new MessageSendCoordinator(
            messageRepositoryPort(dataSource),
            chatRepositoryPort(dataSource),
            mlsService,
            mlsMigrationService,
            natsOutbound,
            uuidGenerator,
            readCachePort);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource) {
        return new MessageApplicationService(messageRepositoryPort(dataSource), chatRepositoryPort(dataSource));
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator) {
        return messageApplicationService(dataSource, blockRepositoryPort, sendCoordinator,
            editCoordinator, null, null, null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator) {
        return messageApplicationService(dataSource, blockRepositoryPort, sendCoordinator,
            editCoordinator, deleteCoordinator, reactionCoordinator, null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator,
                                                                      MessagePinCoordinator pinCoordinator,
                                                                      MessageQueryPort messageQueryPort,
                                                                      com.avandocmsg.messenger.api.mls.MlsService mlsService) {
        return messageApplicationService(dataSource, blockRepositoryPort, sendCoordinator, editCoordinator,
            deleteCoordinator, reactionCoordinator, pinCoordinator, messageQueryPort, mlsService, null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator,
                                                                      MessagePinCoordinator pinCoordinator,
                                                                      MessageQueryPort messageQueryPort,
                                                                      com.avandocmsg.messenger.api.mls.MlsService mlsService,
                                                                      com.avandocmsg.messenger.api.compliance.DlpBridgeGate dlpBridgeGate) {
        return new MessageApplicationService(messageRepositoryPort(dataSource), chatRepositoryPort(dataSource),
            blockRepositoryPort,
            sendCoordinator, editCoordinator, deleteCoordinator, reactionCoordinator, pinCoordinator,
            messageQueryPort, mlsService, dlpBridgeGate);
    }

    public static com.avandocmsg.messenger.core.port.FederationTrustPort federationTrustPort(DataSource dataSource) {
        return new com.avandocmsg.messenger.core.adapter.persistence.JdbcFederationTrustAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ChatPollPort chatPollPort(DataSource dataSource) {
        return new com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPollAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ScheduledMessagePort scheduledMessagePort(DataSource dataSource) {
        return new com.avandocmsg.messenger.core.adapter.persistence.JdbcScheduledMessageAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.MessageReminderPort messageReminderPort(DataSource dataSource) {
        return new com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReminderAdapter(dataSource);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator,
                                                                      MessageEditCoordinator editCoordinator,
                                                                      MessageDeleteCoordinator deleteCoordinator,
                                                                      MessageReactionCoordinator reactionCoordinator,
                                                                      MessagePinCoordinator pinCoordinator) {
        return messageApplicationService(dataSource, blockRepositoryPort, sendCoordinator,
            editCoordinator, deleteCoordinator, reactionCoordinator, pinCoordinator,
            messageQueryPort(dataSource), null);
    }

    public static MessageApplicationService messageApplicationService(DataSource dataSource,
                                                                      BlockRepositoryPort blockRepositoryPort,
                                                                      MessageSendCoordinator sendCoordinator) {
        return messageApplicationService(dataSource, blockRepositoryPort, sendCoordinator, null);
    }

    public static MessageEditCoordinator messageEditCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageEditCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static MessageEditCoordinator messageEditCoordinator(DataSource dataSource, IndexerEventPublisher indexer) {
        return new MessageEditCoordinator(messageRepositoryPort(dataSource), indexer);
    }

    public static MessageDeleteCoordinator messageDeleteCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageDeleteCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static MessageDeleteCoordinator messageDeleteCoordinator(
            DataSource dataSource,
            NatsOutboundPort natsOutbound,
            IndexerEventPublisher indexer) {
        return new MessageDeleteCoordinator(messageRepositoryPort(dataSource), natsOutbound, indexer);
    }

    public static IndexerEventPublisher indexerEventPublisher(NatsOutboundPort natsOutbound, BooleanSupplier indexerAvailable) {
        return new IndexerEventPublisher(natsOutbound, indexerAvailable);
    }

    public static ScimGroupRepositoryPort scimGroupRepositoryPort(DataSource dataSource) {
        return new JdbcScimGroupRepositoryAdapter(dataSource);
    }

    public static OrgUserDirectoryPort orgUserDirectoryPort(DataSource dataSource) {
        return new JdbcOrgUserDirectoryAdapter(dataSource);
    }

    public static DirectorySyncRunRepositoryPort directorySyncRunRepositoryPort(DataSource dataSource) {
        return new JdbcDirectorySyncRunRepositoryAdapter(
            new JdbcDirectorySyncRunJdbcRepository(dataSource));
    }

    public static MessageReactionCoordinator messageReactionCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessageReactionCoordinator(messageRepositoryPort(dataSource), natsOutbound);
    }

    public static BlockRepositoryPort blockRepositoryPort(DataSource dataSource) {
        return new JdbcBlockRepositoryAdapter(dataSource);
    }

    public static ContactRepositoryPort contactRepositoryPort(DataSource dataSource) {
        return new JdbcContactRepositoryAdapter(dataSource);
    }

    public static MessagePinCoordinator messagePinCoordinator(DataSource dataSource, NatsOutboundPort natsOutbound) {
        return new MessagePinCoordinator(messageRepositoryPort(dataSource), natsOutbound);
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
                                                                AppConfig appConfig,
                                                                com.avandocmsg.messenger.core.port.NatsOutboundPort natsOutbound) {
        return new UserApplicationService(
            userRepositoryPort(dataSource),
            savedChatPort(dataSource, uuidGenerator),
            readCachePort,
            appConfig,
            new com.avandocmsg.messenger.core.application.UserPresencePublisher(natsOutbound));
    }

    public static PublicLinkPort publicLinkPort(DataSource dataSource, UuidGenerator uuidGenerator) {
        return new FilePublicLinkPortAdapter(dataSource, uuidGenerator);
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
                                                                MessageQueryPort messageQueryPort,
                                                                ObjectStoragePort objectStoragePort,
                                                                UuidGenerator uuidGenerator,
                                                                AppConfig appConfig) {
        return new FileApplicationService(
            fileMetadataPort(dataSource),
            messageQueryPort,
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

    public static com.avandocmsg.messenger.core.port.ChatPersistencePort chatPersistencePort(DataSource dataSource,
                                                                                              DataSource readDataSource,
                                                                                              Clock clock,
                                                                                              UuidGenerator uuidGenerator,
                                                                                              int queryTimeoutSeconds) {
        return new JdbcChatPersistenceAdapter(dataSource, readDataSource, clock, uuidGenerator, queryTimeoutSeconds);
    }

    public static com.avandocmsg.messenger.core.port.ChatReadStatePort chatReadStatePort(DataSource dataSource) {
        return new JdbcChatReadStateAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.MessageReadReceiptPort messageReadReceiptPort(DataSource dataSource) {
        return new JdbcMessageReadReceiptAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.UserLookupPort userLookupPort(DataSource dataSource) {
        return new JdbcUserLookupAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ChatBanPort chatBanPort(DataSource dataSource, Clock clock,
                                                                             UuidGenerator uuidGenerator) {
        return new JdbcChatBanAdapter(dataSource, clock, uuidGenerator);
    }

    public static com.avandocmsg.messenger.core.port.AuditPort auditPort(DataSource dataSource) {
        return new JdbcAuditAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ExportJobPort exportJobPort(DataSource dataSource) {
        return new JdbcExportJobAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ExportJobPort exportJobPort(ExportJobRepository exportJobRepository) {
        return new JdbcExportJobAdapter(exportJobRepository.jdbcRepository());
    }

    public static com.avandocmsg.messenger.core.port.RetentionPolicyPort retentionPolicyPort(DataSource dataSource) {
        return new JdbcRetentionPolicyAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort chatRetentionPolicyPort(
        DataSource dataSource) {
        return new JdbcChatRetentionPolicyAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.LegalHoldPort legalHoldPort(DataSource dataSource) {
        return new JdbcLegalHoldAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.OrganizationLookupPort organizationLookupPort(
        DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        return new JdbcOrganizationLookupAdapter(dataSource, clock, uuidGenerator);
    }

    public static com.avandocmsg.messenger.core.port.MigrationImportJobPort migrationImportJobPort(DataSource dataSource) {
        return new JdbcMigrationImportJobAdapter(dataSource);
    }

    public static com.avandocmsg.messenger.core.port.ConferencePort conferencePort(
        DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        return new JdbcConferenceAdapter(dataSource, appConfig, uuidGenerator);
    }

    public static com.avandocmsg.messenger.core.port.LiveSessionPort liveSessionPort(
        DataSource dataSource, AppConfig appConfig, UuidGenerator uuidGenerator) {
        return new JdbcLiveSessionAdapter(dataSource, appConfig, uuidGenerator);
    }

    public static com.avandocmsg.messenger.core.port.LiveSessionPort liveSessionPort(
        com.avandocmsg.messenger.api.repository.LiveSessionRepository liveSessionRepository) {
        return new JdbcLiveSessionAdapter(liveSessionRepository.jdbcRepository());
    }

    public static com.avandocmsg.messenger.core.port.DevicePort devicePort(DataSource dataSource, Clock clock,
                                                                           UuidGenerator uuidGenerator) {
        return new JdbcDeviceAdapter(dataSource, clock, uuidGenerator);
    }
}
