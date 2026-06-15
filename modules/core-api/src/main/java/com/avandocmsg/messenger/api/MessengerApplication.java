package com.avandocmsg.messenger.api;

import com.avandocmsg.messenger.api.auth.AuthService;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.bans.ChatBanService;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.DatabaseConfig;
import com.avandocmsg.messenger.api.config.HotReloadWatcher;
import com.avandocmsg.messenger.api.config.JerseyConfig;
import com.avandocmsg.messenger.api.config.SolrClientFactory;
import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
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
import com.avandocmsg.messenger.api.messages.MessageService;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.api.repository.FilePublicLinkRepository;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
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
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatBanRepository;
import com.avandocmsg.messenger.api.admin.PurgeStatusService;
import com.avandocmsg.messenger.api.repository.LegalHoldRepository;
import com.avandocmsg.messenger.api.repository.ChatReadRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ContactRepository;
import com.avandocmsg.messenger.api.repository.FileRepository;
import com.avandocmsg.messenger.api.repository.MessageReadReceiptRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.api.chats.ReadReceiptService;
import com.avandocmsg.messenger.api.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final BlockRepository blockRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final FileRepository fileRepository;
    private final ChatBanRepository chatBanRepository;
    private final E2EEService e2eeService;
    private final KeyPackageRepository keyPackageRepository;
    private final SessionRepository sessionRepository;
    private final MlsService mlsService;
    private final FileProxy fileProxy;
    private final ExportJobRepository exportJobRepository;
    private final AuditRepository auditRepository;
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
    private IndexerHotPlugMonitor indexerHotPlugMonitor;
    private final ExportSuggestedHandler exportSuggestedHandler;

    public MessengerApplication() {
        this.appConfig = new AppConfig();
        log.info("Starting AvandocMsg.Messenger core-api v{}", appConfig.version());
        CryptoProvider.ensureLoaded();
        var databaseConfig = new DatabaseConfig(appConfig);
        this.dataSource = databaseConfig.dataSource();
        var readDs = databaseConfig.readDataSource().orElse(null);
        databaseConfig.warnIfPoolOversubscribed();
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
        this.userRepository = new UserRepository(dataSource);
        this.contactRepository = new ContactRepository(dataSource);
        this.blockRepository = new BlockRepository(dataSource);
        this.chatRepository = new ChatRepository(dataSource, readDs, clock, uuidGenerator);
        this.messageRepository = new MessageRepository(dataSource, readDs, clock);
        this.fileRepository = new FileRepository(dataSource);
        this.chatBanRepository = new ChatBanRepository(dataSource, clock, uuidGenerator);
        this.e2eeService = new E2EEService();
        this.keyPackageRepository = new KeyPackageRepository(dataSource, clock, uuidGenerator);
        this.sessionRepository = new SessionRepository(dataSource, clock, uuidGenerator);
        this.mlsService = new MlsService(sessionRepository, e2eeService);
        this.fileProxy = createFileProxy();
        this.exportJobRepository = new ExportJobRepository(dataSource);
        this.auditRepository = new AuditRepository(dataSource);
        var natsOutbound = new NatsConnectionOutbound(natsConnection, jetStreamOptional());
        this.exportJobEnqueuer = new ExportJobEnqueuer(
            exportJobRepository,
            auditRepository,
            natsOutbound,
            uuidGenerator
        );
        var exportAutoQueue = appConfig.exportAutoQueueOnSuggestedEnabled()
            ? Optional.of(new ExportAutoQueueOnSuggested(
                appConfig, exportJobEnqueuer, exportJobRepository, chatRepository, auditRepository))
            : Optional.<ExportAutoQueueOnSuggested>empty();
        this.exportSuggestedHandler = new ExportSuggestedHandler(auditRepository, exportAutoQueue);
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
        tomcat.start();
        log.info("core-api started on port {} (API locale: {})", appConfig.port(), appConfig.locale().toLanguageTag());

        if (appConfig.exportCompleteSubscriberEnabled()) {
            exportCompleteSubscriber = new ExportReplayCompleteSubscriber(natsConnection, exportJobRepository);
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
            indexerHotPlugMonitor = new IndexerHotPlugMonitor(
                natsConnection,
                appConfig.hotplugHeartbeatTtlMs(),
                appConfig.hotplugIndexerServiceId()
            );
            indexerHotPlugMonitor.start();
            log.info(
                "Indexer hot-plug presence check enabled (serviceId={}, ttlMs={})",
                appConfig.hotplugIndexerServiceId(),
                appConfig.hotplugHeartbeatTtlMs()
            );
        } else {
            indexerHotPlugMonitor = null;
            log.info("Indexer hot-plug presence check disabled (HOTPLUG_INDEXER_PRESENCE_REQUIRED=false)");
        }

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
        var organizationRepository = new OrganizationRepository(dataSource, this.clock, this.uuidGenerator);
        var retentionPolicyRepository = new RetentionPolicyRepository(dataSource);
        var chatRetentionPolicyRepository = new ChatRetentionPolicyRepository(dataSource);
        var filePublicLinkRepository = new FilePublicLinkRepository(dataSource, this.uuidGenerator);
        var messageSearchService = new MessageSearchService(appConfig, messageRepository, chatRepository,
            solrBinding.client(), solrBinding.cloudMode());
        var chatReadRepository = new ChatReadRepository(dataSource);
        var messageReadReceiptRepository = new MessageReadReceiptRepository(dataSource);

        var authService = new AuthService(
            appConfig,
            userRepository,
            CoreModule.userRepositoryPort(dataSource),
            CoreModule.savedChatPort(dataSource, this.uuidGenerator));
        var authRateLimiter = redisConfig != null
            ? AuthRateLimiter.redis(redisConfig.sync(), appConfig)
            : AuthRateLimiter.noop();
        this.readCachePort = CoreModule.readCachePort(
            redisConfig != null ? redisConfig.sync() : null, appConfig);
        var contactService = new ContactService(contactRepository, userRepository, blockRepository);
        var natsOutbound = new NatsConnectionOutbound(natsConnection, jetStreamOptional());
        var adminManifest = AdminUiManifest.load(MessengerApplication.class.getClassLoader());
        var adminServerStatsService = new AdminServerStatsService(dataSource, appConfig, natsOutbound, redisProbe);
        var chatService = new ChatService(chatRepository, blockRepository, chatReadRepository,
            messageRepository, natsOutbound, this.clock, this.uuidGenerator, readCachePort, appConfig);
        var readReceiptService = new ReadReceiptService(messageReadReceiptRepository, chatRepository,
            messageRepository, chatReadRepository, userRepository, auditRepository, natsOutbound,
            appConfig, this.clock, readCachePort);
        var mlsGroupStateRepository = new MlsGroupStateRepository(dataSource, this.clock);
        var mlsWirePublisher = new MlsWirePublisher(natsOutbound, appConfig);
        var mlsGroupManager = new MlsGroupManager(mlsGroupStateRepository, mlsService,
            this.uuidGenerator, this.clock, mlsWirePublisher);
        var mlsMigrationService = new MlsMigrationService(dataSource, mlsGroupManager, chatRepository);
        var chatApplicationService = CoreModule.chatApplicationService(dataSource, chatRepository);
        var messageApplicationService = CoreModule.messageApplicationService(dataSource, chatRepository);
        var userApplicationService = CoreModule.userApplicationService(dataSource, this.uuidGenerator, readCachePort, appConfig);
        var objectStoragePort = CoreModule.objectStoragePort(appConfig, minioClient, fileProxy);
        var fileApplicationService = CoreModule.fileApplicationService(
            dataSource, messageRepository, objectStoragePort, this.uuidGenerator, appConfig);
        var organizationApplicationService = CoreModule.organizationApplicationService(dataSource, this.uuidGenerator);
        var publicLinkPort = CoreModule.publicLinkPort(filePublicLinkRepository);
        var legalHoldRepository = new LegalHoldRepository(dataSource);
        var purgeStatusService = new PurgeStatusService(dataSource, auditRepository);
        var messageService = new MessageService(messageRepository, chatRepository, blockRepository,
            mlsService, mlsMigrationService, natsOutbound, this.uuidGenerator, readCachePort,
            () -> indexerHotPlugMonitor == null || indexerHotPlugMonitor.isIndexerPresent());
        var fileService = new FileService(fileApplicationService, messageRepository);
        var exportComplianceSeed = new AdminExportComplianceSeed(
            chatService, messageService, fileService, chatRepository, chatRetentionPolicyRepository);
        var chatBanService = new ChatBanService(chatBanRepository, chatRepository);
        var conferenceRepository = new com.avandocmsg.messenger.api.repository.ConferenceRepository(dataSource, appConfig,
            this.uuidGenerator);

        UserMessageSource userMessages = new CompositeMessageSource(appConfig.locale(),
            MessengerApplication.class.getClassLoader(),
            List.of(
                "com.avandocmsg.messenger.i18n.messages_core_api",
                "com.avandocmsg.messenger.i18n.messages_common"));

        var conferenceService = new com.avandocmsg.messenger.api.conference.ConferenceService(
            conferenceRepository, chatRepository, chatService, natsOutbound, userMessages);

        var jerseyServlet = new ServletContainer(
            new JerseyConfig(dataSource, appConfig, userMessages, this.clock, this.uuidGenerator, tokenValidator, authService, authRateLimiter,
                userRepository, contactRepository, contactService,
                chatRepository, chatService, chatReadRepository, readReceiptService, chatApplicationService,
                messageApplicationService, userApplicationService, fileApplicationService,
                organizationApplicationService,
                blockRepository,
                messageRepository, messageService, natsConnection, natsOutbound,
                minioClient, fileRepository, fileService,
                chatBanRepository, chatBanService,
                e2eeService, keyPackageRepository, sessionRepository, mlsService, mlsGroupManager,
                mlsMigrationService, mlsWirePublisher, fileProxy, conferenceService,
                auditRepository, exportJobRepository, exportJobEnqueuer, exportFileAccess, this.exportSuggestedHandler,
                exportComplianceSeed,
                organizationRepository, retentionPolicyRepository, chatRetentionPolicyRepository,
                publicLinkPort, messageSearchService, adminManifest, adminServerStatsService, redisProbe,
                readCachePort, legalHoldRepository, purgeStatusService));
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
        if (indexerHotPlugMonitor != null) {
            indexerHotPlugMonitor.close();
            indexerHotPlugMonitor = null;
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
