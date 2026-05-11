package com.avandocmsg.messenger.api.blocks;

import com.avandocmsg.messenger.api.blocks.dto.BlockUserRequest;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.repository.BlockRepository;
import com.avandocmsg.messenger.api.repository.ContactRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/v1/blocks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Blocks", description = "Полная блокировка пользователей (раздел 10 ТЗ)")
public class BlocksResource {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final UserMessageSource messages;

    @Inject
    public BlocksResource(BlockRepository blockRepository,
                          UserRepository userRepository,
                          ContactRepository contactRepository,
                          UserMessageSource messages) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "Список заблокированных текущим пользователем")
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var rows = blockRepository.listBlockedUsers(userId);
        return Response.ok(rows).build();
    }

    @POST
    @Operation(summary = "Заблокировать пользователя",
        description = "Добавляет запись в blocks; контакты в обе стороны удаляются. Повторный вызов — без ошибки (идемпотентно).")
    public Response block(BlockUserRequest request,
                          @Context SecurityContext securityContext) {
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.blocks.user_id_required")))
                .build();
        }
        var targetId = UuidParams.required(request.userId(), "user_id");
        var blockerId = CurrentUserId.uuid(securityContext);
        if (targetId.equals(blockerId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.blocks.cannot_block_self")))
                .build();
        }
        if (userRepository.findById(targetId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.blocks.user_not_found")))
                .build();
        }
        if (blockRepository.exists(blockerId, targetId)) {
            return Response.noContent().build();
        }
        if (!blockRepository.block(blockerId, targetId)) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.blocks.block_failed")))
                .build();
        }
        contactRepository.remove(blockerId, targetId);
        contactRepository.remove(targetId, blockerId);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{userId}")
    @Operation(summary = "Разблокировать пользователя")
    public Response unblock(@PathParam("userId") String userIdStr,
                            @Context SecurityContext securityContext) {
        var targetId = UuidParams.required(userIdStr, "user_id");
        var blockerId = CurrentUserId.uuid(securityContext);
        if (!blockRepository.unblock(blockerId, targetId)) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.blocks.not_found")))
                .build();
        }
        return Response.noContent().build();
    }
}
