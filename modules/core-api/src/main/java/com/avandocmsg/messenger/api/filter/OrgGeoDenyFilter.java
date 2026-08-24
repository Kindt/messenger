package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.OrgRoutingContext;
import com.avandocmsg.messenger.api.security.DeniedAccessAudit;
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
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.util.Locale;
import java.util.UUID;

/**
 * Prod geo deny scaffold: nginx sets {@code X-Geo-Country} or {@code CF-IPCountry}.
 * Enforce with {@code ORG_GEO_DENY_ENFORCE=1} and {@code ORG_GEO_DENY_COUNTRIES=RU,CN,...}.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 1)
public class OrgGeoDenyFilter implements ContainerRequestFilter {

    private static final String HDR_GEO = "X-Geo-Country";
    private static final String HDR_CF = "CF-IPCountry";

    private final AppConfig appConfig;
    private final DeniedAccessAudit deniedAccessAudit;
    private final UserMessageSource messages;

    @Context
    private HttpServletRequest httpRequest;

    @Inject
    public OrgGeoDenyFilter(AppConfig appConfig, DeniedAccessAudit deniedAccessAudit, UserMessageSource messages) {
        this.appConfig = appConfig;
        this.deniedAccessAudit = deniedAccessAudit;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!appConfig.orgGeoDenyEnforce()) {
            return;
        }
        if (JwtAuthFilter.isPublicJerseyPath(request.getUriInfo().getPath())) {
            return;
        }
        var denied = appConfig.orgGeoDeniedCountries();
        if (denied.isEmpty()) {
            return;
        }
        var country = resolveCountryCode();
        if (country == null || !denied.contains(country)) {
            return;
        }
        var orgId = OrgRoutingContext.get();
        deniedAccessAudit.geoDenied(actorUserId(request.getSecurityContext()), orgId, country);
        request.abortWith(Response.status(Response.Status.FORBIDDEN)
            .entity(new ApiError(403, messages.get("error.org.geo_denied")))
            .build());
    }

    private String resolveCountryCode() {
        if (httpRequest == null) {
            return null;
        }
        for (var header : new String[] { HDR_GEO, HDR_CF }) {
            var raw = httpRequest.getHeader(header);
            if (raw != null && !raw.isBlank()) {
                return raw.trim().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static UUID actorUserId(SecurityContext securityContext) {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(securityContext.getUserPrincipal().getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
