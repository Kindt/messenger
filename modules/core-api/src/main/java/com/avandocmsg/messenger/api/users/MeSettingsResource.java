package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.users.dto.MeSettingsResponse;
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

@Path("/v1/me/settings")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Settings", description = "Authenticated client settings")
public class MeSettingsResource {

    private final AppConfig appConfig;

    @Inject
    public MeSettingsResource(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GET
    @Operation(summary = "Client settings", description = "Web Push VAPID public key and related client hints")
    public Response settings(@Context SecurityContext securityContext) {
        CurrentUserId.uuid(securityContext);
        var push = new MeSettingsResponse.PushSettings(
            appConfig.webClientVapidPublicKey().orElse(null));
        return Response.ok(new MeSettingsResponse(push)).build();
    }
}
