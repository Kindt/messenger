package com.avandocmsg.messenger.api.live;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/live-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Live streaming", description = "Прямые эфиры — join/end по id сессии")
public class LiveSessionResource {

    private final LiveSessionService liveSessionService;
    private final UserMessageSource messages;

    @Inject
    public LiveSessionResource(LiveSessionService liveSessionService, UserMessageSource messages) {
        this.liveSessionService = liveSessionService;
        this.messages = messages;
    }

    @GET
    @Path("{sessionId}")
    @Operation(summary = "Карточка эфира")
    public Response get(@PathParam("sessionId") String sessionId,
                        @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(sessionId, "live_session_id");
        return liveSessionService.get(id, userId)
            .map(c -> Response.ok(c).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.live.not_found")))
                .build());
    }

    @POST
    @Path("{sessionId}/join")
    @Operation(summary = "Присоединиться к эфиру (LiveKit token)")
    public Response join(@PathParam("sessionId") String sessionId,
                         @Context SecurityContext securityContext) {
        if (!liveSessionService.liveStreamingConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get("error.live.not_configured")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(sessionId, "live_session_id");
        return liveSessionService.join(id, userId)
            .map(j -> Response.ok(j).build())
            .orElse(Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.live.cannot_join")))
                .build());
    }

    @POST
    @Path("{sessionId}/leave")
    @Operation(summary = "Покинуть эфир")
    public Response leave(@PathParam("sessionId") String sessionId,
                          @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(sessionId, "live_session_id");
        liveSessionService.leave(id, userId);
        return Response.noContent().build();
    }

    @POST
    @Path("{sessionId}/end")
    @Operation(summary = "Завершить эфир (ведущий или admin чата)")
    public Response end(@PathParam("sessionId") String sessionId,
                        @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(sessionId, "live_session_id");
        if (liveSessionService.end(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.live.cannot_end")))
            .build();
    }
}
