package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.admin.dto.SetUserOrganizationRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ChatRetentionPolicyRepository;
import com.avandocmsg.messenger.api.repository.RetentionPolicyRepository;
import com.avandocmsg.messenger.api.export.AdminExportComplianceSeed;
import com.avandocmsg.messenger.api.export.ExportFileAccess;
import com.avandocmsg.messenger.api.export.ExportJobEnqueuer;
import com.avandocmsg.messenger.api.export.ExportSuggestedHandler;
import com.avandocmsg.messenger.core.bootstrap.CoreModule;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdminResourceTest {

    @Test
    void session_mapsUserPrincipal() {
        var id = UUID.randomUUID().toString();
        var cfg = new AppConfig() {
            @Override
            public String version() {
                return "9.9.9-test";
            }
        };
        var resource = new AdminResource(cfg, new AuditRepository(null),
            CoreModule.organizationApplicationService(null, UuidGenerator.standard()),
            new RetentionPolicyRepository(null),
            new ChatRepository(null, Clock.systemUTC(), UuidGenerator.standard()),
            new ChatRetentionPolicyRepository(null),
            new ExportSuggestedHandler(new AuditRepository(null)),
            mock(AdminExportComplianceSeed.class),
            new ExportJobEnqueuer(null, new AuditRepository(null), mock(NatsOutboundPort.class), UuidGenerator.standard()),
            null,
            new ExportFileAccess(cfg),
            mock(NatsOutboundPort.class),
            null,
            null,
            null,
            null,
            null,
            null,
            I18nTestFixtures.messagesEn());
        var principal = new UserPrincipal(id, "csadmin", Set.of("admin", "offline_access"));

        var sc = new SecurityContext() {
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

        var body = resource.session(sc);

        assertEquals(id, body.userId());
        assertEquals("csadmin", body.username());
        assertTrue(body.realmRoles().contains("admin"));
        assertEquals("9.9.9-test", body.apiVersion());
    }

    @Test
    void retentionPolicyPatchAuditDetails_serializesNullAges() throws Exception {
        var tree = new ObjectMapper().readTree(AdminResource.retentionPolicyPatchAuditDetails(
            null, null, false, true, false));
        assertTrue(tree.get("hot_message_body_max_age_days").isNull());
        assertTrue(tree.get("hot_metadata_min_age_days").isNull());
        assertFalse(tree.get("archive_metadata_enabled").booleanValue());
        assertTrue(tree.get("deep_archive_enabled").booleanValue());
        assertFalse(tree.get("legal_hold").booleanValue());
    }

    @Test
    void retentionPolicyPatchAuditDetails_serializesNumericAges() throws Exception {
        var tree = new ObjectMapper().readTree(AdminResource.retentionPolicyPatchAuditDetails(
            90, 14, true, false, true));
        assertEquals(90, tree.get("hot_message_body_max_age_days").intValue());
        assertEquals(14, tree.get("hot_metadata_min_age_days").intValue());
        assertTrue(tree.get("archive_metadata_enabled").booleanValue());
        assertFalse(tree.get("deep_archive_enabled").booleanValue());
        assertTrue(tree.get("legal_hold").booleanValue());
    }

    @Test
    void userOrganizationSetAuditDetails_containsOrgId() throws Exception {
        var orgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var tree = new ObjectMapper().readTree(AdminResource.userOrganizationSetAuditDetails(orgId));
        assertEquals(orgId.toString(), tree.get("org_id").asText());
    }

    @Test
    void organizationCreateAuditDetails_escapesName() throws Exception {
        var raw = "Acme \"Beta\" \\ Co.";
        var tree = new ObjectMapper().readTree(AdminResource.organizationCreateAuditDetails(raw));
        assertEquals(raw, tree.get("name").asText());
    }

    @Test
    void organizationDeleteAuditDetails_matchesCreateJsonShape() {
        var name = "Contoso";
        assertEquals(
            AdminResource.organizationCreateAuditDetails(name),
            AdminResource.organizationDeleteAuditDetails(name));
    }

    @Test
    void setUserOrganization_invalidPathUserId_throwsInvalidUuidParameterException() {
        var resource = adminResourceWithNullRepos();
        var orgId = UUID.randomUUID().toString();
        var sc = adminSecurityContext();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.setUserOrganization("not-a-uuid", new SetUserOrganizationRequest(orgId), sc));
    }

    @Test
    void setUserOrganization_invalidBodyOrgId_throwsInvalidUuidParameterException() {
        var resource = adminResourceWithNullRepos();
        var userId = UUID.randomUUID().toString();
        var sc = adminSecurityContext();
        assertThrows(InvalidUuidParameterException.class,
            () -> resource.setUserOrganization(userId, new SetUserOrganizationRequest("also-not-uuid"), sc));
    }

    @Test
    void setUserOrganization_nullBody_returns400() {
        var resource = adminResourceWithNullRepos();
        var userId = UUID.randomUUID().toString();
        var sc = adminSecurityContext();
        var res = resource.setUserOrganization(userId, null, sc);
        assertEquals(400, res.getStatus());
    }

    private static AdminResource adminResourceWithNullRepos() {
        var cfg = new AppConfig() {
            @Override
            public String version() {
                return "0-test";
            }
        };
        return new AdminResource(cfg, new AuditRepository(null),
            CoreModule.organizationApplicationService(null, UuidGenerator.standard()),
            new RetentionPolicyRepository(null),
            new ChatRepository(null, Clock.systemUTC(), UuidGenerator.standard()),
            new ChatRetentionPolicyRepository(null),
            new ExportSuggestedHandler(new AuditRepository(null)),
            mock(AdminExportComplianceSeed.class),
            new ExportJobEnqueuer(null, new AuditRepository(null), mock(NatsOutboundPort.class), UuidGenerator.standard()),
            null,
            new ExportFileAccess(cfg),
            mock(NatsOutboundPort.class),
            null,
            null,
            null,
            null,
            null,
            null,
            I18nTestFixtures.messagesEn());
    }

    private static SecurityContext adminSecurityContext() {
        var actorId = UUID.randomUUID().toString();
        var principal = new UserPrincipal(actorId, "csadmin", Set.of("admin"));
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
