package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.phase5.dto.GuestRedeemResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/conferences/guest")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Conference ADR", description = "Guest link redeem (T02309)")
public class ConferenceGuestRedeemResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public ConferenceGuestRedeemResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GET
    @Path("{token}")
    @Operation(summary = "Redeem guest conference link (public)")
    public Response redeem(@PathParam("token") String token) {
        return service.redeemGuestLink(token)
            .map(row -> Response.ok(new GuestRedeemResponse(
                row.conferenceId().toString(),
                row.chatId().toString(),
                row.waitingRoom(),
                row.status())).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.phase5.not_found")))
                .build());
    }
}
