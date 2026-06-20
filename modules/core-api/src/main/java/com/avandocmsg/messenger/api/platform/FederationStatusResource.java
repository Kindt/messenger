package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.dto.FederationStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/v1/platform/federation")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform", description = "Product platform capabilities and federation scaffold")
public class FederationStatusResource {

    @GET
    @Path("/status")
    @Operation(summary = "Federation scaffold status", description = "Public read-only; cross-org messaging not enabled until partner pilot")
    public FederationStatusResponse status() {
        return new FederationStatusResponse(
            "scaffold",
            false,
            List.of(),
            "Cross-org federation MVP pending; see docs/adr/ADR-federation-scaffold.md"
        );
    }
}
