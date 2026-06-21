package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.phase5.dto.CreateKanbanTaskRequest;
import com.avandocmsg.messenger.api.phase5.dto.KanbanTaskResponse;
import com.avandocmsg.messenger.api.phase5.dto.WhiteboardResponse;
import com.avandocmsg.messenger.api.phase5.dto.WhiteboardSaveRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/chats/{chatId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Collaboration ADR", description = "Whiteboard and kanban scaffolds (T02313/14)")
public class ChatCollaborationAdrResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public ChatCollaborationAdrResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GET
    @Path("whiteboard")
    @Operation(summary = "Get chat whiteboard snapshot")
    public Response getWhiteboard(@PathParam("chatId") String chatId, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        return service.getWhiteboard(cid, userId)
            .map(row -> Response.ok(WhiteboardResponse.from(row)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.phase5.not_found")))
                .build());
    }

    @PUT
    @Path("whiteboard")
    @Operation(summary = "Save whiteboard snapshot JSON")
    public Response saveWhiteboard(@PathParam("chatId") String chatId,
                                   WhiteboardSaveRequest request,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var title = request != null ? request.title() : null;
        var snapshot = request != null ? request.snapshotJson() : "{}";
        return service.saveWhiteboard(cid, userId, title, snapshot)
            .map(row -> Response.ok(WhiteboardResponse.from(row)).build())
            .orElse(forbidden());
    }

    @GET
    @Path("kanban/tasks")
    @Operation(summary = "List kanban tasks")
    public Response listTasks(@PathParam("chatId") String chatId, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var rows = service.listKanbanTasks(cid, userId).stream().map(KanbanTaskResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("kanban/tasks")
    @Operation(summary = "Create kanban task")
    public Response createTask(@PathParam("chatId") String chatId,
                               CreateKanbanTaskRequest request,
                               @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var column = request != null ? request.columnKey() : "todo";
        var title = request != null ? request.title() : null;
        return service.createKanbanTask(cid, userId, column, title)
            .map(id -> Response.status(Response.Status.CREATED)
                .entity(KanbanTaskResponse.created(id.toString(), column, title)).build())
            .orElse(forbidden());
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.phase5.forbidden")))
            .build();
    }
}
