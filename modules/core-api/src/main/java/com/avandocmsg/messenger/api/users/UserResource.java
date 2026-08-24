package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.security.TimingSensitivePaths;
import com.avandocmsg.messenger.api.users.dto.SavedChatResponse;
import com.avandocmsg.messenger.api.users.dto.UpdateLocaleRequest;
import com.avandocmsg.messenger.api.users.dto.UpdatePresenceRequest;
import com.avandocmsg.messenger.api.users.dto.UpdatePrivacyRequest;
import com.avandocmsg.messenger.api.users.dto.UpdateProfileRequest;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.application.AvatarApplicationService;
import com.avandocmsg.messenger.core.application.UserDomainMapper;
import com.avandocmsg.messenger.core.domain.FileId;
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
    private static final String ERR_PROFILE_UPDATE_FAILED = "error.user.profile_update_failed";
    private static final String ERR_PRESENCE_INVALID = "error.user.presence_invalid";

    private final UserApplicationService userApplicationService;
    private final AvatarApplicationService avatarApplicationService;
    private final AppConfig appConfig;
    private final UserMessageSource messages;

    @Inject
    public UserResource(UserApplicationService userApplicationService,
                        AvatarApplicationService avatarApplicationService,
                        AppConfig appConfig,
                        UserMessageSource messages) {
        this.userApplicationService = userApplicationService;
        this.avatarApplicationService = avatarApplicationService;
        this.appConfig = appConfig;
        this.messages = messages;
    }

    private com.avandocmsg.messenger.api.users.dto.UserProfile mapProfile(
            java.util.Optional<com.avandocmsg.messenger.core.domain.UserProfile> domain,
            UserId viewerId) {
        return domain.map(p -> {
            var api = UserDomainMapper.toResponse(p);
            return avatarApplicationService.enrichUserProfile(api, viewerId, p.avatarFileId());
        }).orElse(null);
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
        return TimingSensitivePaths.respondUser(appConfig, () -> {
            var userId = CurrentUserId.uuid(securityContext);
            var profile = mapProfile(
                userApplicationService.getProfileForViewer(UserId.of(userId), UserId.of(userId)),
                UserId.of(userId));
            if (profile == null) {
                TimingSensitivePaths.padNotFound(appConfig);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get("error.user.not_found")))
                    .build();
            }
            return Response.ok(profile).build();
        });
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") String id, @Context SecurityContext securityContext) {
        return TimingSensitivePaths.respondUser(appConfig, () -> {
            var viewerId = CurrentUserId.uuid(securityContext);
            var targetId = UuidParams.required(id, "user_id");
            var profile = mapProfile(
                userApplicationService.getProfileForViewer(UserId.of(viewerId), UserId.of(targetId)),
                UserId.of(viewerId));
            if (profile == null) {
                TimingSensitivePaths.padNotFound(appConfig);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError(404, messages.get("error.user.not_found")))
                    .build();
            }
            return Response.ok(profile).build();
        });
    }

    @PATCH
    @Path("/me")
    @Operation(summary = "Обновить профиль")
    public Response updateProfile(UpdateProfileRequest request,
                                   @Context SecurityContext securityContext) {
        if (request == null) {
            return profileError(Response.Status.BAD_REQUEST);
        }
        var uid = UserId.of(CurrentUserId.uuid(securityContext));
        var result = applyProfileUpdate(request, uid);
        if (result.error() != null) {
            return result.error();
        }
        var profile = mapProfile(userApplicationService.getProfileForViewer(uid, uid), uid);
        return profile != null ? Response.ok(profile).build() : profileError(Response.Status.INTERNAL_SERVER_ERROR);
    }

    private record ProfileUpdateResult(
        com.avandocmsg.messenger.core.domain.UserProfile updated,
        Response error
    ) {}

    private ProfileUpdateResult applyProfileUpdate(UpdateProfileRequest request, UserId uid) {
        com.avandocmsg.messenger.core.domain.UserProfile updated = null;
        if (request.displayName() != null || request.phone() != null) {
            updated = userApplicationService
                .updateProfile(uid, request.displayName(), request.phone())
                .orElse(null);
        }
        var avatarResult = applyAvatarChange(request, uid, updated);
        if (avatarResult.error() != null) {
            return avatarResult;
        }
        updated = avatarResult.updated();
        if (request.avatarHidden() != null) {
            updated = avatarApplicationService.updateUserAvatarHidden(uid, request.avatarHidden()).orElse(updated);
        }
        var failure = failureWhenNoUpdate(request, updated);
        return failure != null ? new ProfileUpdateResult(updated, failure) : new ProfileUpdateResult(updated, null);
    }

    private ProfileUpdateResult applyAvatarChange(
        UpdateProfileRequest request,
        UserId uid,
        com.avandocmsg.messenger.core.domain.UserProfile updated
    ) {
        if (Boolean.TRUE.equals(request.removeAvatar())) {
            if (!avatarApplicationService.userMayUploadAvatar(uid)) {
                return new ProfileUpdateResult(updated, profileError(Response.Status.FORBIDDEN));
            }
            return new ProfileUpdateResult(avatarApplicationService.clearUserAvatar(uid).orElse(updated), null);
        }
        if (request.avatarFileId() == null || request.avatarFileId().isBlank()) {
            return new ProfileUpdateResult(updated, null);
        }
        if (!avatarApplicationService.userMayUploadAvatar(uid)) {
            return new ProfileUpdateResult(updated, profileError(Response.Status.FORBIDDEN));
        }
        var fileId = UuidParams.required(request.avatarFileId(), "avatar_file_id");
        return new ProfileUpdateResult(
            avatarApplicationService.setUserAvatar(uid, FileId.of(fileId)).orElse(null),
            null);
    }

    private Response failureWhenNoUpdate(
        UpdateProfileRequest request,
        com.avandocmsg.messenger.core.domain.UserProfile updated
    ) {
        boolean attempted = request.displayName() != null || request.phone() != null
            || request.avatarFileId() != null || Boolean.TRUE.equals(request.removeAvatar())
            || request.avatarHidden() != null;
        if (updated != null || !attempted) {
            return null;
        }
        if (!avatarApplicationService.avatarsEnabled()
            && (request.avatarFileId() != null || Boolean.TRUE.equals(request.removeAvatar()))) {
            return profileError(Response.Status.FORBIDDEN);
        }
        return profileError(Response.Status.INTERNAL_SERVER_ERROR);
    }

    private Response profileError(Response.Status status) {
        return Response.status(status)
            .entity(new ApiError(status.getStatusCode(), messages.get(ERR_PROFILE_UPDATE_FAILED)))
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
                .entity(new ApiError(400, messages.get(ERR_PRESENCE_INVALID)))
                .build();
        }
        if (request.presenceStatus() != null
            && !PRESENCE_ALLOWED.contains(request.presenceStatus())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_PRESENCE_INVALID)))
                .build();
        }
        if (request.presenceStatus() == null
            && request.customStatusText() == null
            && request.dndUntil() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get(ERR_PRESENCE_INVALID)))
                .build();
        }
        var userId = CurrentUserId.uuid(securityContext);
        var uid = UserId.of(userId);
        return userApplicationService.updateUserStatus(uid, request)
            .flatMap(p -> java.util.Optional.ofNullable(mapProfile(java.util.Optional.of(p), uid)))
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
        var uid = UserId.of(userId);
        return userApplicationService.updatePrivacy(uid, request.privacyDisableReadReceipts())
            .flatMap(p -> java.util.Optional.ofNullable(mapProfile(java.util.Optional.of(p), uid)))
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
        var uid = UserId.of(userId);
        return userApplicationService.updateUiLocale(uid, request.uiLocale())
            .flatMap(p -> java.util.Optional.ofNullable(mapProfile(java.util.Optional.of(p), uid)))
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
