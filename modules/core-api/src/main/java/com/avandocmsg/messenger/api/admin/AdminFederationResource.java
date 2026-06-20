package com.avandocmsg.messenger.api.admin;

import com.avandocmsg.messenger.api.admin.dto.FederationTrustCreateRequest;
import com.avandocmsg.messenger.api.admin.dto.FederationTrustResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/admin/federation/trust")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin federation", description = "Cross-org trust registry (spec 022 T02308 MVP)")
public class AdminFederationResource {

    private final FederationTrustPort federationTrustPort;
    private final UserLookupPort userLookupPort;

    @Inject
    public AdminFederationResource(FederationTrustPort federationTrustPort, UserLookupPort userLookupPort) {
        this.federationTrustPort = federationTrustPort;
        this.userLookupPort = userLookupPort;
    }

    @GET
    @Operation(summary = "List federation trusts for actor org")
    public Response list(@Context SecurityContext securityContext) {
        var orgId = actorOrgId(securityContext);
        if (orgId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var rows = federationTrustPort.listForOrg(orgId).stream().map(FederationTrustResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Operation(summary = "Create or update federation trust with partner org")
    public Response create(FederationTrustCreateRequest body, @Context SecurityContext securityContext) {
        var orgId = actorOrgId(securityContext);
        if (orgId == null || body == null || body.partnerOrgId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var status = body.status() != null && !body.status().isBlank() ? body.status() : "active";
        var id = federationTrustPort.insert(orgId, body.partnerOrgId(), status, body.expiresAt());
        if (id == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return federationTrustPort.findById(id)
            .map(row -> Response.status(Response.Status.CREATED).entity(FederationTrustResponse.from(row)).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
    }

    private UUID actorOrgId(SecurityContext securityContext) {
        var actorId = CurrentUserId.uuid(securityContext);
        var profile = userLookupPort.findById(actorId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return null;
        }
        return UUID.fromString(profile.orgId());
    }
}
