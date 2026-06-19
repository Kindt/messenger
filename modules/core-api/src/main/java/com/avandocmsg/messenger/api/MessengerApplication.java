package com.avandocmsg.messenger.api;

import com.avandocmsg.messenger.api.auth.AuthService;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.api.bots.BotRepository;
import com.avandocmsg.messenger.api.bots.BotService;
import com.avandocmsg.messenger.api.cache.ReadCacheInvalidationSubscriber;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.bans.ChatBanService;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.DatabaseConfig;
import com.avandocmsg.messenger.api.config.OrganizationRoutingDataSource;
import com.avandocmsg.messenger.api.config.OrganizationShardRouter;
import com.avandocmsg.messenger.api.config.HotReloadWatcher;
import com.avandocmsg.messenger.api.config.JerseyConfig;
import com.avandocmsg.messenger.api.config.SolrClientFactory;
import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
import com.avandocmsg.messenger.api.admin.fleet.FleetSnapshotService;
import com.avandocmsg.messenger.api.admin.fleet.FleetTargetRegistry;
import com.avandocmsg.messenger.api.admin.ui.AdminServerStatsService;
import com.avandocmsg.messenger.api.admin.ui.AdminUiManifest;
import com.avandocmsg.messenger.api.admin.ui.ClasspathAdminStaticServlet;
import com.avandocmsg.messenger.api.config.MinioConfig;
import com.avandocmsg.messenger.api.config.NatsConfig;
import com.avandocmsg.messenger.api.config.RedisConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.api.hotplug.IndexerHotPlugMonitor;
import com.avandocmsg.messenger.api.contacts.ContactService;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportAutoQueueOnSuggested;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportReplayCompleteSubscriber;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.api.export.ExportSuggestedSubscriber;
import com.avandocmsg.messenger.api.metrics.ExportJobsDbCollector;
import com.avandocmsg.messenger.api.metrics.ExportMetrics;
import com.avandocmsg.messenger.api.metrics.ReadCacheMetrics;
import com.avandocmsg.messenger.api.crypto.CryptoProvider;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.crypto.KeyPackageRepository;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.files.HttpFileProxy;
import com.avandocmsg.messenger.api.files.MinioFileProxy;
import com.avandocmsg.messenger.common.nats.JetStreamMessagingSetup;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.api.search.MessageSearchService;
import com.avandocmsg.messenger.core.adapter.messaging.NatsConnectionOutbound;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.avandocmsg.messenger.api.mls.MlsGroupManager;
import com.avandocmsg.messenger.api.mls.MlsGroupStateRepository;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.MlsWireHandler;
import com.avandocmsg.messenger.api.mls.MlsWirePublisher;
import com.avandocmsg.messenger.api.mls.MlsWireSubscriber;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.core.adapter.mls.OpenMlsBindingFactory;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageReadRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageWriteRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageMentionRepositoryAdapter;
import com.avandocmsg.messenger.core.application.MessageMentionCoordinator;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.api.chats.ReadReceiptService;
import com.avandocmsg.messenger.api.admin.PurgeStatusService;
import com.avandocmsg.messenger.common.i18n.CompositeMessageSource;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import org.apache.solr.client.solrj.SolrClient;
import io.minio.MinioClient;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Clock;
import java.nio.file.Files;
import java.util.Optional;
import java.nio.file.Paths;
import java.util.List;

public class MessengerApplication {
    private static final Logger log = LoggerFactory.getLogger(MessengerApplication.class);
    private static final String SERVLET_NAME = "jersey";

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

    private Tomcat tomcat;
    private Context ctx;
    private HotReloadWatcher watcher;
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
    private final ExportSuggestedHandler exportSuggestedHandler;

