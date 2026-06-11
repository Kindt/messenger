package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.ConferenceParticipantResponse;
import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

import java.util.UUID;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Conferences", description = "Групповые видеоконференции (комната Jitsi / WebRTC у клиента)")
public class ConferenceResource {

    private final ConferenceService conferenceService;
    private final UserMessageSource messages;

    @Inject
    public ConferenceResource(ConferenceService conferenceService, UserMessageSource messages) {
        this.conferenceService = conferenceService;
        this.messages = messages;
    }

    @POST
    @Path("conferences")
    @Operation(summary = "Создать встречу",
        description = "Создаёт группу-встречу и конференцию Jitsi с join_url для приглашения участников")
    public Response createStandalone(CreateConferenceRequest request,
                                     @Context SecurityContext securityContext) {
        if (request == null) {
            request = new CreateConferenceRequest(null, null);
        }
        var userId = CurrentUserId.uuid(securityContext);
        var created = conferenceService.createStandalone(userId, request);
        return created.map(c -> Response.status(Response.Status.CREATED).entity(c).build())
            .orElse(Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.conference.cannot_create")))
                .build());
    }

    @GET
    @Path("conferences/by-room/{roomSlug}")
    @Operation(summary = "Найти активную конференцию по имени комнаты Jitsi")
    public Response getByRoom(@PathParam("roomSlug") String roomSlug,
                              @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return conferenceService.getByRoomSlug(userId, roomSlug)
            .map(c -> Response.ok(c).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.conference.not_found")))
                .build());
    }

    @GET
    @Path("conferences/active")
    @Operation(summary = "Активные конференции", description = "По одной активной конференции на каждый чат, где пользователь — участник")
    public Response listActive(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return Response.ok(conferenceService.listActiveForUser(userId)).build();
    }

    @GET
    @Path("conferences/{conferenceId}")
    @Operation(summary = "Карточка конференции")
    public Response get(@PathParam("conferenceId") String conferenceId,
                        @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(conferenceId, "conference_id");
        return conferenceService.get(id, userId)
            .map(c -> Response.ok(c).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.conference.not_found")))
                .build());
    }

    @GET
    @Path("conferences/{conferenceId}/participants")
    @Operation(summary = "Участники конференции", description = "Пользователи с активной записью входа (left_at IS NULL)")
    @ApiResponse(responseCode = "200", description = "Participant list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConferenceParticipantResponse.class))))
    public Response listParticipants(@PathParam("conferenceId") String conferenceId,
                                     @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(conferenceId, "conference_id");
        var list = conferenceService.listParticipants(id, userId);
        return list.map(Response::ok)
            .map(b -> b.build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.conference.not_found")))
                .build());
    }

    @POST
    @Path("conferences/{conferenceId}/join")
    @Operation(summary = "Запись участника о входе в звонок")
    public Response join(@PathParam("conferenceId") String conferenceId,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(conferenceId, "conference_id");
        if (conferenceService.join(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiError(400, messages.get("error.conference.cannot_join")))
            .build();
    }

    @POST
    @Path("conferences/{conferenceId}/leave")
    @Operation(summary = "Отметить выход из звонка")
    public Response leave(@PathParam("conferenceId") String conferenceId,
                          @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(conferenceId, "conference_id");
        conferenceService.leave(id, userId);
        return Response.noContent().build();
    }

    @POST
    @Path("conferences/{conferenceId}/end")
    @Operation(summary = "Завершить конференцию (создатель или admin/owner чата)")
    public Response end(@PathParam("conferenceId") String conferenceId,
                        @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var id = UuidParams.required(conferenceId, "conference_id");
        if (conferenceService.end(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.conference.cannot_end")))
            .build();
    }
}
