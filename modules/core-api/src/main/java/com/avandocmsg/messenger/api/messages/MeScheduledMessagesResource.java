package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.MeScheduledMessageResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.ScheduledMessagePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.format.DateTimeFormatter;

@Path("/v1/me/scheduled-messages")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Scheduled messages", description = "User scheduled outbound messages")
public class MeScheduledMessagesResource {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ScheduledMessagePort scheduledMessagePort;
    private final UserMessageSource messages;

    @Inject
    public MeScheduledMessagesResource(ScheduledMessagePort scheduledMessagePort, UserMessageSource messages) {
        this.scheduledMessagePort = scheduledMessagePort;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "List pending scheduled messages for current user")
    public Response list(@QueryParam("limit") @DefaultValue("50") int limit,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = scheduledMessagePort.listForSender(userId, limit).stream()
            .map(MeScheduledMessagesResource::toResponse)
            .toList();
        return Response.ok(rows).build();
    }

    @DELETE
    @Path("{messageId}")
    @Operation(summary = "Cancel pending scheduled message")
    public Response cancel(@PathParam("messageId") String messageId,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(messageId, "message_id");
        var row = scheduledMessagePort.find(id);
        if (row.isEmpty() || !row.get().senderId().equals(userId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.scheduled_message.not_found")))
                .build();
        }
        if (!"pending".equals(row.get().status())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.scheduled_message.cancel_failed")))
                .build();
        }
        if (!scheduledMessagePort.cancelPending(id, userId)) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.scheduled_message.cancel_failed")))
                .build();
        }
        return Response.noContent().build();
    }

    private static MeScheduledMessageResponse toResponse(ScheduledMessagePort.ScheduledRow row) {
        return new MeScheduledMessageResponse(
            row.id().toString(),
            row.chatId().toString(),
            row.messageType(),
            row.content(),
            row.scheduledAt() != null ? ISO.format(row.scheduledAt()) : null,
            row.status(),
            row.createdAt() != null ? ISO.format(row.createdAt()) : null);
    }
}
