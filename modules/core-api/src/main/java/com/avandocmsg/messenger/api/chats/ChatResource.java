package com.avandocmsg.messenger.api.chats;

import com.avandocmsg.messenger.api.chats.dto.AddMemberRequest;
import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import com.avandocmsg.messenger.api.chats.dto.CreateChatRequest;
import com.avandocmsg.messenger.api.chats.dto.BatchReadRequest;
import com.avandocmsg.messenger.api.chats.dto.MarkReadRequest;
import com.avandocmsg.messenger.api.chats.dto.MuteRequest;
import com.avandocmsg.messenger.api.chats.dto.PersonalFilterRequest;
import com.avandocmsg.messenger.api.chats.dto.UnreadCountResponse;
import com.avandocmsg.messenger.api.chats.dto.UpdateChatRequest;
import com.avandocmsg.messenger.api.chats.dto.UpdateRoleRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.security.TimingNormalization;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
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

import java.util.List;
import java.util.UUID;

@Path("/v1/chats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Chats", description = "Chat and group management")
public class ChatResource {

    private final ChatService chatService;
    private final ReadReceiptService readReceiptService;
    private final ChatApplicationService chatApplicationService;
    private final AppConfig appConfig;
    private final UserMessageSource messages;

    @Inject
    public ChatResource(ChatService chatService, ReadReceiptService readReceiptService,
                        ChatApplicationService chatApplicationService, AppConfig appConfig,
                        UserMessageSource messages) {
        this.chatService = chatService;
        this.readReceiptService = readReceiptService;
        this.chatApplicationService = chatApplicationService;
        this.appConfig = appConfig;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "List chats", description = "Get all chats the current user is a member of")
    @ApiResponse(responseCode = "200", description = "List of chats",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatResponse.class))))
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chats = chatService.list(userId);
        return Response.ok(chats).build();
    }

    @POST
    @Operation(summary = "Create chat", description = "Create a P2P or group chat")
    @ApiResponse(responseCode = "200", description = "Chat created or existing P2P returned",
        content = @Content(schema = @Schema(implementation = ChatResponse.class)))
    @ApiResponse(responseCode = "201", description = "Group chat created",
        content = @Content(schema = @Schema(implementation = ChatResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response create(CreateChatRequest request, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);

        if ("p2p".equals(request.type()) && request.memberIds() != null && request.memberIds().size() == 1) {
            var chat = chatService.findOrCreateP2P(userId, UuidParams.required(request.memberIds().get(0), "member_id"));
            if (chat == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.chat.cannot_create_p2p")))
                    .build();
            }
            return Response.ok(chat).build();
        }

        if ("group".equals(request.type())) {
            if (request.memberIds() != null) {
                for (var mid : request.memberIds()) {
                    UuidParams.required(mid, "member_id");
                }
            }
            var chat = chatService.createGroup(request.title(), userId, request.memberIds());
            if (chat == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError(400, messages.get("error.chat.cannot_create_group")))
                    .build();
            }
            return Response.status(Response.Status.CREATED).entity(chat).build();
        }

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, messages.get("error.chat.invalid_type")))
            .build();
    }

    @GET
    @Path("/{chatId}")
    @Operation(summary = "Get chat", description = "Get chat details by ID")
    @ApiResponse(responseCode = "200", description = "Chat details",
        content = @Content(schema = @Schema(implementation = ChatResponse.class)))
    @ApiResponse(responseCode = "404", description = "Chat not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response getById(@PathParam("chatId") String chatIdStr,
                            @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var minNs = appConfig.timingNormalizationMinNanos();
        if (minNs > 0) {
            return TimingNormalization.runWithMinimumDuration(minNs, () -> resolveGetById(chatId, userId));
        }
        return resolveGetById(chatId, userId);
    }

    private Response resolveGetById(UUID chatId, UUID userId) {
        if (chatApplicationService.getChatForMember(ChatId.of(chatId), UserId.of(userId)).isEmpty()) {
            TimingNormalization.padNotFoundExtra(appConfig.timingNotFoundExtraNanos());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.chat.not_found")))
                .build();
        }
        var chat = chatService.getById(chatId, userId);
        if (chat == null) {
            TimingNormalization.padNotFoundExtra(appConfig.timingNotFoundExtraNanos());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.chat.not_found")))
                .build();
        }
        return Response.ok(chat).build();
    }

    @PATCH
    @Path("/{chatId}")
    @Operation(summary = "Update chat", description = "Update chat title (owner/admin only)")
    public Response update(@PathParam("chatId") String chatIdStr,
                           UpdateChatRequest request,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        if (request.title() != null) {
            var ok = chatService.updateTitle(chatId, userId, request.title());
            if (!ok) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiError(403, messages.get("error.chat.not_authorized")))
                    .build();
            }
        }
        var chat = chatService.getById(chatId, userId);
        return chat != null ? Response.ok(chat).build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    @Path("/{chatId}/members")
    @Operation(summary = "Add member", description = "Add a user to a group chat (owner/admin only)")
    public Response addMember(@PathParam("chatId") String chatIdStr,
                              AddMemberRequest request,
                              @Context SecurityContext securityContext) {
        var actorId = CurrentUserId.uuid(securityContext);
        var ok = chatService.addMember(UuidParams.required(chatIdStr, "chat_id"), actorId,
            UuidParams.required(request.userId(), "user_id"));
        if (!ok) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.cannot_add_member")))
                .build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{chatId}/members/{userId}")
    @Operation(summary = "Remove member", description = "Remove a user from a group chat")
    public Response removeMember(@PathParam("chatId") String chatIdStr,
                                  @PathParam("userId") String targetUserIdStr,
                                  @Context SecurityContext securityContext) {
        var ok = chatService.removeMember(
            UuidParams.required(chatIdStr, "chat_id"),
            CurrentUserId.uuid(securityContext),
            UuidParams.required(targetUserIdStr, "user_id"));
        if (!ok) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.cannot_remove_member")))
                .build();
        }
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{chatId}/members/{userId}/role")
    @Operation(summary = "Set role", description = "Change member role (owner only)")
    public Response setRole(@PathParam("chatId") String chatIdStr,
                            @PathParam("userId") String targetUserIdStr,
                            UpdateRoleRequest request,
                            @Context SecurityContext securityContext) {
        var ok = chatService.setRole(
            UuidParams.required(chatIdStr, "chat_id"),
            CurrentUserId.uuid(securityContext),
            UuidParams.required(targetUserIdStr, "user_id"),
            request.role());
        if (!ok) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.cannot_set_role")))
                .build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/{chatId}/members")
    @Operation(summary = "List members", description = "Get all members of a chat")
    @ApiResponse(responseCode = "200", description = "List of members",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatMemberResponse.class))))
    @ApiResponse(responseCode = "403", description = "Caller is not a member",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response listMembers(@PathParam("chatId") String chatIdStr,
                                @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        return chatService.listMembersForViewer(chatId, userId)
            .map(members -> Response.ok(members).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.not_a_member")))
                .build());
    }

    @POST
    @Path("/{chatId}/mute")
    @Operation(summary = "Mute chat", description = "Mute or unmute notifications for this chat")
    public Response mute(@PathParam("chatId") String chatIdStr,
                         MuteRequest request,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        chatService.setMuted(UuidParams.required(chatIdStr, "chat_id"), userId, request.muted());
        return Response.ok().build();
    }

    @PATCH
    @Path("/{chatId}/personal-filter")
    @Operation(summary = "Personal filter", description = "Enable or disable personal message filter for this chat")
    @ApiResponse(responseCode = "200", description = "Filter updated")
    public Response personalFilter(@PathParam("chatId") String chatIdStr,
                                    PersonalFilterRequest request,
                                    @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        chatService.setPersonalFilter(UuidParams.required(chatIdStr, "chat_id"), userId, request.active());
        return Response.ok().build();
    }

    @POST
    @Path("/{chatId}/read")
    @Operation(summary = "Отметить прочитанным до сообщения", description = "ТЗ п. 12.2: агрегат read state; без тела — до последнего сообщения")
    public Response markRead(@PathParam("chatId") String chatIdStr,
                             MarkReadRequest request,
                             @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        final UUID upTo;
        if (request != null && request.upToMessageId() != null && !request.upToMessageId().isBlank()) {
            upTo = UuidParams.required(request.upToMessageId(), "up_to_message_id");
        } else {
            upTo = null;
        }
        var userId = CurrentUserId.uuid(securityContext);
        var ok = chatService.markRead(chatId, userId, upTo);
        if (!ok) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.chat.cannot_update_read_state")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{chatId}/unread-count")
    @Operation(summary = "Число непрочитанных (от других участников)")
    public Response unreadCount(@PathParam("chatId") String chatIdStr,
                                @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var n = chatService.unreadCount(chatId, userId);
        if (n < 0) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.not_a_member")))
                .build();
        }
        return Response.ok(new UnreadCountResponse(n)).build();
    }

    @POST
    @Path("/{chatId}/typing")
    @Operation(summary = "Индикатор набора", description = "Событие в NATS msg.typing (ТЗ п. 19)")
    public Response typing(@PathParam("chatId") String chatIdStr,
                           @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        chatService.publishTyping(chatId, userId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{chatId}/messages/{messageId}/read")
    @Operation(summary = "Per-message read receipt", description = "Marks one message as read by the current user")
    @ApiResponse(responseCode = "204", description = "Recorded")
    public Response markMessageRead(@PathParam("chatId") String chatIdStr,
                                    @PathParam("messageId") String messageIdStr,
                                    @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var messageId = UuidParams.required(messageIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        return mapReadReceiptResult(readReceiptService.markMessageRead(chatId, userId, messageId));
    }

    @POST
    @Path("/{chatId}/read-batch")
    @Operation(summary = "Batch read receipts", description = "Marks several messages as read")
    public Response markBatchRead(@PathParam("chatId") String chatIdStr,
                                  BatchReadRequest request,
                                  @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var ids = request == null || request.messageIds() == null
            ? List.<UUID>of()
            : request.messageIds().stream().map(s -> UuidParams.required(s, "message_id")).toList();
        return mapReadReceiptResult(readReceiptService.markBatchRead(chatId, userId, ids));
    }

    @GET
    @Path("/{chatId}/read-receipts")
    @Operation(summary = "List read receipts for a message")
    public Response listReadReceipts(@PathParam("chatId") String chatIdStr,
                                     @QueryParam("message_id") String messageIdStr,
                                     @QueryParam("offset") Integer offset,
                                     @QueryParam("limit") Integer limit,
                                     @Context SecurityContext securityContext) {
        if (messageIdStr == null || messageIdStr.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.read_receipt.message_id_required")))
                .build();
        }
        var chatId = UuidParams.required(chatIdStr, "chat_id");
        var messageId = UuidParams.required(messageIdStr, "message_id");
        var userId = CurrentUserId.uuid(securityContext);
        var off = offset != null ? Math.max(0, offset) : 0;
        var lim = limit != null ? Math.min(500, Math.max(1, limit)) : 100;
        return readReceiptService.listForMessage(chatId, userId, messageId, off, lim)
            .map(r -> Response.ok(r).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.read_receipt.not_found")))
                .build());
    }

    private Response mapReadReceiptResult(ReadReceiptService.MarkResult result) {
        return switch (result) {
            case OK -> Response.noContent().build();
            case NOT_MEMBER -> Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.chat.not_a_member")))
                .build();
            case MESSAGE_NOT_FOUND -> Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.read_receipt.message_not_in_chat")))
                .build();
            case BATCH_TOO_LARGE -> Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.read_receipt.batch_too_large")))
                .build();
        };
    }
}
