package com.avandocmsg.messenger.api.platform;

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

    @Inject
    public FederationStatusResource(FederationStatusService federationStatusService, UserLookupPort userLookupPort) {
        this.federationStatusService = federationStatusService;
        this.userLookupPort = userLookupPort;
    }

    @GET
    @Path("/status")
    @Operation(summary = "Federation status", description = "Public scaffold or MVP when trusts exist")
    public FederationStatusResponse status(@Context SecurityContext securityContext) {
        try {
            var userId = CurrentUserId.uuid(securityContext);
            var profile = userLookupPort.findById(userId).orElse(null);
            if (profile != null && profile.orgId() != null && !profile.orgId().isBlank()) {
                return federationStatusService.statusForOrg(UUID.fromString(profile.orgId()));
            }
        } catch (Exception ignored) {
            // anonymous / invalid token — fall through to global
        }
        return federationStatusService.globalStatus();
    }
}
