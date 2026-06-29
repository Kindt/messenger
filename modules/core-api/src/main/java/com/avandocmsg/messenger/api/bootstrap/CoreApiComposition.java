package com.avandocmsg.messenger.api.bootstrap;

import com.avandocmsg.messenger.api.admin.PurgeStatusService;
import com.avandocmsg.messenger.api.admin.fleet.FleetSnapshotService;
import com.avandocmsg.messenger.api.admin.fleet.FleetTargetRegistry;
import com.avandocmsg.messenger.api.admin.ui.AdminServerStatsService;
import com.avandocmsg.messenger.api.admin.ui.AdminUiManifest;
import com.avandocmsg.messenger.api.admin.ui.ClasspathAdminStaticServlet;
import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
import com.avandocmsg.messenger.api.auth.AuthService;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.api.bots.BotRepository;
import com.avandocmsg.messenger.api.bots.BotService;
import com.avandocmsg.messenger.api.cache.ReadCacheInvalidationSubscriber;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.ReadReceiptService;
import com.avandocmsg.messenger.api.chats.bans.ChatBanService;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.DatabaseConfig;
import com.avandocmsg.messenger.api.config.JerseyConfig;
import com.avandocmsg.messenger.api.config.MinioConfig;
import com.avandocmsg.messenger.api.config.NatsConfig;
import com.avandocmsg.messenger.api.config.OrganizationRoutingDataSource;
import com.avandocmsg.messenger.api.config.OrganizationShardRouter;
import com.avandocmsg.messenger.api.config.RedisConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.api.config.SolrClientFactory;
import com.avandocmsg.messenger.api.contacts.ContactService;
import com.avandocmsg.messenger.api.crypto.CryptoProvider;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.crypto.KeyPackageRepository;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportAutoQueueOnSuggested;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportReplayCompleteSubscriber;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.api.export.ExportSuggestedSubscriber;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.files.HttpFileProxy;
import com.avandocmsg.messenger.api.files.MinioFileProxy;
import com.avandocmsg.messenger.api.hotplug.IndexerHotPlugMonitor;
import com.avandocmsg.messenger.api.metrics.ExportJobsDbCollector;
import com.avandocmsg.messenger.api.metrics.ExportMetrics;
import com.avandocmsg.messenger.api.metrics.ReadCacheMetrics;
import com.avandocmsg.messenger.api.mls.MlsGroupManager;
import com.avandocmsg.messenger.api.mls.MlsGroupStateRepository;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.MlsWireHandler;
import com.avandocmsg.messenger.api.mls.MlsWirePublisher;
import com.avandocmsg.messenger.api.mls.MlsWireSubscriber;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.api.search.MessageSearchService;
import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.common.nats.JetStreamMessagingSetup;
import com.avandocmsg.messenger.core.adapter.messaging.NatsConnectionOutbound;
import com.avandocmsg.messenger.core.adapter.mls.OpenMlsBindingFactory;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageMentionRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReadRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageWriteRepository;
import com.avandocmsg.messenger.core.application.MessageMentionCoordinator;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import io.minio.MinioClient;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import jakarta.servlet.ServletContext;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.solr.client.solrj.SolrClient;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Shared composition root for embedded Tomcat and external servlet containers (WAR).
 */
public class CoreApiComposition {
    static final String SERVLET_NAME = "jersey";

    private static final Logger log = LoggerFactory.getLogger(CoreApiComposition.class);

    private final AppConfig appConfig;
    private final DataSource dataSource;
    private final Connection natsConnection;
    private final MinioClient minioClient;
    private final TokenValidator tokenValidator;
    private final ContactRepositoryPort contactRepositoryPort;
    private final BlockRepositoryPort blockRepositoryPort;
    private final ChatPersistencePort chatPersistencePort;
    private final JdbcMessageRepositoryAdapter messagePersistence;
    private final E2EEService e2eeService;
    private final KeyPackageRepository keyPackageRepository;
    private final SessionRepository sessionRepository;
    private final MlsService mlsService;
    private final FileProxy fileProxy;
    private final ExportJobPort exportJobPort;
    private final ExportJobEnqueuer exportJobEnqueuer;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final ExportSuggestedHandler exportSuggestedHandler;

