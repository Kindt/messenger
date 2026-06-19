package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.api.auth.AuthPolicyAdminResource;
import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
import com.avandocmsg.messenger.api.auth.AuthResource;
import com.avandocmsg.messenger.api.auth.AuthService;
import com.avandocmsg.messenger.api.auth.policy.AuthPolicyService;
import com.avandocmsg.messenger.api.directory.DirectorySyncService;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.api.chats.ChatResource;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.ReadReceiptService;
import com.avandocmsg.messenger.api.chats.bans.ChatBanResource;
import com.avandocmsg.messenger.api.chats.bans.ChatBanService;
import com.avandocmsg.messenger.api.config.openapi.OpenApiConfig;
import com.avandocmsg.messenger.api.contacts.ContactResource;
import com.avandocmsg.messenger.api.contacts.ContactService;
import com.avandocmsg.messenger.api.contacts.SearchResource;
import com.avandocmsg.messenger.api.crypto.CryptoResource;
import com.avandocmsg.messenger.api.devices.DeviceResource;
import com.avandocmsg.messenger.api.admin.AdminMigrationImportResource;
import com.avandocmsg.messenger.api.admin.AdminResource;
import com.avandocmsg.messenger.api.admin.fleet.FleetSnapshotService;
import com.avandocmsg.messenger.api.admin.ui.AdminServerStatsService;
import com.avandocmsg.messenger.api.admin.ui.AdminStatsPort;
import com.avandocmsg.messenger.api.admin.ui.AdminUiManifest;
import com.avandocmsg.messenger.api.admin.ui.AdminConsoleRedirectResource;
import com.avandocmsg.messenger.api.admin.ui.AdminUiResource;
import com.avandocmsg.messenger.api.blocks.BlocksResource;
import com.avandocmsg.messenger.api.conference.ChatConferenceResource;
import com.avandocmsg.messenger.api.conference.ConferenceResource;
import com.avandocmsg.messenger.api.conference.ConferenceService;
import com.avandocmsg.messenger.api.live.ChatCallLiveKitService;
import com.avandocmsg.messenger.api.live.ChatLiveSessionResource;
import com.avandocmsg.messenger.api.live.LiveSessionResource;
import com.avandocmsg.messenger.api.live.LiveSessionService;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportResource;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.crypto.KeyPackageRepository;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.files.FileResource;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.media.MediaCapabilitiesResource;
import com.avandocmsg.messenger.api.metrics.PrometheusMetricsResource;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ChatRetentionPolicyPort;
import com.avandocmsg.messenger.core.port.DevicePort;
import com.avandocmsg.messenger.core.port.ExportJobPort;
import com.avandocmsg.messenger.core.port.LegalHoldPort;
import com.avandocmsg.messenger.core.port.MigrationImportJobPort;
import com.avandocmsg.messenger.core.port.RetentionPolicyPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.api.search.MessageSearchService;
import com.avandocmsg.messenger.api.bots.BotRepository;
import com.avandocmsg.messenger.api.bots.BotResource;
import com.avandocmsg.messenger.api.bots.BotRateLimiter;
import com.avandocmsg.messenger.api.bots.BotService;
import com.avandocmsg.messenger.api.filter.BotRateLimitFilter;
import com.avandocmsg.messenger.api.filter.BotTokenAuthFilter;
import com.avandocmsg.messenger.api.filter.ScimBearerAuthFilter;
import com.avandocmsg.messenger.api.filter.JwtAuthFilter;
import com.avandocmsg.messenger.api.filter.OrgRoutingClearFilter;
import com.avandocmsg.messenger.api.filter.OrgIpAllowlistFilter;
import com.avandocmsg.messenger.api.filter.OrgRoutingFilter;
import com.avandocmsg.messenger.api.health.HealthResource;
import com.avandocmsg.messenger.api.messages.MessageResource;
import com.avandocmsg.messenger.api.mls.MlsGroupManager;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.MlsWirePublisher;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.OrgUserDirectoryPort;
import com.avandocmsg.messenger.core.port.ScimGroupRepositoryPort;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
import com.avandocmsg.messenger.api.admin.PurgeStatusService;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.api.users.MeIntegrationsResource;
import com.avandocmsg.messenger.api.users.MeSettingsResource;
import com.avandocmsg.messenger.api.users.UserResource;
import com.avandocmsg.messenger.core.adapter.messaging.NatsConnectionOutbound;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.OpenMlsBindingPort;
import com.avandocmsg.messenger.core.port.PublicLinkPort;
import com.avandocmsg.messenger.core.port.ReadCachePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.minio.MinioClient;
import io.nats.client.Connection;

