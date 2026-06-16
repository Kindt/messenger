package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/v1/integrations/outbound")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Plugin outbound", description = "Spec 014: external systems push messages into chat")
public class PluginOutboundResource {

    private final PluginOutboundService outboundService;

    @Inject
    public PluginOutboundResource(PluginOutboundService outboundService) {
        this.outboundService = outboundService;
    }

    public record OutboundBody(String text, String format) {}

    public record OutboundResponse(boolean ok, MessageResponse message) {}

    @POST
    @Path("{instanceId}")
    @Operation(summary = "Deliver outbound notification to configured chat")
    public Response deliver(
        @PathParam("instanceId") UUID instanceId,
        @HeaderParam("X-Plugin-Outbound-Token") String token,
        OutboundBody body
    ) {
        var req = new PluginOutboundService.OutboundRequest(
            body != null ? body.text() : null,
            body != null ? body.format() : "markdown"
        );
        var result = outboundService.deliver(instanceId, token, req);
        return switch (result.outcome()) {
            case DELIVERED -> Response.ok(new OutboundResponse(true, result.message())).build();
            case NOT_FOUND -> Response.status(404).entity(err(result.errorKey())).build();
            case UNAUTHORIZED -> Response.status(401).entity(err(result.errorKey())).build();
            case MISCONFIGURED -> Response.status(409).entity(err(result.errorKey())).build();
            case SEND_FAILED -> Response.status(502).entity(err(result.errorKey())).build();
        };
    }

    private Map<String, String> err(String key) {
        return Map.of("error", outboundService.localizedError(key));
    }
}