    private RedisConfig redisConfig;
    private RedisProbe redisProbe;
    private ReadCachePort readCachePort;
    private SolrClient solrClient;
    private ExportReplayCompleteSubscriber exportCompleteSubscriber;
    private ExportSuggestedSubscriber exportSuggestedSubscriber;
    private MlsWireSubscriber mlsWireSubscriber;
    private ReadCacheInvalidationSubscriber readCacheInvalidationSubscriber;
    private IndexerHotPlugMonitor indexerHotPlugMonitor;
    private com.avandocmsg.messenger.api.directory.DirectorySyncScheduler directorySyncScheduler;
    private com.avandocmsg.messenger.api.directory.DirectorySyncService directorySyncService;
    private com.avandocmsg.messenger.api.admin.MigrationImportScheduler migrationImportScheduler;
    private MessageSendCoordinator messageSendCoordinator;
    private com.avandocmsg.messenger.api.messages.ScheduledMessageScheduler scheduledMessageScheduler;
    private com.avandocmsg.messenger.api.messages.MessageReminderScheduler messageReminderScheduler;
    private com.avandocmsg.messenger.core.port.ScheduledMessagePort scheduledMessagePort;
    private com.avandocmsg.messenger.core.port.MessageReminderPort messageReminderPort;

    public CoreApiComposition() {
        this.appConfig = new AppConfig();
        appConfig.validateStartup();
        log.info("Starting AvandocMsg.Messenger core-api v{}", appConfig.version());
        CryptoProvider.ensureLoaded();
        var databaseConfig = new DatabaseConfig(appConfig);
        var primaryDs = databaseConfig.dataSource();
        this.dataSource = databaseConfig.shardDataSource()
            .<DataSource>map(shard -> new OrganizationRoutingDataSource(primaryDs, shard))
            .orElse(primaryDs);
        var readDs = databaseConfig.readDataSource().orElse(null);
        databaseConfig.warnIfPoolOversubscribed();
        OrganizationShardRouter.logShardConfig(databaseConfig);
        runMigrations();
        this.natsConnection = new NatsConfig(appConfig).connection();
        if (appConfig.natsJetstream()) {
            try {
                JetStreamMessagingSetup.ensureSendStream(this.natsConnection);
            } catch (Exception e) {
                throw new RuntimeException("JetStream stream setup failed (MESSAGING / msg.send)", e);
            }
        }
        this.minioClient = new MinioConfig(appConfig).client();
        this.clock = Clock.systemUTC();
        this.uuidGenerator = UuidGenerator.standard();
        this.tokenValidator = new TokenValidator(appConfig, clock);
        this.contactRepositoryPort = CoreModule.contactRepositoryPort(dataSource);
        this.blockRepositoryPort = CoreModule.blockRepositoryPort(dataSource);
        this.chatPersistencePort = CoreModule.chatPersistencePort(
            dataSource, readDs, clock, uuidGenerator, appConfig.apiJdbcQueryTimeoutSeconds());
        var messageWriteRepo = new JdbcMessageWriteRepository(
            dataSource, readDs, clock, appConfig.apiJdbcQueryTimeoutSeconds());
        var messageReadRepo = new JdbcMessageReadRepository(
            dataSource, readDs, appConfig.apiJdbcQueryTimeoutSeconds());
        var mentionRepositoryPort = new JdbcMessageMentionRepositoryAdapter(dataSource);
        this.messagePersistence = new JdbcMessageRepositoryAdapter(
            messageReadRepo, messageWriteRepo, mentionRepositoryPort);
        this.e2eeService = new E2EEService();
        this.keyPackageRepository = new KeyPackageRepository(dataSource, clock, uuidGenerator);
        this.sessionRepository = new SessionRepository(dataSource, clock, uuidGenerator);
        this.mlsService = new MlsService(sessionRepository, e2eeService);
        this.fileProxy = createFileProxy();
        this.exportJobPort = CoreModule.exportJobPort(dataSource);
        var auditPort = CoreModule.auditPort(dataSource);
        var natsOutbound = new NatsConnectionOutbound(natsConnection, jetStreamOptional());
        this.exportJobEnqueuer = new ExportJobEnqueuer(
            exportJobPort,
            auditPort,
            natsOutbound,
            uuidGenerator
        );
        var exportAutoQueue = appConfig.exportAutoQueueOnSuggestedEnabled()
            ? Optional.of(new ExportAutoQueueOnSuggested(
                appConfig, exportJobEnqueuer, exportJobPort, chatPersistencePort, auditPort))
            : Optional.<ExportAutoQueueOnSuggested>empty();
        this.exportSuggestedHandler = new ExportSuggestedHandler(auditPort, exportAutoQueue);
        ExportMetrics.ensureRegistered();
        ReadCacheMetrics.ensureRegistered();
        if (appConfig.rateLimitAuthEnabled() || appConfig.redisReadCacheEnabled()) {
            this.redisConfig = new RedisConfig(appConfig);
            if (appConfig.rateLimitAuthEnabled()) {
                log.info("Auth rate limiting enabled (Redis)");
            }
            if (appConfig.redisReadCacheEnabled()) {
                log.info("Redis read cache enabled");
            }
        } else {
            this.redisConfig = null;
        }
        this.redisProbe = new RedisProbe(appConfig, redisConfig);
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    @FunctionalInterface
    private interface HttpServletRegistration {
        void register(String jerseyName, ServletContainer jerseyServlet, ClasspathAdminStaticServlet adminServlet);
    }

    public void wireToServletContext(ServletContext servletContext) {
        wireApplicationStack((jerseyName, jerseyServlet, adminServlet) -> {
            var jerseyReg = servletContext.addServlet(jerseyName, jerseyServlet);
            if (jerseyReg != null) {
                jerseyReg.addMapping("/api/*");
            }
            var adminReg = servletContext.addServlet("adminUiStatic", adminServlet);
            if (adminReg != null) {
                adminReg.addMapping("/admin", "/admin/*");
            }
        });
    }

    /** Embedded Tomcat: register servlets on {@link Context} before {@code tomcat.start()}. */
    public void wireToEmbeddedTomcatContext(Context tomcatContext) {
        wireApplicationStack((jerseyName, jerseyServlet, adminServlet) -> {
            var jerseyWrapper = Tomcat.addServlet(tomcatContext, jerseyName, jerseyServlet);
            jerseyWrapper.setLoadOnStartup(1);
            tomcatContext.addServletMappingDecoded("/api/*", jerseyName);
            var adminWrapper = Tomcat.addServlet(tomcatContext, "adminUiStatic", adminServlet);
            adminWrapper.setLoadOnStartup(2);
            tomcatContext.addServletMappingDecoded("/admin/*", "adminUiStatic");
        });
    }

    private void wireApplicationStack(HttpServletRegistration registration) {
        var solrBinding = SolrClientFactory.create(appConfig);
        this.solrClient = solrBinding.client();
        var exportFileAccess = new ExportFileAccess(appConfig, Optional.of(minioClient));
        var chatReadStatePort = CoreModule.chatReadStatePort(dataSource);
        var messageReadReceiptPort = CoreModule.messageReadReceiptPort(dataSource);
        var userLookupPort = CoreModule.userLookupPort(dataSource);
        var auditPort = CoreModule.auditPort(dataSource);
        var retentionPolicyPort = CoreModule.retentionPolicyPort(dataSource);
        var chatRetentionPolicyPort = CoreModule.chatRetentionPolicyPort(dataSource);
        var legalHoldPort = CoreModule.legalHoldPort(dataSource);
        var organizationLookupPort = CoreModule.organizationLookupPort(dataSource, this.clock, this.uuidGenerator);
        var migrationImportJobPort = CoreModule.migrationImportJobPort(dataSource);
        var federationTrustPort = CoreModule.federationTrustPort(dataSource);
        var federationMemberGuard = new com.avandocmsg.messenger.api.federation.FederationMemberGuard(
            federationTrustPort, userLookupPort);
        var federationStatusService = new com.avandocmsg.messenger.api.platform.FederationStatusService(
            federationTrustPort, organizationLookupPort);
        var devicePort = CoreModule.devicePort(dataSource, this.clock, this.uuidGenerator);
        var messageRepoPort = messagePersistence;
        var messageQueryPort = messagePersistence;
        var messageSearchService = new MessageSearchService(appConfig, messageQueryPort, chatPersistencePort,
            solrBinding.client(), solrBinding.cloudMode());

        var authRateLimiter = redisConfig != null
            ? AuthRateLimiter.redis(redisConfig.sync(), appConfig)
            : AuthRateLimiter.noop();
        this.readCachePort = CoreModule.readCachePort(
            redisConfig != null ? redisConfig.sync() : null, appConfig);
        var natsOutbound = new NatsConnectionOutbound(natsConnection, jetStreamOptional());
        var adminManifest = AdminUiManifest.load(CoreApiComposition.class.getClassLoader());
        var adminStatsJdbc = new com.avandocmsg.messenger.core.adapter.persistence.JdbcAdminStatsJdbcRepository(dataSource);
        ExportJobsDbCollector.registerDefault(adminStatsJdbc, appConfig.exportProcessingStaleMinutes());
        var adminServerStatsService = new AdminServerStatsService(adminStatsJdbc, appConfig, natsOutbound, redisProbe);
        var fleetTargetRegistry = FleetTargetRegistry.fromJson(appConfig.fleetTargetsJson());
        var fleetHotPlugRegistry = indexerHotPlugMonitor != null ? indexerHotPlugMonitor.registry() : null;
        var fleetSnapshotService = new FleetSnapshotService(
            fleetTargetRegistry,
            adminServerStatsService,
            appConfig,
            fleetHotPlugRegistry,
            appConfig.hotplugHeartbeatTtlMs()
        );
        var chatService = new ChatService(chatPersistencePort, blockRepositoryPort, chatReadStatePort,
            messageRepoPort, messageQueryPort, natsOutbound, this.clock, this.uuidGenerator, readCachePort, appConfig,
            federationMemberGuard);
        var readReceiptService = new ReadReceiptService(messageReadReceiptPort, chatPersistencePort,
            messageRepoPort, chatReadStatePort, userLookupPort, auditPort, natsOutbound,
            appConfig, this.clock, readCachePort);
        var mlsGroupStateRepository = new MlsGroupStateRepository(dataSource, this.clock);
        var mlsWirePublisher = new MlsWirePublisher(natsOutbound, appConfig);
        var mlsGroupManager = new MlsGroupManager(mlsGroupStateRepository, mlsService,
            this.uuidGenerator, this.clock, mlsWirePublisher);
        var mlsMigrationService = new MlsMigrationService(adminStatsJdbc, mlsGroupManager, chatPersistencePort);
        var openMlsBindingPort = OpenMlsBindingFactory.create(appConfig, mlsService);
        var chatApplicationService = CoreModule.chatApplicationService(dataSource);
        var userApplicationService = CoreModule.userApplicationService(
            dataSource, this.uuidGenerator, readCachePort, appConfig, natsOutbound);
        var avatarAccessTokenService = CoreModule.avatarAccessTokenService(appConfig);
        var avatarApplicationService = CoreModule.avatarApplicationService(
            dataSource, appConfig, readCachePort, natsOutbound, chatPersistencePort);
        var contactService = new ContactService(contactRepositoryPort, userLookupPort, blockRepositoryPort,
            avatarApplicationService);
        var objectStoragePort = CoreModule.objectStoragePort(appConfig, minioClient, fileProxy);
        var fileApplicationService = CoreModule.fileApplicationService(
            dataSource, messageQueryPort, objectStoragePort, this.uuidGenerator, appConfig);
        var authService = new AuthService(
            appConfig,
            CoreModule.userLookupPort(dataSource),
            CoreModule.userRepositoryPort(dataSource),
            CoreModule.savedChatPort(dataSource, this.uuidGenerator),
            fileApplicationService,
            avatarApplicationService);
        var uiBrandingService = CoreModule.uiBrandingService(dataSource);
        var organizationApplicationService = CoreModule.organizationApplicationService(dataSource, this.uuidGenerator);
        var publicLinkPort = CoreModule.publicLinkPort(dataSource, this.uuidGenerator);
        var purgeStatusService = new PurgeStatusService(adminStatsJdbc, auditPort);
        java.util.function.BooleanSupplier indexerAvailable =
            () -> indexerHotPlugMonitor == null || indexerHotPlugMonitor.isIndexerPresent();
        var indexerEventPublisher = CoreModule.indexerEventPublisher(natsOutbound, indexerAvailable);
        var messageSendCoordinator = new MessageSendCoordinator(
            messagePersistence,
            CoreModule.chatRepositoryPort(dataSource),
            mlsService, mlsMigrationService, natsOutbound,
            this.uuidGenerator, readCachePort,
            new MessageMentionCoordinator(
                CoreModule.chatRepositoryPort(dataSource),
                new JdbcMessageMentionRepositoryAdapter(dataSource),
                natsOutbound));
        this.messageSendCoordinator = messageSendCoordinator;
        var messageEditCoordinator = CoreModule.messageEditCoordinator(dataSource, indexerEventPublisher);
        var messageDeleteCoordinator = CoreModule.messageDeleteCoordinator(
            dataSource, natsOutbound, indexerEventPublisher);
        var messageReactionCoordinator = CoreModule.messageReactionCoordinator(dataSource, natsOutbound);
        var messagePinCoordinator = CoreModule.messagePinCoordinator(dataSource, natsOutbound);
        var pluginRepository = new com.avandocmsg.messenger.api.plugins.PluginRepository(dataSource);
        var integrationRouterClient = new com.avandocmsg.messenger.api.plugins.IntegrationRouterClient(
            appConfig.integrationsBaseUrl());
        UserMessageSource userMessagesEarly = new CompositeMessageSource(appConfig.locale(),
            CoreApiComposition.class.getClassLoader(),
            List.of(
                "com.avandocmsg.messenger.i18n.messages_core_api",
                "com.avandocmsg.messenger.i18n.messages_common"));
        var pluginPolicyService = new com.avandocmsg.messenger.api.plugins.PluginPolicyService(pluginRepository);
        var pluginPlatformService = new com.avandocmsg.messenger.api.plugins.PluginPlatformService(
            pluginRepository, integrationRouterClient, pluginPolicyService, userMessagesEarly);
        var dlpBridgeGate = new com.avandocmsg.messenger.api.compliance.DlpBridgeGate(
            pluginRepository, pluginPlatformService, userLookupPort);
        var messageApplicationService = CoreModule.messageApplicationService(
            dataSource, blockRepositoryPort, messageSendCoordinator, messageEditCoordinator,
            messageDeleteCoordinator, messageReactionCoordinator, messagePinCoordinator,
            messageQueryPort, mlsService, dlpBridgeGate);
        var fileService = new FileService(fileApplicationService, messageQueryPort);
        var exportComplianceSeed = new AdminExportComplianceSeed(
            chatService, messageApplicationService, fileService, chatPersistencePort, chatRetentionPolicyPort);
        var chatBanService = new ChatBanService(
            CoreModule.chatBanPort(dataSource, this.clock, this.uuidGenerator), chatPersistencePort);
        var conferencePort = CoreModule.conferencePort(dataSource, appConfig, this.uuidGenerator);

        UserMessageSource userMessages = new CompositeMessageSource(appConfig.locale(),
            CoreApiComposition.class.getClassLoader(),
            List.of(
                "com.avandocmsg.messenger.i18n.messages_core_api",
                "com.avandocmsg.messenger.i18n.messages_common"));

        var conferenceService = new com.avandocmsg.messenger.api.conference.ConferenceService(
            conferencePort, chatPersistencePort, chatService, natsOutbound, userMessages);

        var liveKitTokenService = new com.avandocmsg.messenger.api.live.LiveKitTokenService(appConfig);
        var liveSessionPort = CoreModule.liveSessionPort(dataSource, appConfig, this.uuidGenerator);
        var liveSessionService = new com.avandocmsg.messenger.api.live.LiveSessionService(
            liveSessionPort, chatPersistencePort, liveKitTokenService, natsOutbound, userMessages);
        var chatCallLiveKitService = new com.avandocmsg.messenger.api.live.ChatCallLiveKitService(
            chatPersistencePort, liveKitTokenService);

        var botRepository = new BotRepository(dataSource);
        var botService = new BotService(botRepository, chatPersistencePort, messageApplicationService,
            chatBanService, auditPort, this.uuidGenerator,
            CoreModule.userRepositoryPort(dataSource), avatarApplicationService);

        var pluginOutboundService = new com.avandocmsg.messenger.api.plugins.PluginOutboundService(
            pluginRepository, messageApplicationService, userMessagesEarly);

        var authPolicyRepository = new com.avandocmsg.messenger.api.auth.policy.AuthPolicyRepository(dataSource);
        var keycloakAuthSyncClient = new com.avandocmsg.messenger.api.auth.policy.KeycloakAuthSyncClient(appConfig);
        var authPolicyService = new com.avandocmsg.messenger.api.auth.policy.AuthPolicyService(
            appConfig, authPolicyRepository, organizationLookupPort, keycloakAuthSyncClient);

        var directorySyncRunRepository = CoreModule.directorySyncRunRepositoryPort(dataSource);
        var orgUserDirectory = CoreModule.orgUserDirectoryPort(dataSource);
        var ldapDirectoryClient = new com.avandocmsg.messenger.api.directory.JndiLdapDirectoryClient();
        this.directorySyncService = new com.avandocmsg.messenger.api.directory.DirectorySyncService(
            authPolicyRepository, organizationLookupPort, directorySyncRunRepository,
            orgUserDirectory, ldapDirectoryClient, this.uuidGenerator);

        var platformModuleOverrideRepository =
            new com.avandocmsg.messenger.api.platform.PlatformModuleOverrideRepository(dataSource);
        var platformModuleRegistry = com.avandocmsg.messenger.api.platform.PlatformModuleRegistry.create(
            appConfig, platformModuleOverrideRepository);

        var chatPollPort = CoreModule.chatPollPort(dataSource);
        this.scheduledMessagePort = CoreModule.scheduledMessagePort(dataSource);
        this.messageReminderPort = CoreModule.messageReminderPort(dataSource);
        var chatPollService = new com.avandocmsg.messenger.api.polls.ChatPollService(
            chatPollPort, chatPersistencePort, this.clock);
        var phase5AdrService = new com.avandocmsg.messenger.api.phase5.Phase5AdrService(
            new com.avandocmsg.messenger.api.phase5.Phase5AdrRepository(dataSource),
            chatPersistencePort,
            conferencePort,
            userLookupPort,
            pluginRepository,
            pluginPlatformService,
            appConfig);
        var liveKitEgressClient = new com.avandocmsg.messenger.api.live.LiveKitEgressClient(appConfig, liveKitTokenService);
        var meshCallRecordingService = new com.avandocmsg.messenger.api.meshcall.MeshCallRecordingService(
            new com.avandocmsg.messenger.api.meshcall.MeshCallRecordingRepository(dataSource),
            chatPersistencePort,
            auditPort,
            liveKitTokenService,
            liveKitEgressClient,
            appConfig,
            CoreModule.fileMetadataPort(dataSource),
            this.uuidGenerator);

        var jerseyConfig = new JerseyConfig(dataSource, appConfig, userMessages, this.clock, this.uuidGenerator, tokenValidator, authService, authRateLimiter,
                userLookupPort, organizationLookupPort, contactRepositoryPort, contactService,
                chatService, readReceiptService, chatApplicationService,
                messageApplicationService, userApplicationService, fileApplicationService,
                avatarApplicationService, avatarAccessTokenService,
                organizationApplicationService,
                blockRepositoryPort,
                messagePersistence, messagePersistence, natsConnection, natsOutbound,
                minioClient, fileService,
                chatBanService,
                e2eeService, keyPackageRepository, sessionRepository, mlsService, mlsGroupManager,
                mlsMigrationService, openMlsBindingPort, mlsWirePublisher, fileProxy, conferenceService, liveSessionService,
                chatCallLiveKitService,
                auditPort, exportJobPort, exportJobEnqueuer, exportFileAccess, this.exportSuggestedHandler,
                exportComplianceSeed,
                retentionPolicyPort, chatRetentionPolicyPort, chatPersistencePort,
                publicLinkPort, messageSearchService, adminManifest, adminServerStatsService, fleetSnapshotService, redisProbe,
                readCachePort, legalHoldPort, purgeStatusService, botRepository, botService,
                pluginRepository, pluginPlatformService, pluginPolicyService, pluginOutboundService,
                authPolicyService, directorySyncService, migrationImportJobPort, devicePort, orgUserDirectory,
                platformModuleRegistry, platformModuleOverrideRepository,
                federationTrustPort, federationStatusService, dlpBridgeGate,
                chatPollPort, chatPollService, scheduledMessagePort, messageReminderPort, phase5AdrService,
                meshCallRecordingService,
                uiBrandingService);
        var jerseyServlet = new ServletContainer(jerseyConfig);

        registration.register(SERVLET_NAME, jerseyServlet, new ClasspathAdminStaticServlet());
    }

    public void startBackgroundServices() throws IOException {
        if (appConfig.readCacheNatsInvalidateEnabled()) {
            readCacheInvalidationSubscriber = new ReadCacheInvalidationSubscriber(natsConnection, readCachePort);
            readCacheInvalidationSubscriber.start();
        } else {
            readCacheInvalidationSubscriber = null;
            log.info(
                "Read-cache NATS invalidation subscriber disabled (READ_CACHE_NATS_INVALIDATE_ENABLED=false; pipeline uses Redis DEL)"
            );
        }

        if (appConfig.exportCompleteSubscriberEnabled()) {
            exportCompleteSubscriber = new ExportReplayCompleteSubscriber(natsConnection, exportJobPort);
            exportCompleteSubscriber.start();
        } else {
            exportCompleteSubscriber = null;
            log.info("Export complete NATS subscriber disabled (EXPORT_COMPLETE_SUBSCRIBER_ENABLED=false)");
        }

        if (appConfig.exportSuggestedSubscriberEnabled()) {
            if (appConfig.exportAutoQueueOnSuggestedEnabled()) {
                log.info(
                    "Export auto-queue on {} enabled (cooldown {} min)",
                    com.avandocmsg.messenger.common.nats.NatsSubjects.MSG_EXPORT_SUGGESTED,
                    appConfig.exportAutoQueueCooldownMinutes()
                );
            }
            exportSuggestedSubscriber = new ExportSuggestedSubscriber(natsConnection, exportSuggestedHandler);
            exportSuggestedSubscriber.start();
        } else {
            exportSuggestedSubscriber = null;
            log.info("Export suggested NATS subscriber disabled (EXPORT_SUGGESTED_SUBSCRIBER_ENABLED=false)");
        }

        if (appConfig.mlsWireEnabled() && appConfig.mlsWireSubscriberEnabled()) {
            var mlsGroupStateRepository = new MlsGroupStateRepository(dataSource, clock);
            var mlsWireHandler = new MlsWireHandler(mlsGroupStateRepository, mlsService, clock);
            mlsWireSubscriber = new MlsWireSubscriber(natsConnection, mlsWireHandler);
            mlsWireSubscriber.start();
        } else {
            mlsWireSubscriber = null;
            log.info("MLS wire NATS subscriber disabled (MLS_WIRE_SUBSCRIBER_ENABLED=false or wire off)");
        }

        if (appConfig.hotplugIndexerPresenceRequired()) {
            log.info(
                "Indexer hot-plug presence check enabled (serviceId={}, ttlMs={})",
                appConfig.hotplugIndexerServiceId(),
                appConfig.hotplugHeartbeatTtlMs()
            );
        } else {
            log.info("Indexer hot-plug presence gating disabled (HOTPLUG_INDEXER_PRESENCE_REQUIRED=false)");
        }
        indexerHotPlugMonitor = new IndexerHotPlugMonitor(
            natsConnection,
            appConfig.hotplugHeartbeatTtlMs(),
            appConfig.hotplugIndexerServiceId()
        );
        indexerHotPlugMonitor.start();

        directorySyncScheduler = new com.avandocmsg.messenger.api.directory.DirectorySyncScheduler(
            appConfig, directorySyncService);

        migrationImportScheduler = new com.avandocmsg.messenger.api.admin.MigrationImportScheduler(
            appConfig,
            CoreModule.migrationImportJobPort(dataSource),
            chatPersistencePort,
            messagePersistence);

        scheduledMessageScheduler = new com.avandocmsg.messenger.api.messages.ScheduledMessageScheduler(
            appConfig, scheduledMessagePort, messageSendCoordinator, clock);
        messageReminderScheduler = new com.avandocmsg.messenger.api.messages.MessageReminderScheduler(
            appConfig, messageReminderPort, clock);
    }

    public void stopBackgroundServices() throws Exception {
        if (exportCompleteSubscriber != null) {
            exportCompleteSubscriber.close();
            exportCompleteSubscriber = null;
        }
        if (exportSuggestedSubscriber != null) {
            exportSuggestedSubscriber.close();
            exportSuggestedSubscriber = null;
        }
        if (mlsWireSubscriber != null) {
            mlsWireSubscriber.close();
            mlsWireSubscriber = null;
        }
        if (readCacheInvalidationSubscriber != null) {
            readCacheInvalidationSubscriber.close();
            readCacheInvalidationSubscriber = null;
        }
        if (indexerHotPlugMonitor != null) {
            indexerHotPlugMonitor.close();
            indexerHotPlugMonitor = null;
        }
        if (directorySyncScheduler != null) {
            directorySyncScheduler.close();
            directorySyncScheduler = null;
        }
        if (migrationImportScheduler != null) {
            migrationImportScheduler.close();
            migrationImportScheduler = null;
        }
        if (scheduledMessageScheduler != null) {
            scheduledMessageScheduler.close();
            scheduledMessageScheduler = null;
        }
        if (messageReminderScheduler != null) {
            messageReminderScheduler.close();
            messageReminderScheduler = null;
        }
        if (redisConfig != null) {
            redisConfig.shutdown();
            redisConfig = null;
        }
        if (redisProbe != null) {
            redisProbe.shutdown();
            redisProbe = null;
        }
        if (solrClient != null) {
            try {
                solrClient.close();
            } catch (Exception e) {
                log.warn("Solr close: {}", e.getMessage());
            }
            solrClient = null;
        }
    }

    private Optional<JetStream> jetStreamOptional() {
        try {
            return appConfig.natsJetstream()
                ? Optional.of(natsConnection.jetStream())
                : Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException("Cannot obtain JetStream context", e);
        }
    }

    private FileProxy createFileProxy() {
        if ("http".equalsIgnoreCase(appConfig.fileProxyMode())) {
            log.info("File proxy mode: HTTP -> {}", appConfig.fileProxyUrl());
            return new HttpFileProxy(appConfig.fileProxyUrl(), appConfig.fileProxyAuthToken());
        }
        log.info("File proxy mode: embedded MinIO -> {}", appConfig.minioEndpoint());
        return new MinioFileProxy(minioClient, appConfig.minioBucket());
    }

    private void runMigrations() {
        var flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
        log.info("Database migrations applied");
    }
}
