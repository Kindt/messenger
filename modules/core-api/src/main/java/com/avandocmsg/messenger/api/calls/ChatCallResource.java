package com.avandocmsg.messenger.api.calls;

import com.avandocmsg.messenger.api.calls.dto.CallSignalRequest;
import com.avandocmsg.messenger.api.calls.dto.CreateCallRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.UUID;

@Path("/v1/chats/{chatId}/calls")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Calls", description = "Provider-neutral Korus call sessions")
public final class ChatCallResource {

    private final UnifiedCallService calls;

    @Inject
    public ChatCallResource(UnifiedCallService calls) {
        this.calls = calls;
    }

    @POST
    @Operation(summary = "Create and join a Korus call session")
    public Response create(
        @PathParam("chatId") String chatId,
        CreateCallRequest request,
        @Context SecurityContext securityContext
    ) {
        var cid = UuidParams.required(chatId, "chat_id");
        var userId = CurrentUserId.uuid(securityContext);
        var kind = request != null ? request.kind() : "group";
        var mediaIntent = request != null ? request.mediaIntent() : "audio";
        return calls.create(cid, userId, kind, mediaIntent)
            .map(body -> Response.status(Response.Status.CREATED).entity(body).build())
            .orElseGet(ChatCallResource::forbidden);
    }

    @POST
    @Path("{sessionId}/join")
    @Operation(summary = "Join an active Korus call session")
    public Response join(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var userId = CurrentUserId.uuid(securityContext);
        return calls.join(cid, sid, userId)
            .map(body -> Response.ok(body).build())
            .orElseGet(ChatCallResource::forbidden);
    }

    @POST
    @Path("{sessionId}/decline")
    @Operation(summary = "Decline a Korus call invitation without ending the room")
    public Response decline(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!calls.decline(cid, sid, userId)) {
            return forbidden();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("{sessionId}/end")
    @Operation(summary = "End a Korus call session")
    public Response end(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var userId = CurrentUserId.uuid(securityContext);
        if (!calls.end(cid, sid, userId)) {
            return forbidden();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("{sessionId}/participants/{participantId}/leave")
    @Operation(summary = "Leave a Korus call without ending the room")
    public Response leave(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @PathParam("participantId") String participantId,
        @Context SecurityContext securityContext
    ) {
        var ids = ids(chatId, sessionId, participantId);
        var userId = CurrentUserId.uuid(securityContext);
        if (!calls.leave(ids.chatId(), ids.sessionId(), userId, ids.participantId())) {
            return forbidden();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("{sessionId}/signals/{participantId}")
    @Operation(summary = "Submit SDP or ICE to the assigned Korus media node")
    public Response signal(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @PathParam("participantId") String participantId,
        CallSignalRequest request,
        @Context SecurityContext securityContext
    ) {
        var ids = ids(chatId, sessionId, participantId);
        var userId = CurrentUserId.uuid(securityContext);
        if (!calls.submitSignal(ids.chatId(), ids.sessionId(), userId, ids.participantId(), request)) {
            return forbidden();
        }
        return Response.accepted().build();
    }

    @GET
    @Path("{sessionId}/signals/{participantId}")
    @Operation(summary = "Poll signals emitted by the assigned Korus media node")
    public Response pollSignals(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @PathParam("participantId") String participantId,
        @Context SecurityContext securityContext
    ) {
        var ids = ids(chatId, sessionId, participantId);
        var userId = CurrentUserId.uuid(securityContext);
        return calls.pollSignals(ids.chatId(), ids.sessionId(), userId, ids.participantId(), 64)
            .map(body -> Response.ok(body).build())
            .orElseGet(ChatCallResource::forbidden);
    }

    private static CallIds ids(String chatId, String sessionId, String participantId) {
        return new CallIds(
            UuidParams.required(chatId, "chat_id"),
            UuidParams.required(sessionId, "session_id"),
            UuidParams.required(participantId, "participant_id")
        );
    }

    private static Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(Response.Status.FORBIDDEN.getStatusCode(), "call_forbidden"))
            .build();
    }

    private record CallIds(UUID chatId, UUID sessionId, UUID participantId) {}
}
