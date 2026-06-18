package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.AuthPolicyTestResponse;
import com.avandocmsg.messenger.api.auth.dto.TestAuthPolicyRequest;
import com.avandocmsg.messenger.api.auth.dto.UpdateAuthPolicyRequest;
import com.avandocmsg.messenger.api.auth.policy.AuthPolicyService;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/admin/orgs/{orgId}/auth-policy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Auth Policy", description = "Org-level login methods and Keycloak sync")
@RolesAllowed("admin")
public class AuthPolicyAdminResource {

    private final AuthPolicyService authPolicyService;
    private final UserMessageSource messages;

    @Inject
    public AuthPolicyAdminResource(AuthPolicyService authPolicyService, UserMessageSource messages) {
        this.authPolicyService = authPolicyService;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "Get org auth policy")
    public Response get(@PathParam("orgId") String orgIdStr) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdStr, "org_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        return authPolicyService.getPolicy(orgId)
            .map(p -> Response.ok(p).build())
            .orElseGet(() -> Response.status(404)
                .entity(new ApiError(404, messages.get("error.admin.org_not_found")))
                .build());
    }

    @PATCH
    @Operation(summary = "Update org auth policy and optionally apply to Keycloak")
    public Response patch(
        @PathParam("orgId") String orgIdStr,
        UpdateAuthPolicyRequest request,
        @Context SecurityContext securityContext
    ) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdStr, "org_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        var actor = CurrentUserId.uuid(securityContext);
        return authPolicyService.updatePolicy(orgId, request, actor)
            .map(p -> Response.ok(p).build())
            .orElseGet(() -> Response.status(404)
                .entity(new ApiError(404, messages.get("error.admin.org_not_found")))
                .build());
    }

    @POST
    @Path("test")
    @Operation(summary = "Test org auth provider connectivity (LDAP bind or TCP)")
    public Response test(
        @PathParam("orgId") String orgIdStr,
        TestAuthPolicyRequest request
    ) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdStr, "org_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        var providerId = request != null ? request.providerId() : null;
        return authPolicyService.testPolicy(orgId, providerId)
            .map(r -> Response.ok(new AuthPolicyTestResponse(r.ok(), r.message())).build())
            .orElseGet(() -> Response.status(404)
                .entity(new ApiError(404, messages.get("error.admin.org_not_found")))
                .build());
    }
}
