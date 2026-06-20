package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.ScheduleMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.ScheduledMessageResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.ScheduledMessagePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Path("/v1/chats/{chatId}/messages/scheduled")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Messages", description = "Scheduled messages")
public class ScheduledMessageResource {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ScheduledMessagePort scheduledMessagePort;
    private final ChatPersistencePort chatPersistencePort;
    private final UserMessageSource messages;

    @Inject
    public ScheduledMessageResource(
        ScheduledMessagePort scheduledMessagePort,
        ChatPersistencePort chatPersistencePort,
        UserMessageSource messages
    ) {
        this.scheduledMessagePort = scheduledMessagePort;
        this.chatPersistencePort = chatPersistencePort;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Schedule message for future delivery")
    public Response schedule(@PathParam("chatId") String chatId,
                             ScheduleMessageRequest request,
                             @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        if (request == null || request.content() == null || request.content().isBlank()
            || request.scheduledAt() == null || request.scheduledAt().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.scheduled_message.invalid")))
                .build();
        }
        if (chatPersistencePort.getMemberRole(cid, userId) == null) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.scheduled_message.not_member")))
                .build();
        }
        Instant scheduledAt;
        try {
            scheduledAt = Instant.parse(request.scheduledAt().trim());
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.scheduled_message.invalid")))
                .build();
        }
        UUID replyTo = parseOptionalUuid(request.replyToMsgId());
        UUID threadId = parseOptionalUuid(request.threadId());
        var id = scheduledMessagePort.create(new ScheduledMessagePort.CreateScheduled(
            cid, userId,
            request.type() != null ? request.type() : "text",
            request.content(),
            scheduledAt,
            replyTo,
            threadId,
            request.clientMsgId()));
        if (id == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.scheduled_message.create_failed")))
                .build();
        }
        return scheduledMessagePort.find(id)
            .map(row -> Response.status(Response.Status.CREATED).entity(toResponse(row)).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.scheduled_message.create_failed")))
                .build());
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }

    static ScheduledMessageResponse toResponse(ScheduledMessagePort.ScheduledRow row) {
        return new ScheduledMessageResponse(
            row.id().toString(),
            row.chatId().toString(),
            row.senderId().toString(),
            row.messageType(),
            row.content(),
            row.scheduledAt() != null ? ISO.format(row.scheduledAt()) : null,
            row.status(),
            row.replyToMsgId() != null ? row.replyToMsgId().toString() : null,
            row.threadId() != null ? row.threadId().toString() : null,
            row.clientMsgId(),
            row.sentMessageId() != null ? row.sentMessageId().toString() : null,
            row.createdAt() != null ? ISO.format(row.createdAt()) : null);
    }
}
