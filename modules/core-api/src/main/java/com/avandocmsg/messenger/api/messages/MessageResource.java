package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.EditMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.ForwardMessageRequest;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PlaintextPreviewResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionRequest;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.application.MessageDomainMapper;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.api.metrics.ApiDeniedMetrics;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/chats/{chatId}/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Messages", description = "Message send, receive, and management")
public class MessageResource {

    private final MessageService messageService;
    private final MessageApplicationService messageApplicationService;
    private final AppConfig appConfig;
    private final UserMessageSource messages;

    @Inject
    public MessageResource(MessageService messageService,
                             MessageApplicationService messageApplicationService,
                             AppConfig appConfig,
                             UserMessageSource messages) {
        this.messageService = messageService;
        this.messageApplicationService = messageApplicationService;
        this.appConfig = appConfig;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Send message", description = "Send a message to a chat")
    @ApiResponse(responseCode = "201", description = "Message sent",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response send(@PathParam("chatId") String chatIdStr,
                         SendMessageRequest request,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        if (request.content() == null || request.content().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.content_required")))
                .build();
        }
        if (request.visibilityTtlSeconds() != null) {
            int ttl = request.visibilityTtlSeconds();
            int max = appConfig.visibilityTtlMaxSeconds();
            if (ttl < 1 || ttl > max) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.format("error.message.ttl_range", max)))
                    .build();
            }
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        UUID replyToMsgId = null;
        if (request.replyToMsgId() != null && !request.replyToMsgId().isBlank()) {
            replyToMsgId = UuidParams.required(request.replyToMsgId(), "reply_to_msg_id");
        }
        if (!messageApplicationService.isChatMember(ChatId.of(chatId), UserId.of(userId))) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.send_denied.not_member")))
                .build();
        }
        var msg = messageApplicationService.sendMessage(chatId, userId, request, replyToMsgId);
        if (msg == null) {
            var denied = messageApplicationService.sendBlockedReason(chatId, userId);
            if (denied.isPresent()) {
                ApiDeniedMetrics.messageSendDenied();
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiError(403, messages.get(denied.get())))
                    .build();
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.message.send_failed")))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(msg).build();
    }

