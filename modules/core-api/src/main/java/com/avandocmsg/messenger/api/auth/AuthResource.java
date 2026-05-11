package com.avandocmsg.messenger.api.auth;

import com.avandocmsg.messenger.api.auth.dto.LoginRequest;
import com.avandocmsg.messenger.api.auth.dto.LoginResponse;
import com.avandocmsg.messenger.api.auth.dto.RegisterRequest;
import com.avandocmsg.messenger.api.auth.dto.RegisterResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication and registration")
public class AuthResource {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final UserMessageSource messages;

    @Inject
    public AuthResource(AuthService authService, AuthRateLimiter rateLimiter, UserMessageSource messages) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.messages = messages;
    }

    @POST
    @Path("/login")
    @Operation(summary = "Login", description = "Authenticate with username and password, returns JWT tokens")
    @ApiResponse(responseCode = "200", description = "Authenticated successfully",
        content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response login(LoginRequest request, @Context HttpServletRequest httpRequest) {
        if (!rateLimiter.allowLogin(AuthRateLimiter.clientIp(httpRequest))) {
            return Response.status(429)
                .entity(new ApiError(429, messages.get("error.auth.rate_login")))
                .build();
        }
        if (request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.auth.username_password_required")))
                .build();
        }
        var response = authService.login(request);
        if (response == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.auth.invalid_credentials")))
                .build();
        }
        return Response.ok(response).build();
    }

    @POST
    @Path("/register")
    @Operation(summary = "Register", description = "Create a new user account")
    @ApiResponse(responseCode = "201", description = "User created",
        content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    @ApiResponse(responseCode = "409", description = "Username already exists",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response register(RegisterRequest request, @Context HttpServletRequest httpRequest) {
        if (!rateLimiter.allowRegister(AuthRateLimiter.clientIp(httpRequest))) {
            return Response.status(429)
                .entity(new ApiError(429, messages.get("error.auth.rate_register")))
                .build();
        }
        if (request.username() == null || request.password() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.auth.username_password_required")))
                .build();
        }
        if (request.username().length() < 3 || request.username().length() > 32) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.auth.username_length")))
                .build();
        }
        var response = authService.register(request);
        if (response == null) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ApiError(409, messages.get("error.auth.username_exists")))
                .build();
        }
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh token",
        description = "Обмен refresh_token на новую пару токенов (как у Keycloak); тело ответа совпадает с /login")
    @ApiResponse(responseCode = "200", description = "Новые токены",
        content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Недействительный refresh token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response refresh(RefreshTokenRequest request) {
        if (request.refreshToken() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.auth.refresh_required")))
                .build();
        }
        var tokens = authService.refreshAccessToken(request.refreshToken());
        if (tokens == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.auth.invalid_refresh_token")))
                .build();
        }
        return Response.ok(tokens).build();
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Logout",
        description = "Отзыв refresh_token в Keycloak (revoke); тело как у /refresh. Успех — **204** (токен сброшен или уже недействителен).")
    @ApiResponse(responseCode = "204", description = "Сессия refresh завершена на сервере")
    @ApiResponse(responseCode = "400", description = "Нет refresh_token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Keycloak недоступен",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
    public Response logout(RefreshTokenRequest request, @Context HttpServletRequest httpRequest) {
        if (!rateLimiter.allowLogout(AuthRateLimiter.clientIp(httpRequest))) {
            return Response.status(429)
                .entity(new ApiError(429, messages.get("error.auth.rate_logout")))
                .build();
        }
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(400, messages.get("error.auth.refresh_token_required")))
                .build();
        }
        if (!authService.revokeRefreshToken(request.refreshToken())) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(new ApiError(502, messages.get("error.auth.revocation_unavailable")))
                .build();
        }
        return Response.noContent().build();
    }

    @io.swagger.v3.oas.annotations.media.Schema(name = "RefreshTokenRequest")
    record RefreshTokenRequest(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Refresh token from login response")
        @JsonProperty("refresh_token") String refreshToken
    ) {}
}
