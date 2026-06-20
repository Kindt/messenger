package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.dto.PlatformCapabilitiesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/platform")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform", description = "Product module capabilities (Base + add-ons)")
public class PlatformCapabilitiesResource {

    private final PlatformModuleRegistry registry;

    @Inject
    public PlatformCapabilitiesResource(PlatformModuleRegistry registry) {
        this.registry = registry;
    }

    @GET
    @Path("capabilities")
    @Operation(summary = "Platform capabilities",
        description = "Public snapshot of Base + add-on states for webui and integrations.")
    public PlatformCapabilitiesResponse capabilities() {
        return registry.toCapabilitiesResponse();
    }
}