    @GET
    @Operation(summary = "List messages", description = "Get messages in a chat (paginated)")
    @ApiResponse(responseCode = "200", description = "List of messages",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = MessageResponse.class))))
    public Response list(@PathParam("chatId") String chatIdStr,
                         @QueryParam("limit") @DefaultValue("50") int limit,
                         @QueryParam("before") String beforeStr,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (!messageApplicationService.canAccessChat(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        UUID before = null;
        if (beforeStr != null && !beforeStr.isBlank()) {
            before = UuidParams.required(beforeStr, "before");
        }
        var messages = messageApplicationService.listMessages(chatId, userId, limit, before);
        return Response.ok(messages).build();
    }

    @GET
    @Path("/{msgId}/plaintext-preview")
    @Operation(summary = "E2EE plaintext preview", description = "Server-side decrypt for e2ee-* messages (MLS stub on server)")
    @ApiResponse(responseCode = "200", description = "Decrypted plaintext",
        content = @Content(schema = @Schema(implementation = PlaintextPreviewResponse.class)))
    @ApiResponse(responseCode = "404", description = "Not found or not decryptable",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response plaintextPreview(@PathParam("chatId") String chatIdStr,
                                     @PathParam("msgId") String msgIdStr,
                                     @Context SecurityContext securityContext) {
        if ("active".equalsIgnoreCase(appConfig.mlsStatus())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.plaintext_preview_disabled")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        var plain = messageApplicationService.plaintextPreview(chatId, msgId, userId);
        if (plain == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.not_found")))
                .build();
        }
        return Response.ok(new PlaintextPreviewResponse(plain)).build();
    }

    @GET
    @Path("/{msgId}")
    @Operation(summary = "Get message", description = "Get a single message by ID")
    @ApiResponse(responseCode = "200", description = "Message",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "404", description = "Message not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response getById(@PathParam("chatId") String chatIdStr,
                            @PathParam("msgId") String msgIdStr,
                            @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var msg = messageApplicationService
            .getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId))
            .map(MessageDomainMapper::toResponse)
            .orElse(null);
        if (msg == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.not_found")))
                .build();
        }
        return Response.ok(msg).build();
    }

    @PATCH
    @Path("/{msgId}")
    @Operation(summary = "Edit message", description = "Edit message content (sender only)")
    @ApiResponse(responseCode = "200", description = "Message updated",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "403", description = "Not authorized",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response edit(@PathParam("chatId") String chatIdStr,
                         @PathParam("msgId") String msgIdStr,
                         EditMessageRequest request,
                         @Context SecurityContext securityContext) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.content_required")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        if (messageApplicationService
            .getMessageForMember(ChatId.of(chatId), MessageId.of(msgId), UserId.of(userId))
            .isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.not_found")))
                .build();
        }
        var msg = messageApplicationService.editMessage(chatId, msgId, userId, request.content());
        if (msg == null) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.cannot_edit")))
                .build();
        }
        return Response.ok(msg).build();
    }

    @DELETE
    @Path("/{msgId}")
    @Operation(summary = "Delete message", description = "Soft-delete a message (sender only)")
    @ApiResponse(responseCode = "204", description = "Message deleted")
    @ApiResponse(responseCode = "403", description = "Not authorized",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response delete(@PathParam("chatId") String chatIdStr,
                           @PathParam("msgId") String msgIdStr,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var ok = messageApplicationService.deleteMessage(chatId, msgId, userId);
        if (!ok) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.cannot_delete")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{msgId}/versions")
    @Operation(summary = "Message versions", description = "Get edit history of a message")
    @ApiResponse(responseCode = "200", description = "List of versions",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = MessageVersionResponse.class))))
    public Response versions(@PathParam("chatId") String chatIdStr,
                             @PathParam("msgId") String msgIdStr,
                             @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!messageApplicationService.canAccessChat(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var versions = messageApplicationService.getMessageVersions(chatId, msgId, userId);
        return Response.ok(versions).build();
    }

    @POST
    @Path("/{msgId}/reactions")
    @Operation(summary = "Add reaction", description = "Add a reaction to a message")
    @ApiResponse(responseCode = "201", description = "Reaction added")
    @ApiResponse(responseCode = "400", description = "Invalid reaction",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response addReaction(@PathParam("chatId") String chatIdStr,
                                @PathParam("msgId") String msgIdStr,
                                ReactionRequest request,
                                @Context SecurityContext securityContext) {
        if (request == null || request.reaction() == null || request.reaction().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.reaction_required")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (messageApplicationService.getMessageForMember(
            com.avandocmsg.messenger.core.domain.ChatId.of(chatId),
            com.avandocmsg.messenger.core.domain.MessageId.of(msgId),
            com.avandocmsg.messenger.core.domain.UserId.of(userId)).isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var ok = messageApplicationService.addReaction(chatId, msgId, userId, request.reaction());
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.not_found")))
                .build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{msgId}/reactions")
    @Operation(summary = "Remove reaction", description = "Remove a reaction from a message")
    @ApiResponse(responseCode = "204", description = "Reaction removed")
    @ApiResponse(responseCode = "404", description = "Reaction not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response removeReaction(@PathParam("chatId") String chatIdStr,
                                   @PathParam("msgId") String msgIdStr,
                                   ReactionRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null || request.reaction() == null || request.reaction().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.reaction_required")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (messageApplicationService.getMessageForMember(
            com.avandocmsg.messenger.core.domain.ChatId.of(chatId),
            com.avandocmsg.messenger.core.domain.MessageId.of(msgId),
            com.avandocmsg.messenger.core.domain.UserId.of(userId)).isEmpty()) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var ok = messageApplicationService.removeReaction(chatId, msgId, userId, request.reaction());
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.reaction_not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{msgId}/reactions")
    @Operation(summary = "List reactions", description = "Get all reactions on a message")
    @ApiResponse(responseCode = "200", description = "List of reactions",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReactionResponse.class))))
    public Response listReactions(@PathParam("chatId") String chatIdStr,
                                  @PathParam("msgId") String msgIdStr,
                                  @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!messageApplicationService.canAccessChat(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var reactions = messageApplicationService.getReactions(chatId, msgId, userId);
        return Response.ok(reactions).build();
    }

    @POST
    @Path("/{msgId}/pin")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Pin message", description = "Pin a message in the chat")
    @ApiResponse(responseCode = "201", description = "Message pinned")
    @ApiResponse(responseCode = "400", description = "Cannot pin",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response pinMessage(@PathParam("chatId") String chatIdStr,
                               @PathParam("msgId") String msgIdStr,
                               @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!messageApplicationService.isChatMember(ChatId.of(chatId), UserId.of(userId))) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var ok = messageApplicationService.pinMessage(chatId, msgId, userId);
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.not_found")))
                .build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{msgId}/pin")
    @Consumes(MediaType.WILDCARD)
    @Operation(summary = "Unpin message", description = "Unpin a message from the chat")
    @ApiResponse(responseCode = "204", description = "Message unpinned")
    public Response unpinMessage(@PathParam("chatId") String chatIdStr,
                                 @PathParam("msgId") String msgIdStr,
                                 @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!messageApplicationService.isChatMember(ChatId.of(chatId), UserId.of(userId))) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var ok = messageApplicationService.unpinMessage(chatId, msgId, userId);
        if (!ok) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.message.pinned_not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/pins")
    @Operation(summary = "List pinned messages", description = "Get all pinned messages in a chat")
    @ApiResponse(responseCode = "200", description = "List of pinned messages",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PinnedMessageResponse.class))))
    public Response listPinned(@PathParam("chatId") String chatIdStr,
                               @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!messageApplicationService.canAccessChat(chatId, userId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.message.not_member")))
                .build();
        }
        var pinned = messageApplicationService.getPinnedMessages(chatId, userId);
        return Response.ok(pinned).build();
    }

    @POST
    @Path("/{msgId}/forward")
    @Operation(summary = "Forward message", description = "Copy message to another chat (e.g. Saved vault)")
    @ApiResponse(responseCode = "201", description = "Forwarded",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Cannot forward",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response forward(@PathParam("chatId") String chatIdStr,
                            @PathParam("msgId") String msgIdStr,
                            ForwardMessageRequest request,
                            @Context SecurityContext securityContext) {
        if (request == null || request.targetChatId() == null || request.targetChatId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.target_chat_required")))
                .build();
        }
        var sourceChatId = UuidParams.required(chatIdStr, "chat_id");
        var msgId = UuidParams.required(msgIdStr, "message_id");
        var targetChatId = UuidParams.required(request.targetChatId(), "target_chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var msg = messageApplicationService.forwardMessage(sourceChatId, msgId, userId, targetChatId);
        if (msg == null) {
            var denied = messageApplicationService.sendBlockedReason(sourceChatId, userId)
                .or(() -> messageApplicationService.sendBlockedReason(targetChatId, userId));
            if (denied.isPresent()) {
                ApiDeniedMetrics.messageSendDenied();
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiError(403, messages.get(denied.get())))
                    .build();
            }
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.message.cannot_forward")))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(msg).build();
    }
}
