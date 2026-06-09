package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.AdminResource;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepRequest;
import com.avandocmsg.messenger.api.admin.dto.AdminExportCompliancePrepResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.OrganizationRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminExportCompliancePrepTest {

    @Test
    void exportCompliancePrep_disabled_returns404() {
        var cfg = appConfig(false);
        var seed = mock(AdminExportComplianceSeed.class);
        var resource = adminResource(cfg, seed);
        var res = resource.exportCompliancePrep(new AdminExportCompliancePrepRequest(null, true, 3, null, null), adminSc());
        assertEquals(404, res.getStatus());
    }

    @Test
    void exportCompliancePrep_enabled_returnsOk() {
        var chatId = UUID.randomUUID();
        var cfg = appConfig(true);
        var seed = mock(AdminExportComplianceSeed.class);
        when(seed.prepare(any(), any())).thenReturn(new AdminExportComplianceSeed.PrepResult(
            new AdminExportCompliancePrepResponse(chatId.toString(), List.of("m1", "m2", "m3"), true, null, null)));
        var resource = adminResource(cfg, seed);
        var res = resource.exportCompliancePrep(new AdminExportCompliancePrepRequest(null, true, 3, null, null), adminSc());
        assertEquals(200, res.getStatus());
        var body = (AdminExportCompliancePrepResponse) res.getEntity();
        assertEquals(chatId.toString(), body.chatId());
        assertEquals(3, body.messageIds().size());
    }

    @Test
    void exportCompliancePrep_badRequest_mapsMessageCountRange() {
        var cfg = appConfig(true);
        var seed = mock(AdminExportComplianceSeed.class);
        when(seed.prepare(any(), any())).thenThrow(new IllegalArgumentException("message_count_range"));
        var resource = adminResource(cfg, seed);
        var res = resource.exportCompliancePrep(new AdminExportCompliancePrepRequest(null, true, 99, null, null), adminSc());
        assertEquals(400, res.getStatus());
        var err = (ApiError) res.getEntity();
        assertEquals(I18nTestFixtures.messagesEn().get("error.admin.message_count_range"), err.message());
    }

    private static AppConfig appConfig(boolean suggestEnabled) {
        return new AppConfig() {
            @Override
            public boolean exportAdminSuggestEnabled() {
                return suggestEnabled;
            }
        };
    }

    private static AdminResource adminResource(AppConfig cfg, AdminExportComplianceSeed seed) {
        var audit = new AuditRepository(null);
        return new AdminResource(cfg, audit,
            new OrganizationRepository(null, Clock.systemUTC(), UuidGenerator.standard()),
            com.avandocmsg.messenger.core.bootstrap.CoreModule.organizationApplicationService(null),
            new RetentionPolicyRepository(null),
            new ChatRepository(null, Clock.systemUTC(), UuidGenerator.standard()),
            new ChatRetentionPolicyRepository(null),
            new ExportSuggestedHandler(audit),
            seed,
            new ExportJobEnqueuer(null, audit, mock(NatsOutboundPort.class), UuidGenerator.standard()),
            null,
            new ExportFileAccess(cfg),
            mock(NatsOutboundPort.class),
            null,
            null,
            null,
            null,
            null,
            I18nTestFixtures.messagesEn());
    }

    private static SecurityContext adminSc() {
        var actor = UUID.randomUUID();
        var principal = new UserPrincipal(actor.toString(), "csadmin", Set.of("admin"));
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return principal.hasRealmRole(role);
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        };
    }
}
