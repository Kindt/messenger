package com.avandocmsg.messenger.api.platform.stack;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public ExternalStackStatusResource() {
        this(new ExternalStackStatusService());
    }

    ExternalStackStatusResource(ExternalStackStatusService statusService) {
        this.statusService = statusService;
    }

    @GET
    @Path("status")
    @Operation(summary = "External stack manifest status")
    public ExternalStackStatusService.ExternalStackStatusResponse status() {
        return statusService.status(List.of());
    }

    @GET
    @Path("profiles")
    @Operation(summary = "External stack connector profile status")
    public ExternalStackStatusService.ExternalStackProfileStatusResponse profiles() {
        return statusService.profileStatus(List.of());
    }
}
