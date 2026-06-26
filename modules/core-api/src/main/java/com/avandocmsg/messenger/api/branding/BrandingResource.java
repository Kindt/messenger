package com.avandocmsg.messenger.api.branding;

import com.avandocmsg.messenger.api.branding.dto.BrandingDtos;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.application.BrandingWebManifestBuilder;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UiBrandingService;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.UiBrandingPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/branding")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Branding", description = "UI branding snapshot")
public class BrandingResource {
    private final UiBrandingService brandingService;
    private final UserLookupPort userLookupPort;
    private final OrganizationApplicationService organizationApplicationService;
    private final AvatarApplicationService avatarApplicationService;

    @Inject
    public BrandingResource(
        UiBrandingService brandingService,
        UserLookupPort userLookupPort,
        OrganizationApplicationService organizationApplicationService,
        AvatarApplicationService avatarApplicationService
    ) {
        this.brandingService = brandingService;
        this.userLookupPort = userLookupPort;
        this.organizationApplicationService = organizationApplicationService;
        this.avatarApplicationService = avatarApplicationService;
    }

    @GET
    @Operation(summary = "Public branding snapshot")
    public Response getPublicBranding() {
        return Response.ok(BrandingDtos.fromMerged(brandingService.getPublicBranding())).build();
    }

    @GET
    @Path("me")
    @Operation(summary = "Branding merged for current user organization")
    public Response getMeBranding(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userLookupPort.findById(userId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Response.ok(BrandingDtos.fromMerged(brandingService.getPublicBranding())).build();
        }
        UUID orgId;
        try {
            orgId = UUID.fromString(profile.orgId());
        } catch (IllegalArgumentException ignored) {
            return Response.ok(BrandingDtos.fromMerged(brandingService.getPublicBranding())).build();
        }
        var logoUrl = resolveOrgLogoUrl(userId, orgId);
        return Response.ok(BrandingDtos.fromMerged(brandingService.getForOrg(orgId, logoUrl))).build();
    }

    @GET
    @Path("me/manifest.webmanifest")
    @Produces("application/manifest+json")
    @Operation(summary = "PWA manifest merged for current user organization")
    public Response getMeWebManifest(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userLookupPort.findById(userId).orElse(null);
        UiBrandingPort.MergedBranding merged;
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            merged = brandingService.getPublicBranding();
        } else {
            try {
                var orgId = UUID.fromString(profile.orgId());
                var logoUrl = resolveOrgLogoUrl(userId, orgId);
                merged = brandingService.getForOrg(orgId, logoUrl);
            } catch (IllegalArgumentException ignored) {
                merged = brandingService.getPublicBranding();
            }
        }
        return Response.ok(BrandingWebManifestBuilder.build(merged)).build();
    }

    @GET
    @Path("manifest.webmanifest")
    @Produces("application/manifest+json")
    @Operation(summary = "PWA manifest with org/platform theme colors")
    public Response getWebManifest() {
        var merged = brandingService.getPublicBranding();
        return Response.ok(BrandingWebManifestBuilder.build(merged)).build();
    }

    private String resolveOrgLogoUrl(UUID userId, UUID orgId) {
        if (userId == null || orgId == null) {
            return null;
        }
        return organizationApplicationService.findById(OrganizationId.of(orgId))
            .filter(org -> org.logoFileId() != null)
            .map(org -> avatarApplicationService.mintOrgLogoUrl(UserId.of(userId), org.logoFileId()))
            .orElse(null);
    }
}
