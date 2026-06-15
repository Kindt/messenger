package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.bots.BotPrincipal;
import com.avandocmsg.messenger.api.bots.BotService;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class BotTokenAuthFilter implements ContainerRequestFilter {

    private final BotService botService;
    private final UserMessageSource messages;

    @Inject
    public BotTokenAuthFilter(BotService botService, UserMessageSource messages) {
        this.botService = botService;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var path = JwtAuthFilter.normalizeJerseyPath(request.getUriInfo().getPath());
        if (!path.startsWith("v1/bot/")) {
            return;
        }

        var authHeader = request.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.bot.missing_token")))
                .build());
            return;
        }

        var token = authHeader.substring(7).trim();
        var bot = botService.authenticateToken(token);
        if (bot.isEmpty()) {
            request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ApiError(401, messages.get("error.bot.invalid_token")))
                .build());
            return;
        }

        var row = bot.get();
        var principal = new BotPrincipal(row.id().toString(), row.botName());
        request.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return request.getUriInfo().getAbsolutePath().getScheme().equals("https");
            }

            @Override
            public String getAuthenticationScheme() {
                return "BotToken";
            }
        });
    }
}
