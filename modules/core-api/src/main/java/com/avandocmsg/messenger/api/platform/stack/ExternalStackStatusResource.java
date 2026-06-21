package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/v1/platform/external-stack")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform", description = "External stack profile manifests and validation status")
public class ExternalStackStatusResource {

    private final ExternalStackStatusService statusService;
    private final ExternalStackRuntimeManifestProvider manifestProvider;

    public ExternalStackStatusResource() {
        this(new ExternalStackStatusService(), null);
    }

    @Inject
    public ExternalStackStatusResource(AppConfig appConfig) {
        this(new ExternalStackStatusService(), new ExternalStackRuntimeManifestProvider(appConfig));
    }

    ExternalStackStatusResource(ExternalStackRuntimeManifestProvider manifestProvider) {
        this(new ExternalStackStatusService(), manifestProvider);
    }

    ExternalStackStatusResource(ExternalStackStatusService statusService, ExternalStackRuntimeManifestProvider manifestProvider) {
        this.statusService = statusService;
        this.manifestProvider = manifestProvider;
    }

    @GET
    @Path("status")
    @Operation(summary = "External stack manifest status")
    public ExternalStackStatusService.ExternalStackStatusResponse status() {
        return statusService.status(manifestProvider != null ? manifestProvider.observations() : List.of());
    }

    @GET
    @Path("profiles")
    @Operation(summary = "External stack connector profile status")
    public ExternalStackStatusService.ExternalStackProfileStatusResponse profiles() {
        return statusService.profileStatus(manifestProvider != null ? manifestProvider.profiles() : List.of());
    }
}
