package com.avandocmsg.messenger.api.phase5;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.phase5.dto.CreateKanbanTaskRequest;
import com.avandocmsg.messenger.api.phase5.dto.KanbanTaskResponse;
import com.avandocmsg.messenger.api.phase5.dto.UpdateKanbanTaskRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/v1/chats/{chatId}/kanban")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Collaboration ADR", description = "Kanban scaffold (T02314)")
public class ChatKanbanAdrResource {

    private final Phase5AdrService service;
    private final UserMessageSource messages;

    @Inject
    public ChatKanbanAdrResource(Phase5AdrService service, UserMessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GET
    @Path("tasks")
    @Operation(summary = "List kanban tasks")
    public Response listTasks(@PathParam("chatId") String chatId, @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var rows = service.listKanbanTasks(cid, userId).stream().map(KanbanTaskResponse::from).toList();
        return Response.ok(rows).build();
    }

    @POST
    @Path("tasks")
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

    @PATCH
    @Path("tasks/{taskId}")
    @Operation(summary = "Move or update kanban task")
    public Response updateTask(@PathParam("chatId") String chatId,
                               @PathParam("taskId") String taskId,
                               UpdateKanbanTaskRequest request,
                               @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var tid = UuidParams.required(taskId, "task_id");
        var column = request != null ? request.columnKey() : null;
        var sort = request != null ? request.sortOrder() : null;
        var title = request != null ? request.title() : null;
        return service.updateKanbanTask(cid, userId, tid, column, sort, title)
            .map(row -> Response.ok(KanbanTaskResponse.from(row)).build())
            .orElse(forbidden());
    }

    @DELETE
    @Path("tasks/{taskId}")
    @Operation(summary = "Delete kanban task")
    public Response deleteTask(@PathParam("chatId") String chatId,
                               @PathParam("taskId") String taskId,
                               @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var cid = UuidParams.required(chatId, "chat_id");
        var tid = UuidParams.required(taskId, "task_id");
        return service.deleteKanbanTask(cid, userId, tid)
            .map(v -> Response.noContent().build())
            .orElse(forbidden());
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.phase5.forbidden")))
            .build();
    }
}