import java.time.Clock;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.sql.DataSource;

public class JerseyConfig extends ResourceConfig {

    public JerseyConfig(DataSource dataSource, AppConfig appConfig, UserMessageSource userMessages, Clock clock, UuidGenerator uuidGenerator,
                        TokenValidator tokenValidator, AuthService authService,
                        AuthRateLimiter authRateLimiter,
                        UserLookupPort userLookupPort,
                        ContactRepositoryPort contactRepositoryPort, ContactService contactService,
                        ChatService chatService,
                        ReadReceiptService readReceiptService,
                        ChatApplicationService chatApplicationService,
                        MessageApplicationService messageApplicationService,
                        UserApplicationService userApplicationService,
                        FileApplicationService fileApplicationService,
                        OrganizationApplicationService organizationApplicationService,
                        BlockRepositoryPort blockRepositoryPort,
                        MessageRepositoryPort messageRepositoryPort,
                        MessageQueryPort messageQueryPort,
                        Connection natsConnection, NatsConnectionOutbound natsOutbound,
                        MinioClient minioClient, FileService fileService,
                        ChatBanService chatBanService,
                        E2EEService e2eeService, KeyPackageRepository keyPackageRepository,
                        SessionRepository sessionRepository, MlsService mlsService, MlsGroupManager mlsGroupManager,
                        MlsMigrationService mlsMigrationService, OpenMlsBindingPort openMlsBindingPort,
                        MlsWirePublisher mlsWirePublisher,
                        FileProxy fileProxy, ConferenceService conferenceService,
                        LiveSessionService liveSessionService,
                        ChatCallLiveKitService chatCallLiveKitService,
                        AuditPort auditPort,
                        ExportJobPort exportJobPort,
                        ExportJobEnqueuer exportJobEnqueuer,
                        ExportFileAccess exportFileAccess,
                        ExportSuggestedHandler exportSuggestedHandler,
                        AdminExportComplianceSeed exportComplianceSeed,
                        RetentionPolicyPort retentionPolicyPort,
                        ChatRetentionPolicyPort chatRetentionPolicyPort,
                        ChatPersistencePort chatPersistencePort,
                        PublicLinkPort publicLinkPort,
                        MessageSearchService messageSearchService,
                        AdminUiManifest adminUiManifest,
                        AdminServerStatsService adminServerStatsService,
                        FleetSnapshotService fleetSnapshotService,
                        RedisProbe redisProbe,
                        ReadCachePort readCachePort,
                        LegalHoldPort legalHoldPort,
                        PurgeStatusService purgeStatusService,
                        BotRepository botRepository,
                        BotService botService,
                        com.avandocmsg.messenger.api.plugins.PluginRepository pluginRepository,
                        com.avandocmsg.messenger.api.plugins.PluginPlatformService pluginPlatformService,
                        com.avandocmsg.messenger.api.plugins.PluginPolicyService pluginPolicyService,
                        com.avandocmsg.messenger.api.plugins.PluginOutboundService pluginOutboundService,
                        AuthPolicyService authPolicyService,
                        DirectorySyncService directorySyncService,
                        MigrationImportJobPort migrationImportJobPort,
                        DevicePort devicePort,
                        OrgUserDirectoryPort orgUserDirectoryPort) {
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(dataSource).to(DataSource.class);
                bind(appConfig).to(AppConfig.class);
                bind(redisProbe).to(RedisProbe.class);
                bind(readCachePort).to(ReadCachePort.class);
                bind(userMessages).to(UserMessageSource.class);
                bind(clock).to(Clock.class);
                bind(uuidGenerator).to(UuidGenerator.class);
                bind(tokenValidator).to(TokenValidator.class);
                bind(authService).to(AuthService.class);
                bind(authRateLimiter).to(AuthRateLimiter.class);
                bind(userLookupPort).to(UserLookupPort.class);
                bind(contactRepositoryPort).to(ContactRepositoryPort.class);
                bind(contactService).to(ContactService.class);
                bind(chatService).to(ChatService.class);
                bind(readReceiptService).to(ReadReceiptService.class);
                bind(chatApplicationService).to(ChatApplicationService.class);
                bind(messageApplicationService).to(MessageApplicationService.class);
                bind(userApplicationService).to(UserApplicationService.class);
                bind(fileApplicationService).to(FileApplicationService.class);
                bind(organizationApplicationService).to(OrganizationApplicationService.class);
                bind(blockRepositoryPort).to(BlockRepositoryPort.class);
                bind(messageRepositoryPort).to(MessageRepositoryPort.class);
                bind(messageQueryPort).to(MessageQueryPort.class);
                bind(natsConnection).to(Connection.class);
                bind(natsOutbound).to(NatsOutboundPort.class);
                bind(natsOutbound).to(NatsConnectionStatus.class);
                bind(minioClient).to(MinioClient.class);
                bind(fileProxy).to(FileProxy.class);
                bind(fileService).to(FileService.class);
                bind(chatBanService).to(ChatBanService.class);
                bind(e2eeService).to(E2EEService.class);
                bind(keyPackageRepository).to(KeyPackageRepository.class);
                bind(sessionRepository).to(SessionRepository.class);
                bind(mlsService).to(MlsService.class);
                bind(mlsGroupManager).to(MlsGroupManager.class);
                bind(mlsMigrationService).to(MlsMigrationService.class);
                bind(openMlsBindingPort).to(OpenMlsBindingPort.class);
                bind(mlsWirePublisher).to(MlsWirePublisher.class);
                bind(conferenceService).to(ConferenceService.class);
                bind(liveSessionService).to(LiveSessionService.class);
                bind(chatCallLiveKitService).to(ChatCallLiveKitService.class);
                bind(auditPort).to(AuditPort.class);
                bind(exportJobPort).to(ExportJobPort.class);
                bind(exportJobEnqueuer).to(ExportJobEnqueuer.class);
                bind(exportFileAccess).to(ExportFileAccess.class);
                bind(exportSuggestedHandler).to(ExportSuggestedHandler.class);
                bind(exportComplianceSeed).to(AdminExportComplianceSeed.class);
                bind(retentionPolicyPort).to(RetentionPolicyPort.class);
                bind(chatRetentionPolicyPort).to(ChatRetentionPolicyPort.class);
                bind(chatPersistencePort).to(ChatPersistencePort.class);
                bind(publicLinkPort).to(PublicLinkPort.class);
                bind(messageSearchService).to(MessageSearchService.class);
                bind(adminUiManifest).to(AdminUiManifest.class);
                bind(adminServerStatsService).to(AdminStatsPort.class);
                bind(adminServerStatsService).to(AdminServerStatsService.class);
                bind(fleetSnapshotService).to(FleetSnapshotService.class);
                bind(legalHoldPort).to(LegalHoldPort.class);
                bind(purgeStatusService).to(PurgeStatusService.class);
                bind(botRepository).to(BotRepository.class);
                bind(botService).to(BotService.class);
                bind(pluginRepository).to(com.avandocmsg.messenger.api.plugins.PluginRepository.class);
                bind(pluginPlatformService).to(com.avandocmsg.messenger.api.plugins.PluginPlatformService.class);
                bind(pluginPolicyService).to(com.avandocmsg.messenger.api.plugins.PluginPolicyService.class);
                bind(pluginOutboundService).to(com.avandocmsg.messenger.api.plugins.PluginOutboundService.class);
                bind(authPolicyService).to(AuthPolicyService.class);
                bind(directorySyncService).to(DirectorySyncService.class);
                bind(migrationImportJobPort).to(MigrationImportJobPort.class);
                bind(devicePort).to(DevicePort.class);
                bind(orgUserDirectoryPort).to(OrgUserDirectoryPort.class);
                bind(CoreModule.scimGroupRepositoryPort(dataSource)).to(ScimGroupRepositoryPort.class);
                bind(BotRateLimiter.fromEnv()).to(BotRateLimiter.class);
                bind(new com.avandocmsg.messenger.api.security.OrgIpAllowlistService(
                    new com.avandocmsg.messenger.api.security.OrgIpAllowlistRepository(dataSource)))
                    .to(com.avandocmsg.messenger.api.security.OrgIpAllowlistService.class);
            }
        });

        register(RolesAllowedDynamicFeature.class);

        register(HealthResource.class);
        register(AuthResource.class);
        register(AuthPolicyAdminResource.class);
        register(com.avandocmsg.messenger.api.security.OrgIpAllowlistAdminResource.class);
        register(com.avandocmsg.messenger.api.directory.DirectorySyncAdminResource.class);
        register(com.avandocmsg.messenger.api.scim.ScimUsersResource.class);
        register(com.avandocmsg.messenger.api.scim.ScimGroupsResource.class);
        register(AdminResource.class);
        register(AdminMigrationImportResource.class);
        register(AdminConsoleRedirectResource.class);
        register(AdminUiResource.class);
        register(UserResource.class);
        register(BlocksResource.class);
        register(ContactResource.class);
        register(SearchResource.class);
        register(ChatResource.class);
        register(MessageResource.class);
        register(FileResource.class);
        register(ChatBanResource.class);
        register(CryptoResource.class);
        register(DeviceResource.class);
        register(MeSettingsResource.class);
        register(MeIntegrationsResource.class);
        register(ExportResource.class);
        register(ConferenceResource.class);
        register(ChatConferenceResource.class);
        register(LiveSessionResource.class);
        register(ChatLiveSessionResource.class);
        register(com.avandocmsg.messenger.api.live.ChatCallLiveKitResource.class);
        register(MediaCapabilitiesResource.class);
        register(BotResource.class);
        register(com.avandocmsg.messenger.api.plugins.PluginAdminResource.class);
        register(com.avandocmsg.messenger.api.plugins.PluginOutboundResource.class);
        register(PrometheusMetricsResource.class);

        register(OpenApiConfig.create(appConfig.version()).getClass());

        register(BotTokenAuthFilter.class);
        register(ScimBearerAuthFilter.class);
        register(BotRateLimitFilter.class);
        register(JwtAuthFilter.class);
        register(OrgIpAllowlistFilter.class);
        register(OrgRoutingFilter.class);
        register(OrgRoutingClearFilter.class);
        register(JacksonFeature.class);
        register(MultiPartFeature.class);
        register(RequestContextMdcFilter.class);
        register(CorsPreflightFilter.class);
        register(CorsResponseFilter.class);
        register(new SecurityHeadersFilter(appConfig));
        register(RequestContextMdcClearFilter.class);
        register(JsonMappingExceptionMapper.class);
        register(ForbiddenExceptionMapper.class);
        register(InvalidUuidParameterExceptionMapper.class);
        register(GeneralExceptionMapper.class);

        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
        property(ServerProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, 0);
    }
}
