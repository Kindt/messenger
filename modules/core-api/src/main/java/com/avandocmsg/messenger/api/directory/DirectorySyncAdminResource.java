package com.avandocmsg.messenger.api.directory;

import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/v1/admin/orgs/{orgId}/directory-sync")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Directory Sync", description = "LDAP directory sync status and manual run")
@RolesAllowed("admin")
public class DirectorySyncAdminResource {

    private final DirectorySyncService directorySyncService;
    private final UserMessageSource messages;

    @Inject
    public DirectorySyncAdminResource(DirectorySyncService directorySyncService, UserMessageSource messages) {
        this.directorySyncService = directorySyncService;
        this.messages = messages;
    }

    @GET
    @Path("/status")
    @Operation(summary = "Latest directory sync run status for org")
    public Response status(@PathParam("orgId") String orgIdStr) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdStr, "org_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        if (!directorySyncService.orgExists(orgId)) {
            return Response.status(404)
                .entity(new ApiError(404, messages.get("error.admin.org_not_found")))
                .build();
        }
        var latest = directorySyncService.latestStatus(orgId);
        if (latest.isEmpty()) {
            return Response.ok(DirectorySyncStatusResponse.empty(orgId)).build();
        }
        return Response.ok(DirectorySyncStatusResponse.from(orgId, latest.get())).build();
    }

    @POST
    @Path("/run")
    @Operation(summary = "Trigger LDAP directory sync for org")
    public Response run(@PathParam("orgId") String orgIdStr) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdStr, "org_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        return directorySyncService.syncFromLdap(orgId)
            .map(row -> Response.ok(DirectorySyncStatusResponse.from(orgId, row)).build())
            .orElseGet(() -> Response.status(404)
                .entity(new ApiError(404, messages.get("error.admin.org_not_found")))
                .build());
    }
}
