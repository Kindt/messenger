package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.live.dto.CreateLiveSessionRequest;
import com.avandocmsg.messenger.api.live.dto.LiveSessionModerationRequest;
import com.avandocmsg.messenger.api.live.dto.PatchLiveSessionDvrRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/chats/{chatId}/live-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Live streaming", description = "Прямые эфиры в чате (WebRTC SFU, spec 013 L2)")
public class ChatLiveSessionResource {

    private final LiveSessionService liveSessionService;
    private final UserMessageSource messages;

    @Inject
    public ChatLiveSessionResource(LiveSessionService liveSessionService, UserMessageSource messages) {
        this.liveSessionService = liveSessionService;
        this.messages = messages;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Начать эфир в чате")
    public Response create(@PathParam("chatId") String chatId,
                           CreateLiveSessionRequest request,
                           @Context SecurityContext securityContext) {
        if (!liveSessionService.liveStreamingConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.live.not_configured")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var created = liveSessionService.create(cid, userId, request);
        return created.map(c -> Response.status(Response.Status.CREATED).entity(c).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.live.cannot_create")))
                .build());
    }

    @GET
    @Operation(summary = "Список эфиров чата")
    public Response list(@PathParam("chatId") String chatId,
                         @QueryParam("active_only") @DefaultValue("true") boolean activeOnly,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        return Response.ok(liveSessionService.listForChat(cid, userId, activeOnly)).build();
    }

    @POST
    @Path("{sessionId}/moderation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Модерация эфира (stop/slow_mode/kick/ban/report)")
    public Response moderate(@PathParam("chatId") String chatId,
                             @PathParam("sessionId") String sessionId,
                             LiveSessionModerationRequest request,
                             @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "live_session_id");
        var action = request != null ? request.action() : null;
        var reason = request != null ? request.reason() : null;
        return liveSessionService.recordModeration(cid, sid, userId, action, reason)
            .map(s -> Response.ok(s).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.live.cannot_moderate")))
                .build());
    }

    @PATCH
    @Path("{sessionId}/dvr")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Обновить HLS/DVR playlist URL (только ведущий)")
    public Response patchDvr(@PathParam("chatId") String chatId,
                             @PathParam("sessionId") String sessionId,
                             PatchLiveSessionDvrRequest request,
                             @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "live_session_id");
        var url = request != null ? request.playlistUrl() : null;
        return liveSessionService.updateDvrPlaylist(cid, sid, userId, url)
            .map(s -> Response.ok(s).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.live.cannot_update_dvr")))
                .build());
    }

    @POST
    @Path("{sessionId}/ingress")
    @Operation(summary = "RTMP ingress credentials (только ведущий)")
    public Response ingress(@PathParam("chatId") String chatId,
                            @PathParam("sessionId") String sessionId,
                            @Context SecurityContext securityContext) {
        if (!liveSessionService.liveStreamingConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.live.not_configured")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "live_session_id");
        return liveSessionService.ingressCredentials(cid, sid, userId)
            .map(c -> Response.ok(c).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.live.cannot_ingress")))
                .build());
    }
}