    public MessengerApplication() {
        this.appConfig = new AppConfig();
        appConfig.validateProductionSecrets();
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
        ExportJobsDbCollector.registerDefault(dataSource, appConfig.exportProcessingStaleMinutes());
        // Prime labeled export counters for Prometheus scrape before first enqueue/cancel.
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

    public void start() throws Exception {
        tomcat = new Tomcat();
        tomcat.setPort(appConfig.port());
        var connector = tomcat.getConnector();
        connector.setProperty("bindOnInit", "false");

        deployServlets();

        readCacheInvalidationSubscriber = new ReadCacheInvalidationSubscriber(natsConnection, readCachePort);
        readCacheInvalidationSubscriber.start();

        tomcat.start();
        log.info("core-api started on port {} (API locale: {})", appConfig.port(), appConfig.locale().toLanguageTag());

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

        if (appConfig.hotReloadEnabled()) {
            var libDir = Paths.get(System.getProperty("app.home", "."), "lib");
            if (Files.exists(libDir)) {
                watcher = new HotReloadWatcher(libDir, this::restart);
                watcher.start();
            } else {
                log.info("HotReload disabled: {} not found", libDir);
            }
        }
    }

    private void deployServlets() {
        ctx = tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        ctx.setParentClassLoader(MessengerApplication.class.getClassLoader());

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
        var devicePort = CoreModule.devicePort(dataSource, this.clock, this.uuidGenerator);
        var messageRepoPort = messagePersistence;
        var messageQueryPort = messagePersistence;
        var messageSearchService = new MessageSearchService(appConfig, messageQueryPort, chatPersistencePort,
            solrBinding.client(), solrBinding.cloudMode());

        var authService = new AuthService(
            appConfig,
            CoreModule.userLookupPort(dataSource),
            CoreModule.userRepositoryPort(dataSource),
            CoreModule.savedChatPort(dataSource, this.uuidGenerator));
        var authRateLimiter = redisConfig != null
            ? AuthRateLimiter.redis(redisConfig.sync(), appConfig)
            : AuthRateLimiter.noop();
        this.readCachePort = CoreModule.readCachePort(
            redisConfig != null ? redisConfig.sync() : null, appConfig);
        var contactService = new ContactService(contactRepositoryPort, userLookupPort, blockRepositoryPort);
        var natsOutbound = new NatsConnectionOutbound(natsConnection, jetStreamOptional());
        var adminManifest = AdminUiManifest.load(MessengerApplication.class.getClassLoader());
        var adminServerStatsService = new AdminServerStatsService(dataSource, appConfig, natsOutbound, redisProbe);
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
            messageRepoPort, messageQueryPort, natsOutbound, this.clock, this.uuidGenerator, readCachePort, appConfig);
        var readReceiptService = new ReadReceiptService(messageReadReceiptPort, chatPersistencePort,
            messageRepoPort, chatReadStatePort, userLookupPort, auditPort, natsOutbound,
            appConfig, this.clock, readCachePort);
        var mlsGroupStateRepository = new MlsGroupStateRepository(dataSource, this.clock);
        var mlsWirePublisher = new MlsWirePublisher(natsOutbound, appConfig);
        var mlsGroupManager = new MlsGroupManager(mlsGroupStateRepository, mlsService,
            this.uuidGenerator, this.clock, mlsWirePublisher);
        var mlsMigrationService = new MlsMigrationService(dataSource, mlsGroupManager, chatPersistencePort);
        var openMlsBindingPort = OpenMlsBindingFactory.create(appConfig, mlsService);
        var chatApplicationService = CoreModule.chatApplicationService(dataSource);
        var userApplicationService = CoreModule.userApplicationService(
            dataSource, this.uuidGenerator, readCachePort, appConfig, natsOutbound);
        var objectStoragePort = CoreModule.objectStoragePort(appConfig, minioClient, fileProxy);
        var fileApplicationService = CoreModule.fileApplicationService(
            dataSource, messageQueryPort, objectStoragePort, this.uuidGenerator, appConfig);
        var organizationApplicationService = CoreModule.organizationApplicationService(dataSource, this.uuidGenerator);
        var publicLinkPort = CoreModule.publicLinkPort(dataSource, this.uuidGenerator);
        var purgeStatusService = new PurgeStatusService(dataSource, auditPort);
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
        var messageEditCoordinator = CoreModule.messageEditCoordinator(dataSource, indexerEventPublisher);
        var messageDeleteCoordinator = CoreModule.messageDeleteCoordinator(
            dataSource, natsOutbound, indexerEventPublisher);
        var messageReactionCoordinator = CoreModule.messageReactionCoordinator(dataSource, natsOutbound);
        var messagePinCoordinator = CoreModule.messagePinCoordinator(dataSource, natsOutbound);
        var messageApplicationService = CoreModule.messageApplicationService(
            dataSource, blockRepositoryPort, messageSendCoordinator, messageEditCoordinator,
            messageDeleteCoordinator, messageReactionCoordinator, messagePinCoordinator,
            messageQueryPort, mlsService);
        var fileService = new FileService(fileApplicationService, messageQueryPort);
        var exportComplianceSeed = new AdminExportComplianceSeed(
            chatService, messageApplicationService, fileService, chatPersistencePort, chatRetentionPolicyPort);
        var chatBanService = new ChatBanService(
            CoreModule.chatBanPort(dataSource, this.clock, this.uuidGenerator), chatPersistencePort);
        var conferencePort = CoreModule.conferencePort(dataSource, appConfig, this.uuidGenerator);

        UserMessageSource userMessages = new CompositeMessageSource(appConfig.locale(),
            MessengerApplication.class.getClassLoader(),
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
            chatBanService, auditPort, this.uuidGenerator);

        var pluginRepository = new com.avandocmsg.messenger.api.plugins.PluginRepository(dataSource);
        var integrationRouterClient = new com.avandocmsg.messenger.api.plugins.IntegrationRouterClient(
            appConfig.integrationsBaseUrl());
        var pluginPolicyService = new com.avandocmsg.messenger.api.plugins.PluginPolicyService(pluginRepository);
        var pluginPlatformService = new com.avandocmsg.messenger.api.plugins.PluginPlatformService(
            pluginRepository, integrationRouterClient, pluginPolicyService, userMessages);
        var pluginOutboundService = new com.avandocmsg.messenger.api.plugins.PluginOutboundService(
            pluginRepository, messageApplicationService, userMessages);

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

        var jerseyServlet = new ServletContainer(
            new JerseyConfig(dataSource, appConfig, userMessages, this.clock, this.uuidGenerator, tokenValidator, authService, authRateLimiter,
                userLookupPort, contactRepositoryPort, contactService,
                chatService, readReceiptService, chatApplicationService,
                messageApplicationService, userApplicationService, fileApplicationService,
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
                authPolicyService, directorySyncService, migrationImportJobPort, devicePort, orgUserDirectory));
        Tomcat.addServlet(ctx, SERVLET_NAME, jerseyServlet);
        ctx.addServletMappingDecoded("/api/*", SERVLET_NAME);

        Tomcat.addServlet(ctx, "adminUiStatic", new ClasspathAdminStaticServlet());
        ctx.addServletMappingDecoded("/admin", "adminUiStatic");
        ctx.addServletMappingDecoded("/admin/*", "adminUiStatic");
    }

    private void restart() {
        try {
            log.info("Hot-reload triggered, restarting application context...");
            ctx.stop();
            ctx.destroy();
            deployServlets();
            ctx.start();
            log.info("Application context reloaded successfully");
        } catch (Exception e) {
            log.error("Failed to reload application context", e);
        }
    }

    public void stop() throws Exception {
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
        if (watcher != null) watcher.stop();
        if (tomcat != null) tomcat.stop();
        if (redisConfig != null) {
            redisConfig.shutdown();
        }
        if (redisProbe != null) {
            redisProbe.shutdown();
        }
        if (solrClient != null) {
            try {
                solrClient.close();
            } catch (Exception e) {
                log.warn("Solr close: {}", e.getMessage());
            }
        }
    }

    private void runMigrations() {
        var flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();
        log.info("Database migrations applied");
    }

    public static void main(String[] args) throws Exception {
        var app = new MessengerApplication();
        app.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { app.stop(); } catch (Exception e) { log.warn("Shutdown error", e); }
        }));
        Thread.currentThread().join();
    }
}
