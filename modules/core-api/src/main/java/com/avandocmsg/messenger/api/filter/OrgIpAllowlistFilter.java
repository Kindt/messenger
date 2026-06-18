package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.auth.AuthRateLimiter;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.OrgRoutingContext;
import com.avandocmsg.messenger.api.security.OrgIpAllowlistService;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/** Lab enforcement of org IP allowlist after auth (spec 022 US32). */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class OrgIpAllowlistFilter implements ContainerRequestFilter {

    private final OrgIpAllowlistService allowlistService;
    private final AppConfig appConfig;
    private final UserMessageSource messages;

  @Context
    private HttpServletRequest httpRequest;

    @Inject
    public OrgIpAllowlistFilter(
        OrgIpAllowlistService allowlistService,
        AppConfig appConfig,
        UserMessageSource messages
    ) {
        this.allowlistService = allowlistService;
        this.appConfig = appConfig;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!appConfig.orgIpAllowlistEnforce()) {
            return;
        }
        if (JwtAuthFilter.isPublicJerseyPath(request.getUriInfo().getPath())) {
            return;
        }
        var orgId = OrgRoutingContext.get();
        if (orgId == null) {
            return;
        }
        var clientIp = AuthRateLimiter.clientIp(httpRequest);
        if (allowlistService.isAllowed(orgId, clientIp)) {
            return;
        }
        request.abortWith(Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.org.ip_allowlist_denied")))
            .build());
    }
}
