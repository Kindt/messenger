package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.phase5.dto.BreakoutRoomResponse;
import com.avandocmsg.messenger.api.phase5.dto.CaptionSessionResponse;
import com.avandocmsg.messenger.api.phase5.dto.CreateBreakoutRequest;
import com.avandocmsg.messenger.api.phase5.dto.CreateGuestLinkRequest;
import com.avandocmsg.messenger.api.phase5.dto.GuestLinkResponse;
import com.avandocmsg.messenger.api.phase5.dto.RecordingResponse;
import com.avandocmsg.messenger.api.phase5.dto.StartCaptionsRequest;
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

@Path("/v1/chats/{chatId}/conferences/{conferenceId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Conference ADR", description = "Recording, guest links, breakout, captions (T02307/09/10/17)")
public class ChatConferenceAdrResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public ChatConferenceAdrResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @POST
    @Path("recordings")
    @Operation(summary = "Start call recording scaffold")
    public Response startRecording(@PathParam("chatId") String chatId,
                                   @PathParam("conferenceId") String conferenceId,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        return service.startRecording(cid, confId, userId)
            .map(id -> Response.status(Response.Status.CREATED)
                .entity(RecordingResponse.started(id.toString())).build())
            .orElse(forbidden());
    }

    @GET
    @Path("recordings")
    @Operation(summary = "List call recordings")
    public Response listRecordings(@PathParam("chatId") String chatId,
                                   @PathParam("conferenceId") String conferenceId,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        var rows = service.listRecordings(cid, confId, userId).stream().map(RecordingResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("guest-links")
    @Operation(summary = "Create guest link with optional waiting room")
    public Response createGuestLink(@PathParam("chatId") String chatId,
                                    @PathParam("conferenceId") String conferenceId,
                                    CreateGuestLinkRequest request,
                                    @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        var waiting = request == null || request.waitingRoom() == null || request.waitingRoom();
        return service.createGuestLink(cid, confId, userId, waiting)
            .map(row -> Response.status(Response.Status.CREATED).entity(GuestLinkResponse.from(row)).build())
            .orElse(forbidden());
    }

    @POST
    @Path("breakout-rooms")
    @Operation(summary = "Create breakout room scaffold")
    public Response createBreakout(@PathParam("chatId") String chatId,
                                   @PathParam("conferenceId") String conferenceId,
                                   CreateBreakoutRequest request,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        var name = request != null && request.name() != null ? request.name() : "Breakout";
        return service.createBreakout(cid, confId, userId, name)
            .map(id -> Response.status(Response.Status.CREATED)
                .entity(BreakoutRoomResponse.created(id.toString(), name)).build())
            .orElse(forbidden());
    }

    @GET
    @Path("breakout-rooms")
    @Operation(summary = "List breakout rooms")
    public Response listBreakouts(@PathParam("chatId") String chatId,
                                  @PathParam("conferenceId") String conferenceId,
                                  @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        var rows = service.listBreakouts(cid, confId, userId).stream().map(BreakoutRoomResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("captions")
    @Operation(summary = "Start live captions session (STT mock)")
    public Response startCaptions(@PathParam("chatId") String chatId,
                                  @PathParam("conferenceId") String conferenceId,
                                  StartCaptionsRequest request,
                                  @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        var lang = request != null ? request.language() : "ru";
        var sample = request != null ? request.sampleText() : null;
        return service.startCaptions(cid, confId, userId, lang, sample)
            .map(row -> Response.status(Response.Status.CREATED).entity(CaptionSessionResponse.from(row)).build())
            .orElse(forbidden());
    }

    @GET
    @Path("captions")
    @Operation(summary = "Get latest captions session")
    public Response getCaptions(@PathParam("chatId") String chatId,
                                @PathParam("conferenceId") String conferenceId,
                                @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var confId = UuidParams.required(conferenceId, "conference_id");
        return service.getCaptions(cid, confId, userId)
            .map(row -> Response.ok(CaptionSessionResponse.from(row)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.phase5.not_found")))
                .build());
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.phase5.forbidden")))
            .build();
    }
}
