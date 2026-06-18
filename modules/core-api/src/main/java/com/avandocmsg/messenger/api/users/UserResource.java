package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.users.dto.SavedChatResponse;
import com.avandocmsg.messenger.api.users.dto.UpdateLocaleRequest;
import com.avandocmsg.messenger.api.users.dto.UpdatePresenceRequest;
import com.avandocmsg.messenger.api.users.dto.UpdatePrivacyRequest;
import com.avandocmsg.messenger.api.users.dto.UpdateProfileRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.application.UserDomainMapper;
import com.avandocmsg.messenger.core.domain.UserId;
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
    private static final Set<String> UI_LOCALES_ALLOWED = Set.of("ru", "en", "be", "kk", "zh", "ko");

    private final UserApplicationService userApplicationService;
    private final UserMessageSource messages;

    @Inject
    public UserResource(UserApplicationService userApplicationService, UserMessageSource messages) {
        this.userApplicationService = userApplicationService;
        this.messages = messages;
    }

    @GET
    @Path("/me/saved-chat")
    @Operation(summary = "Id чата «Хранилище» (раздел 30 ТЗ)", description = "Появляется после первого логина/регистрации")
    public Response savedChat(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        return userApplicationService.getSavedChatId(UserId.of(userId))
            .map(id -> Response.ok(new SavedChatResponse(id.value().toString())).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.saved_chat_not_found")))
                .build());
    }

    @GET
    @Path("/me")
    @Operation(summary = "Текущий профиль")
    public Response me(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userApplicationService
            .getProfileForViewer(UserId.of(userId), UserId.of(userId))
            .map(UserDomainMapper::toResponse)
            .orElse(null);
        if (profile == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.not_found")))
                .build();
        }
        return Response.ok(profile).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id, @Context SecurityContext securityContext) {
        var viewerId = CurrentUserId.uuid(securityContext);
        var targetId = UuidParams.required(id, "user_id");
        var profile = userApplicationService
            .getProfileForViewer(UserId.of(viewerId), UserId.of(targetId))
            .map(UserDomainMapper::toResponse)
            .orElse(null);
        if (profile == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user.not_found")))
                .build();
        }
        return Response.ok(profile).build();
    }

    @PATCH
    @Path("/me")
    @Operation(summary = "Обновить профиль")
    public Response updateProfile(UpdateProfileRequest request,
                                   @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userApplicationService
            .updateProfile(UserId.of(userId), request.displayName(), request.phone())
            .map(UserDomainMapper::toResponse)
            .orElse(null);
        if (profile != null) {
            return Response.ok(profile).build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ApiError(500, messages.get("error.user.profile_update_failed")))
            .build();
    }

    @PATCH
    @Path("/me/presence")
    @Operation(summary = "Установить статус присутствия",
        description = "presence_status: online | away | dnd | offline; optional custom_status_text and dnd_until")
    public Response updatePresence(UpdatePresenceRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.presence_invalid")))
                .build();
        }
        if (request.presenceStatus() != null
            && !PRESENCE_ALLOWED.contains(request.presenceStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.presence_invalid")))
                .build();
        }
        if (request.presenceStatus() == null
            && request.customStatusText() == null
            && request.dndUntil() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.presence_invalid")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        return userApplicationService
            .updateUserStatus(UserId.of(userId), request)
            .map(UserDomainMapper::toResponse)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.user.presence_failed")))
                .build());
    }

    @PATCH
    @Path("/me/privacy")
    @Operation(summary = "Privacy settings", description = "Toggle read receipt visibility")
    public Response updatePrivacy(UpdatePrivacyRequest request,
                                  @Context SecurityContext securityContext) {
        if (request == null || request.privacyDisableReadReceipts() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.privacy_invalid")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        return userApplicationService
            .updatePrivacy(UserId.of(userId), request.privacyDisableReadReceipts())
            .map(UserDomainMapper::toResponse)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.user.privacy_update_failed")))
                .build());
    }

    @PATCH
    @Path("/me/locale")
    @Operation(summary = "UI locale", description = "Web client language preference (ru, en, be, kk, zh, ko)")
    public Response updateLocale(UpdateLocaleRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null || request.uiLocale() == null
            || !UI_LOCALES_ALLOWED.contains(request.uiLocale())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.user.locale_invalid")))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        return userApplicationService
            .updateUiLocale(UserId.of(userId), request.uiLocale())
            .map(UserDomainMapper::toResponse)
            .map(p -> Response.ok(p).build())
            .orElse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(500, messages.get("error.user.locale_update_failed")))
                .build());
    }

    @POST
    @Path("/me/heartbeat")
    @Operation(summary = "Heartbeat", description = "Обновляет last_seen_at; вызывайте периодически при активном клиенте")
    public Response heartbeat(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        userApplicationService.touchHeartbeat(UserId.of(userId));
        return Response.noContent().build();
    }
}
