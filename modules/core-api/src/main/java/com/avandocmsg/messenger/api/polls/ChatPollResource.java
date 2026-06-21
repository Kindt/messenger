package com.avandocmsg.messenger.api.polls;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.polls.dto.CreatePollRequest;
import com.avandocmsg.messenger.api.polls.dto.VotePollRequest;
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

@Path("/v1/chats/{chatId}/polls")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Polls", description = "In-chat polls")
public class ChatPollResource {

    private final ChatPollService chatPollService;
    private final UserMessageSource messages;

    @Inject
    public ChatPollResource(ChatPollService chatPollService, UserMessageSource messages) {
        this.chatPollService = chatPollService;
        this.messages = messages;
    }

    @POST
    @Operation(summary = "Create poll in chat")
    public Response create(@PathParam("chatId") String chatId,
                           CreatePollRequest request,
                           @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var created = chatPollService.create(cid, userId, request);
        return created.map(p -> Response.status(Response.Status.CREATED).entity(p).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.poll.not_member_or_invalid")))
                .build());
    }

    @GET
    @Operation(summary = "List polls in chat")
    public Response list(@PathParam("chatId") String chatId,
                         @QueryParam("limit") @DefaultValue("50") int limit,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        return Response.ok(chatPollService.listForChat(cid, userId, limit)).build();
    }

    @GET
    @Path("{pollId}")
    @Operation(summary = "Get poll with results")
    public Response get(@PathParam("chatId") String chatId,
                        @PathParam("pollId") String pollId,
                        @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var pid = UuidParams.required(pollId, "poll_id");
        return chatPollService.get(cid, pid, userId)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.poll.not_found")))
                .build());
    }

    @POST
    @Path("{pollId}/vote")
    @Operation(summary = "Vote on poll")
    public Response vote(@PathParam("chatId") String chatId,
                         @PathParam("pollId") String pollId,
                         VotePollRequest request,
                         @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var pid = UuidParams.required(pollId, "poll_id");
        var indexes = request != null ? request.optionIndexes() : null;
        var result = chatPollService.vote(cid, pid, userId, indexes);
        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.poll.not_found")))
                .build();
        }
        if (!result.get()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.poll.cannot_vote")))
                .build();
        }
        return chatPollService.get(cid, pid, userId)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.poll.not_found")))
                .build());
    }

    @POST
    @Path("{pollId}/close")
    @Operation(summary = "Close poll (creator only)")
    public Response close(@PathParam("chatId") String chatId,
                          @PathParam("pollId") String pollId,
                          @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var pid = UuidParams.required(pollId, "poll_id");
        return chatPollService.close(cid, pid, userId)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.FORBIDDEN)
                .entity(new ApiError(403, messages.get("error.poll.cannot_close")))
                .build());
    }
}
