package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.platform.dto.FederationStatusResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/platform/federation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform", description = "Product platform capabilities and federation scaffold")
public class FederationStatusResource {

    private final FederationStatusService federationStatusService;
    private final UserLookupPort userLookupPort;
    private final AppConfig appConfig;

    @Inject
    public FederationStatusResource(
        FederationStatusService federationStatusService,
        UserLookupPort userLookupPort,
        AppConfig appConfig
    ) {
        this.federationStatusService = federationStatusService;
        this.userLookupPort = userLookupPort;
        this.appConfig = appConfig;
    }

    @GET
    @Path("/status")
    @Operation(summary = "Federation status", description = "Public scaffold or MVP when trusts exist")
    public FederationStatusResponse status(@Context SecurityContext securityContext) {
        try {
            var userId = CurrentUserId.uuid(securityContext);
            var orgId = resolveOrgId(userId);
            if (orgId != null) {
                return federationStatusService.statusForOrg(orgId);
            }
        } catch (Exception ignored) {
            // anonymous / invalid token — fall through to global
        }
        return federationStatusService.globalStatus();
    }

    @GET
    @Path("/directory")
    @Operation(summary = "Federation holding directory", description = "Trusted partner orgs for current user org")
    public com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse directory(
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        return federationStatusService.directoryForOrg(resolveOrgId(userId));
    }

    private UUID resolveOrgId(UUID userId) {
        var profile = userLookupPort.findById(userId).orElse(null);
        if (profile != null && profile.orgId() != null && !profile.orgId().isBlank()) {
            return UUID.fromString(profile.orgId());
        }
        return appConfig.defaultOrgId().orElse(null);
    }
}
