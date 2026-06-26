package com.avandocmsg.messenger.api.meshcall;

import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.CompleteMeshCallRecordingRequest;
import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.StartMeshCallRecordingRequest;
import com.avandocmsg.messenger.api.meshcall.dto.MeshCallDtos.StartMeshCallSessionRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
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

@Path("/v1/chats/{chatId}/mesh-calls")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Mesh calls", description = "Mesh WebRTC call sessions and recordings")
public class ChatMeshCallResource {

    private final MeshCallRecordingService service;
    private final UserMessageSource messages;

    @Inject
    public ChatMeshCallResource(MeshCallRecordingService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @POST
    @Path("sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start mesh call session (creates audit recording stub)")
    public Response startSession(
        @PathParam("chatId") String chatId,
        StartMeshCallSessionRequest body,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var mode = body != null ? body.mediaMode() : "audio";
        return service.startSession(cid, userId, mode)
            .map(resp -> Response.status(Response.Status.CREATED).entity(resp).build())
            .orElse(forbidden());
    }

    @POST
    @Path("sessions/{sessionId}/join")
    @Operation(summary = "Join mesh call session (participant audit recording)")
    public Response joinSession(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        return service.joinSession(cid, userId, sid)
            .map(resp -> Response.ok(resp).build())
            .orElse(forbidden());
    }

    @POST
    @Path("sessions/{sessionId}/end")
    @Operation(summary = "End mesh call session")
    public Response endSession(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        return service.endSession(cid, userId, sid)
            .map(ok -> Response.noContent().build())
            .orElse(forbidden());
    }

    @POST
    @Path("sessions/{sessionId}/recordings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start user clip recording")
    public Response startRecording(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        StartMeshCallRecordingRequest body,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var kind = body != null ? body.kind() : "user";
        if (!"user".equals(kind)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "invalid_kind"))
                .build();
        }
        return service.startUserRecording(cid, userId, sid)
            .map(resp -> Response.status(Response.Status.CREATED).entity(resp).build())
            .orElse(forbidden());
    }

    @POST
    @Path("sessions/{sessionId}/recordings/{recordingId}/stop")
    @Operation(summary = "Stop server-side user clip (LiveKit composite egress)")
    public Response stopUserRecording(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @PathParam("recordingId") String recordingId,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var rid = UuidParams.required(recordingId, "recording_id");
        return service.stopUserRecording(cid, userId, sid, rid)
            .map(ok -> Response.noContent().build())
            .orElse(forbidden());
    }

    @POST
    @Path("sessions/{sessionId}/recordings/{recordingId}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Complete mesh call recording with uploaded file")
    public Response completeRecording(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @PathParam("recordingId") String recordingId,
        CompleteMeshCallRecordingRequest body,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var rid = UuidParams.required(recordingId, "recording_id");
        if (body == null || body.fileId() == null || body.fileId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, "file_id_required"))
                .build();
        }
        var fileId = UuidParams.required(body.fileId(), "file_id");
        var duration = body.durationMs() != null ? body.durationMs() : 0L;
        return service.completeRecording(cid, userId, sid, rid, fileId, duration)
            .map(ok -> Response.noContent().build())
            .orElse(forbidden());
    }

    @GET
    @Path("sessions/{sessionId}/recordings")
    @Operation(summary = "List mesh call recordings (user clips; audit for chat admins)")
    public Response listRecordings(
        @PathParam("chatId") String chatId,
        @PathParam("sessionId") String sessionId,
        @Context SecurityContext securityContext
    ) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var sid = UuidParams.required(sessionId, "session_id");
        var rows = service.listRecordings(cid, userId, sid);
        return Response.ok(rows).build();
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.phase5.forbidden")))
            .build();
    }
}
