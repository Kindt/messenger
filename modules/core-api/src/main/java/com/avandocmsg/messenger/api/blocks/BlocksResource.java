package com.avandocmsg.messenger.api.blocks;

import com.avandocmsg.messenger.api.blocks.dto.BlockUserRequest;
import com.avandocmsg.messenger.api.blocks.dto.BlockedUserResponse;
import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.core.port.ContactRepositoryPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.domain.BlockedUser;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;
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


@Path("/v1/blocks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Blocks", description = "Полная блокировка пользователей (раздел 10 ТЗ)")
public class BlocksResource {

    private final BlockRepositoryPort blockRepositoryPort;
    private final UserLookupPort userLookupPort;
    private final ContactRepositoryPort contactRepositoryPort;
    private final AvatarApplicationService avatarApplicationService;
    private final UserMessageSource messages;

    @Inject
    public BlocksResource(BlockRepositoryPort blockRepositoryPort,
                          UserLookupPort userLookupPort,
                          ContactRepositoryPort contactRepositoryPort,
                          AvatarApplicationService avatarApplicationService,
                          UserMessageSource messages) {
        this.blockRepositoryPort = blockRepositoryPort;
        this.userLookupPort = userLookupPort;
        this.contactRepositoryPort = contactRepositoryPort;
        this.avatarApplicationService = avatarApplicationService;
        this.messages = messages;
    }

    @GET
    @Operation(summary = "Список заблокированных текущим пользователем")
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var viewerId = UserId.of(userId);
        var rows = blockRepositoryPort.listBlockedUsers(viewerId).stream()
            .map(b -> toResponse(b, viewerId))
            .toList();
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
        if (userLookupPort.findById(targetId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.blocks.user_not_found")))
                .build();
        }
        var blocker = UserId.of(blockerId);
        var target = UserId.of(targetId);
        if (blockRepositoryPort.exists(blocker, target)) {
            return Response.noContent().build();
        }
        if (!blockRepositoryPort.block(blocker, target)) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.blocks.block_failed")))
                .build();
        }
        contactRepositoryPort.remove(UserId.of(blockerId), UserId.of(targetId));
        contactRepositoryPort.remove(UserId.of(targetId), UserId.of(blockerId));
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("/{userId}")
    @Operation(summary = "Разблокировать пользователя")
    public Response unblock(@PathParam("userId") String userIdStr,
                            @Context SecurityContext securityContext) {
        var targetId = UuidParams.required(userIdStr, "user_id");
        var blockerId = CurrentUserId.uuid(securityContext);
        if (!blockRepositoryPort.unblock(UserId.of(blockerId), UserId.of(targetId))) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.blocks.not_found")))
                .build();
        }
        return Response.noContent().build();
    }

    private BlockedUserResponse toResponse(BlockedUser blockedUser, UserId viewerId) {
        var base = new BlockedUserResponse(
            blockedUser.userId().value().toString(),
            blockedUser.username(),
            blockedUser.displayName(),
            blockedUser.blockedAt());
        if (avatarApplicationService == null) {
            return base;
        }
        var avatarFileId = userLookupPort.findById(blockedUser.userId().value())
            .map(u -> u.avatarFileId())
            .filter(id -> id != null && !id.isBlank())
            .map(id -> FileId.of(java.util.UUID.fromString(id)))
            .orElse(null);
        return avatarApplicationService.enrichBlockedUserResponse(base, viewerId, avatarFileId);
    }
}
