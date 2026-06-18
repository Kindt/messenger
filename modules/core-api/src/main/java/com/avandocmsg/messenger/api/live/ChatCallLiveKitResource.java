package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.UUID;

@Path("/v1/chats/{chatId}/calls/livekit")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Group call SFU", description = "LiveKit SFU for group calls in chat (spec 019 US5)")
public class ChatCallLiveKitResource {

    private final ChatCallLiveKitService chatCallLiveKitService;
    private final UserMessageSource messages;

    @Inject
    public ChatCallLiveKitResource(ChatCallLiveKitService chatCallLiveKitService, UserMessageSource messages) {
        this.chatCallLiveKitService = chatCallLiveKitService;
        this.messages = messages;
    }

    @POST
    @Path("join")
    @Operation(summary = "Join group call via LiveKit SFU")
    public Response join(@PathParam("chatId") String chatIdStr, @Context SecurityContext securityContext) {
        if (!chatCallLiveKitService.groupCallSfuEnabled()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.live.not_configured")))
                .build();
        }
        UUID chatId;
        try {
            chatId = UuidParams.required(chatIdStr, "chat_id");
        } catch (Exception e) {
            return Response.status(400).entity(new ApiError(400, messages.get("error.invalid_parameter"))).build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        return chatCallLiveKitService.join(chatId, userId)
            .map(body -> Response.ok(body).build())
            .orElseGet(() -> Response.status(404)
                .entity(new ApiError(404, messages.get("error.chat.not_a_member")))
                .build());
    }
}
