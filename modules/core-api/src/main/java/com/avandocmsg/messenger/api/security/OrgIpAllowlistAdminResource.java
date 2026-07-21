package com.avandocmsg.messenger.api.security;

import com.avandocmsg.messenger.api.security.dto.OrgIpAllowlistResponse;
import com.avandocmsg.messenger.api.security.dto.UpdateOrgIpAllowlistRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/v1/admin/orgs/{orgId}/ip-allowlist")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin IP allowlist", description = "Org IP allowlist (app-layer lab enforcement)")
@RolesAllowed("admin")
public class OrgIpAllowlistAdminResource {

    private final OrgIpAllowlistService allowlistService;
    private final UserMessageSource messages;

    @Inject
    public OrgIpAllowlistAdminResource(OrgIpAllowlistService allowlistService, UserMessageSource messages) {
        this.allowlistService = allowlistService;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "Get org IP allowlist policy")
    public Response get(@PathParam("orgId") String orgIdStr) {
        var orgId = parseOrg(orgIdStr);
        if (orgId == null) {
            return badRequest();
        }
        return allowlistService.get(orgId)
            .map(row -> Response.ok(toResponse(row)).build())
            .orElseGet(() -> Response.ok(new OrgIpAllowlistResponse(orgId.toString(), false, "")).build());
    }

    @PATCH
    @Operation(summary = "Update org IP allowlist policy")
    public Response patch(@PathParam("orgId") String orgIdStr, UpdateOrgIpAllowlistRequest request) {
        var orgId = parseOrg(orgIdStr);
        if (orgId == null) {
            return badRequest();
        }
        var enabled = request != null && Boolean.TRUE.equals(request.enabled());
        var cidrs = request != null ? request.allowedCidrs() : "";
        var saved = allowlistService.update(orgId, enabled, cidrs);
        return Response.ok(toResponse(saved)).build();
    }

    private static OrgIpAllowlistResponse toResponse(OrgIpAllowlistRepository.Row row) {
        return new OrgIpAllowlistResponse(row.orgId().toString(), row.enabled(), row.allowedCidrs());
    }

    private Response badRequest() {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, messages.get("error.invalid_parameter")))
            .build();
    }

    private static UUID parseOrg(String orgIdStr) {
        try {
            return UUID.fromString(orgIdStr);
        } catch (Exception e) {
            return null;
        }
    }
}
