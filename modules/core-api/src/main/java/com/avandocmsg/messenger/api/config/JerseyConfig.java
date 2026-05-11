package com.avandocmsg.messenger.api.config;

import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
import com.avandocmsg.messenger.api.auth.AuthResource;
import com.avandocmsg.messenger.api.auth.AuthService;
import com.avandocmsg.messenger.api.auth.TokenValidator;
import com.avandocmsg.messenger.api.chats.ChatResource;
import com.avandocmsg.messenger.api.chats.ChatService;
import com.avandocmsg.messenger.api.chats.bans.ChatBanResource;
import com.avandocmsg.messenger.api.chats.bans.ChatBanService;
import com.avandocmsg.messenger.api.config.openapi.OpenApiConfig;
import com.avandocmsg.messenger.api.contacts.ContactResource;
import com.avandocmsg.messenger.api.contacts.ContactService;
import com.avandocmsg.messenger.api.contacts.SearchResource;
import com.avandocmsg.messenger.api.crypto.CryptoResource;
import com.avandocmsg.messenger.api.admin.AdminResource;
import com.avandocmsg.messenger.api.admin.ui.AdminServerStatsService;
import com.avandocmsg.messenger.api.admin.ui.AdminStatsPort;
import com.avandocmsg.messenger.api.admin.ui.AdminUiManifest;
import com.avandocmsg.messenger.api.admin.ui.AdminConsoleRedirectResource;
import com.avandocmsg.messenger.api.admin.ui.AdminUiResource;
import com.avandocmsg.messenger.api.blocks.BlocksResource;
import com.avandocmsg.messenger.api.conference.ConferenceResource;
import com.avandocmsg.messenger.api.conference.ConferenceService;
import com.avandocmsg.messenger.api.export.ExportResource;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.crypto.KeyPackageRepository;
import com.avandocmsg.messenger.api.files.FileProxy;
import com.avandocmsg.messenger.api.files.FileResource;
import com.avandocmsg.messenger.api.files.FileService;
import com.avandocmsg.messenger.api.media.MediaCapabilitiesResource;
import com.avandocmsg.messenger.api.metrics.PrometheusMetricsResource;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.FilePublicLinkRepository;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
import com.avandocmsg.messenger.api.search.MessageSearchService;
import com.avandocmsg.messenger.api.filter.JwtAuthFilter;
import com.avandocmsg.messenger.api.health.HealthResource;
import com.avandocmsg.messenger.api.messages.MessageResource;
import com.avandocmsg.messenger.api.messages.MessageService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ChatBanRepository;
import com.avandocmsg.messenger.api.repository.ChatReadRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ContactRepository;
import com.avandocmsg.messenger.api.repository.FileRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.UserResource;
import com.avandocmsg.messenger.core.adapter.messaging.NatsConnectionOutbound;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
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
                        UserRepository userRepository,
                        ContactRepository contactRepository, ContactService contactService,
                        ChatRepository chatRepository, ChatService chatService,
                        ChatReadRepository chatReadRepository,
                        BlockRepository blockRepository,
                        MessageRepository messageRepository, MessageService messageService,
                        Connection natsConnection, NatsConnectionOutbound natsOutbound,
                        MinioClient minioClient, FileRepository fileRepository, FileService fileService,
                        ChatBanRepository chatBanRepository, ChatBanService chatBanService,
                        E2EEService e2eeService, KeyPackageRepository keyPackageRepository,
                        SessionRepository sessionRepository, MlsService mlsService,
                        FileProxy fileProxy, ConferenceService conferenceService,
                        AuditRepository auditRepository,
                        OrganizationRepository organizationRepository,
                        RetentionPolicyRepository retentionPolicyRepository,
                        ChatRetentionPolicyRepository chatRetentionPolicyRepository,
                        FilePublicLinkRepository filePublicLinkRepository,
                        MessageSearchService messageSearchService,
                        AdminUiManifest adminUiManifest,
                        AdminServerStatsService adminServerStatsService) {
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(dataSource).to(DataSource.class);
                bind(appConfig).to(AppConfig.class);
                bind(userMessages).to(UserMessageSource.class);
                bind(clock).to(Clock.class);
                bind(uuidGenerator).to(UuidGenerator.class);
                bind(tokenValidator).to(TokenValidator.class);
                bind(authService).to(AuthService.class);
                bind(authRateLimiter).to(AuthRateLimiter.class);
                bind(userRepository).to(UserRepository.class);
                bind(contactRepository).to(ContactRepository.class);
                bind(contactService).to(ContactService.class);
                bind(chatRepository).to(ChatRepository.class);
                bind(chatService).to(ChatService.class);
                bind(chatReadRepository).to(ChatReadRepository.class);
                bind(blockRepository).to(BlockRepository.class);
                bind(messageRepository).to(MessageRepository.class);
                bind(messageService).to(MessageService.class);
                bind(natsConnection).to(Connection.class);
                bind(natsOutbound).to(NatsOutboundPort.class);
                bind(natsOutbound).to(NatsConnectionStatus.class);
                bind(minioClient).to(MinioClient.class);
                bind(fileProxy).to(FileProxy.class);
                bind(fileRepository).to(FileRepository.class);
                bind(fileService).to(FileService.class);
                bind(chatBanRepository).to(ChatBanRepository.class);
                bind(chatBanService).to(ChatBanService.class);
                bind(e2eeService).to(E2EEService.class);
                bind(keyPackageRepository).to(KeyPackageRepository.class);
                bind(sessionRepository).to(SessionRepository.class);
                bind(mlsService).to(MlsService.class);
                bind(conferenceService).to(ConferenceService.class);
                bind(auditRepository).to(AuditRepository.class);
                bind(organizationRepository).to(OrganizationRepository.class);
                bind(retentionPolicyRepository).to(RetentionPolicyRepository.class);
                bind(chatRetentionPolicyRepository).to(ChatRetentionPolicyRepository.class);
                bind(filePublicLinkRepository).to(FilePublicLinkRepository.class);
                bind(messageSearchService).to(MessageSearchService.class);
                bind(adminUiManifest).to(AdminUiManifest.class);
                bind(adminServerStatsService).to(AdminStatsPort.class);
                bind(adminServerStatsService).to(AdminServerStatsService.class);
            }
        });

        register(RolesAllowedDynamicFeature.class);

        register(HealthResource.class);
        register(AuthResource.class);
        register(AdminResource.class);
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
        register(ExportResource.class);
        register(ConferenceResource.class);
        register(MediaCapabilitiesResource.class);
        register(PrometheusMetricsResource.class);

        register(OpenApiConfig.create(appConfig.version()).getClass());

        register(JwtAuthFilter.class);
        register(JacksonFeature.class);
        register(MultiPartFeature.class);
        register(RequestContextMdcFilter.class);
        register(CorsPreflightFilter.class);
        register(CorsResponseFilter.class);
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
