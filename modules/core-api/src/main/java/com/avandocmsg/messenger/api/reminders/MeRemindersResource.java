package com.avandocmsg.messenger.api.reminders;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.reminders.dto.CreateReminderRequest;
import com.avandocmsg.messenger.api.reminders.dto.ReminderResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.MessageReminderPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Path("/v1/me/reminders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reminders", description = "Personal message reminders")
public class MeRemindersResource {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final MessageReminderPort messageReminderPort;
    private final ChatPersistencePort chatPersistencePort;
    private final UserMessageSource messages;

    @Inject
    public MeRemindersResource(
        MessageReminderPort messageReminderPort,
        ChatPersistencePort chatPersistencePort,
        UserMessageSource messages
    ) {
        this.messageReminderPort = messageReminderPort;
        this.chatPersistencePort = chatPersistencePort;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Create message reminder")
    public Response create(CreateReminderRequest request, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        if (request == null || request.chatId() == null || request.messageId() == null
            || request.remindAt() == null || request.remindAt().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.reminder.invalid")))
                .build();
        }
        var chatId = UuidParams.required(request.chatId(), "chat_id");
        var messageId = UuidParams.required(request.messageId(), "message_id");
        if (chatPersistencePort.getMemberRole(chatId, userId) == null) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.reminder.not_member")))
                .build();
        }
        Instant remindAt;
        try {
            remindAt = Instant.parse(request.remindAt().trim());
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.reminder.invalid")))
                .build();
        }
        var id = messageReminderPort.create(new MessageReminderPort.CreateReminder(
            userId, chatId, messageId, remindAt));
        if (id == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.reminder.create_failed")))
                .build();
        }
        return messageReminderPort.find(id)
            .map(row -> Response.status(Response.Status.CREATED).entity(toResponse(row)).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.reminder.create_failed")))
                .build());
    }

    @GET
    @Operation(summary = "List pending reminders for current user")
    public Response list(@QueryParam("limit") @DefaultValue("50") int limit,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = messageReminderPort.listForUser(userId, limit).stream()
            .map(MeRemindersResource::toResponse)
            .toList();
        return Response.ok(rows).build();
    }

    private static ReminderResponse toResponse(MessageReminderPort.ReminderRow row) {
        return new ReminderResponse(
            row.id().toString(),
            row.chatId().toString(),
            row.messageId().toString(),
            row.remindAt() != null ? ISO.format(row.remindAt()) : null,
            row.status(),
            row.createdAt() != null ? ISO.format(row.createdAt()) : null);
    }
}
