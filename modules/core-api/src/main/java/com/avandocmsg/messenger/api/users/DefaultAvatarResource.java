package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.params.UuidParams;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.application.DefaultAvatarRenderer;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;

@Path("/v1/users")
@Tag(name = "Users", description = "Профиль и присутствие")
public class DefaultAvatarResource {

    private final AppConfig appConfig;
    private final UserRepositoryPort userRepositoryPort;
    private final UserMessageSource messages;

    @Inject
    public DefaultAvatarResource(AppConfig appConfig, UserRepositoryPort userRepositoryPort,
                                 UserMessageSource messages) {
        this.appConfig = appConfig;
        this.userRepositoryPort = userRepositoryPort;
        this.messages = messages;
    }

    @GET
    @Path("{userId}/avatar/default")
    @Produces("image/png")
    @Operation(summary = "Default avatar PNG (initials) when user has no custom avatar")
    public Response defaultAvatar(@PathParam("userId") String userIdStr) {
        if (!appConfig.avatarsEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.not_found")))
                .build();
        }
        var userId = UuidParams.required(userIdStr, "user_id");
        var profile = userRepositoryPort.findById(UserId.of(userId)).orElse(null);
        if (profile == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(404, messages.get("error.user_not_found")))
                .build();
        }
        var png = DefaultAvatarRenderer.pngBytes(profile.displayName(), profile.username());
        var cache = new CacheControl();
        cache.setMaxAge(3600);
        return Response.ok(png).type("image/png").cacheControl(cache).build();
    }
}
