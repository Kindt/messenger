package com.avandocmsg.messenger.api.conference;

import com.avandocmsg.messenger.api.conference.dto.ConferenceResponse;
import com.avandocmsg.messenger.api.conference.dto.CreateConferenceRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * In-chat conference endpoints use a dedicated class-level path (like {@link com.avandocmsg.messenger.api.export.ExportResource})
 * so Jersey does not lose them to {@link com.avandocmsg.messenger.api.chats.ChatResource} /v1/chats/{chatId}.
 */
@Path("/v1/chats/{chatId}/conferences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Conferences", description = "Групповые видеоконференции (комната Jitsi / WebRTC у клиента)")
public class ChatConferenceResource {

    private final ConferenceService conferenceService;
    private final UserMessageSource messages;

    @Inject
    public ChatConferenceResource(ConferenceService conferenceService, UserMessageSource messages) {
        this.conferenceService = conferenceService;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Создать конференцию в чате")
    public Response create(@PathParam("chatId") String chatId,
                           CreateConferenceRequest request,
                           @Context SecurityContext securityContext) {
        if (request == null) {
            request = new CreateConferenceRequest(null, null);
        }
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var created = conferenceService.create(cid, userId, request);
        return created.map(c -> Response.status(Response.Status.CREATED).entity(c).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.conference.not_member_or_chat")))
                .build());
    }

    @GET
    @Operation(summary = "Список конференций чата")
    public Response list(@PathParam("chatId") String chatId,
                         @QueryParam("active_only") @DefaultValue("true") boolean activeOnly,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var list = conferenceService.listForChat(cid, userId, activeOnly);
        return Response.ok(list).build();
    }
}
