package com.avandocmsg.messenger.api.chats.bans;

import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanRequest;
import com.avandocmsg.messenger.api.chats.bans.dto.ChatBanResponse;
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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/chats/{chatId}/bans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Chat Bans", description = "Chat ban management (admin/owner only)")
public class ChatBanResource {

    private static final String PARAM_CHAT_ID = "chat_id";

    private final ChatBanService chatBanService;
    private final UserMessageSource messages;

    @Inject
    public ChatBanResource(ChatBanService chatBanService, UserMessageSource messages) {
        this.chatBanService = chatBanService;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Ban user", description = "Ban a user from a chat (owner/admin only)")
    @ApiResponse(responseCode = "201", description = "User banned",
        content = @Content(schema = @Schema(implementation = ChatBanResponse.class)))
    @ApiResponse(responseCode = "403", description = "Not authorized",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response ban(@PathParam("chatId") String chatIdStr,
                        ChatBanRequest request,
                        @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.ban.body_required")))
                .build();
        }
        var actorId = CurrentUserId.uuid(securityContext);
        var chatId = UuidParams.required(chatIdStr, PARAM_CHAT_ID);
        var targetUserId = UuidParams.required(request.userId(), "user_id");
        var ban = chatBanService.banUser(chatId, actorId, targetUserId, request.reason());
        if (ban == null) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.ban.cannot_ban")))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(ban).build();
    }

    @DELETE
    @Path("/{userId}")
    @Operation(summary = "Unban user", description = "Remove a ban from a user (owner/admin only)")
    @ApiResponse(responseCode = "204", description = "User unbanned")
    @ApiResponse(responseCode = "403", description = "Not authorized",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response unban(@PathParam("chatId") String chatIdStr,
                          @PathParam("userId") String userIdStr,
                          @Context SecurityContext securityContext) {
        var actorId = CurrentUserId.uuid(securityContext);
        var ok = chatBanService.unbanUser(
            UuidParams.required(chatIdStr, PARAM_CHAT_ID),
            actorId,
            UuidParams.required(userIdStr, "user_id"));
        if (!ok) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.ban.cannot_unban")))
                .build();
        }
        return Response.noContent().build();
    }

    @GET
    @Operation(summary = "List bans", description = "Get all banned users in a chat (owner/admin only)")
    @ApiResponse(responseCode = "200", description = "List of bans",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatBanResponse.class))))
    @ApiResponse(responseCode = "403", description = "Not authorized",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response listBans(@PathParam("chatId") String chatIdStr,
                           @Context SecurityContext securityContext) {
        var chatId = UuidParams.required(chatIdStr, PARAM_CHAT_ID);
        var viewerId = CurrentUserId.uuid(securityContext);
        return chatBanService.listBansForViewer(chatId, viewerId)
            .map(bans -> Response.ok(bans).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.ban.cannot_list")))
                .build());
    }
}
