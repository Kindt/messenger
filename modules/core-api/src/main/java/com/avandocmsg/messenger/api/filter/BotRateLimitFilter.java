package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.bots.BotPrincipal;
import com.avandocmsg.messenger.api.bots.BotRateLimiter;
import com.avandocmsg.messenger.api.bots.BotService;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

@Provider
@Priority(Priorities.AUTHENTICATION - 50)
public class BotRateLimitFilter implements ContainerRequestFilter {

    private final BotRateLimiter rateLimiter;
    private final UserMessageSource messages;

    @Inject
    public BotRateLimitFilter(BotRateLimiter rateLimiter, UserMessageSource messages) {
        this.rateLimiter = rateLimiter;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var path = JwtAuthFilter.normalizeJerseyPath(request.getUriInfo().getPath());
        if (!path.startsWith("v1/bot/")) {
            return;
        }
        var principal = request.getSecurityContext().getUserPrincipal();
        if (!(principal instanceof BotPrincipal botPrincipal)) {
            return;
        }
        var botId = UUID.fromString(botPrincipal.botId());
        if (!rateLimiter.tryAcquire(botId)) {
            request.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                .entity(new ApiError(429, messages.get("error.bot.rate_limit")))
                .build());
        }
    }
}
