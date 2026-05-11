package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.dto.SavedChatResponse;
import com.avandocmsg.messenger.api.users.dto.UpdatePresenceRequest;
import com.avandocmsg.messenger.api.users.dto.UpdateProfileRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
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

import java.util.Set;

@Path("/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "Профиль и присутствие")
public class UserResource {

    private static final Set<String> PRESENCE_ALLOWED = Set.of("online", "away", "dnd", "offline");

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final UserMessageSource messages;

    @Inject
    public UserResource(UserRepository userRepository, ChatRepository chatRepository, UserMessageSource messages) {
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.messages = messages;
    }

    @GET
    @Path("/me/saved-chat")
    @Operation(summary = "Id чата «Хранилище» (раздел 30 ТЗ)", description = "Появляется после первого логина/регистрации")
    public Response savedChat(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return chatRepository.findSavedVaultChatId(userId)
            .map(id -> Response.ok(new SavedChatResponse(id.toString())).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.saved_chat_not_found")))
                .build());
    }

    @GET
    @Path("/me")
    @Operation(summary = "Текущий профиль")
    public Response me(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userRepository.findById(userId);
        return profile.map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.not_found")))
                .build());
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id) {
        var profile = userRepository.findById(UuidParams.required(id, "user_id"));
        return profile.map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.not_found")))
                .build());
    }

    @PATCH
    @Path("/me")
    @Operation(summary = "Обновить профиль")
    public Response updateProfile(UpdateProfileRequest request,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var updated = userRepository.updateProfile(userId, request.displayName(), request.phone());
        if (updated) {
            var profile = userRepository.findById(userId);
            return profile.map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ApiError(500, messages.get("error.user.profile_update_failed")))
            .build();
    }

    @PATCH
    @Path("/me/presence")
    @Operation(summary = "Установить статус присутствия",
        description = "presence_status: online | away | dnd | offline")
    public Response updatePresence(UpdatePresenceRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null || request.presenceStatus() == null
            || !PRESENCE_ALLOWED.contains(request.presenceStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.presence_invalid")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        if (!userRepository.updatePresence(userId, request.presenceStatus())) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.user.presence_failed")))
                .build();
        }
        return userRepository.findById(userId)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/me/heartbeat")
    @Operation(summary = "Heartbeat", description = "Обновляет last_seen_at; вызывайте периодически при активном клиенте")
    public Response heartbeat(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        userRepository.touchHeartbeat(userId);
        return Response.noContent().build();
    }
}
