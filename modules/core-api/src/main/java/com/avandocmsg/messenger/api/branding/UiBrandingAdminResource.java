package com.avandocmsg.messenger.api.branding;

import com.avandocmsg.messenger.api.branding.dto.BrandingDtos;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.core.application.UiBrandingService;
import com.avandocmsg.messenger.core.port.AuditPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;
import java.util.UUID;

@Path("/v1/admin/branding")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "Admin Branding", description = "Platform/org UI branding management")
public class UiBrandingAdminResource {
    private final UiBrandingService brandingService;
    private final AuditPort auditPort;

    @Inject
    public UiBrandingAdminResource(UiBrandingService brandingService, AuditPort auditPort) {
        this.brandingService = brandingService;
        this.auditPort = auditPort;
    }

    @GET
    @Path("platform")
    @Operation(summary = "Get platform branding")
    public Response getPlatform() {
        return Response.ok(BrandingDtos.fromPlatform(brandingService.getPlatform())).build();
    }

    @PUT
    @Path("platform")
    @Operation(summary = "Upsert platform branding")
    public Response putPlatform(BrandingDtos.PlatformBrandingUpsertRequest request,
                                @Context SecurityContext securityContext) {
        if (request == null || request.palette() == null || request.palette().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "palette is required"))
                .build();
        }
        try {
            var row = brandingService.upsertPlatform(
                request.palette(),
                request.tokenOverrides(),
                request.customCss(),
                request.brandTitle(),
                request.demoSkinsEnabled() != null && request.demoSkinsEnabled(),
                request.shellLayout()
            );
            var actor = CurrentUserId.uuid(securityContext);
            auditPort.record(actor, "branding.platform.updated", "branding_platform", "1", null);
            return Response.ok(BrandingDtos.fromPlatform(row)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("orgs/{orgId}")
    @Operation(summary = "Get org branding")
    public Response getOrg(@PathParam("orgId") String orgIdRaw) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdRaw, "org_id");
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "invalid org_id"))
                .build();
        }
        return Response.ok(BrandingDtos.fromOrg(brandingService.getOrg(orgId))).build();
    }

    @PUT
    @Path("orgs/{orgId}")
    @Operation(summary = "Upsert org branding")
    public Response putOrg(@PathParam("orgId") String orgIdRaw,
                           BrandingDtos.OrgBrandingUpsertRequest request,
                           @Context SecurityContext securityContext) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdRaw, "org_id");
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "invalid org_id"))
                .build();
        }
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "request is required"))
                .build();
        }
        try {
            var row = brandingService.upsertOrg(
                orgId,
                request.palette(),
                request.tokenOverrides() != null ? request.tokenOverrides() : Map.of(),
                request.customCss(),
                request.brandTitle(),
                request.shellLayout()
            );
            var actor = CurrentUserId.uuid(securityContext);
            auditPort.record(actor, "branding.org.updated", "organization", orgId.toString(), null);
            return Response.ok(BrandingDtos.fromOrg(row)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("orgs/{orgId}")
    @Operation(summary = "Delete org branding override")
    public Response deleteOrg(@PathParam("orgId") String orgIdRaw,
                              @Context SecurityContext securityContext) {
        UUID orgId;
        try {
            orgId = UuidParams.required(orgIdRaw, "org_id");
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "invalid org_id"))
                .build();
        }
        var deleted = brandingService.deleteOrg(orgId);
        if (deleted) {
            var actor = CurrentUserId.uuid(securityContext);
            auditPort.record(actor, "branding.org.deleted", "organization", orgId.toString(), null);
        }
        return Response.noContent().build();
    }
}
