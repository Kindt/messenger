package com.avandocmsg.messenger.api.filter;

import com.avandocmsg.messenger.api.config.OrgRoutingContext;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * FR-OPT-09 phase-B: sets {@link OrgRoutingContext} from authenticated user's {@code org_id}.
 * Runs after {@link JwtAuthFilter}; cleared in {@link OrgRoutingClearFilter}.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class OrgRoutingFilter implements ContainerRequestFilter {

    private final UserLookupPort userLookupPort;

    @Inject
    public OrgRoutingFilter(UserLookupPort userLookupPort) {
        this.userLookupPort = userLookupPort;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (JwtAuthFilter.isPublicJerseyPath(request.getUriInfo().getPath())) {
            return;
        }
        var sc = request.getSecurityContext();
        if (sc == null || sc.getUserPrincipal() == null) {
            return;
        }
        try {
            var userId = UUID.fromString(sc.getUserPrincipal().getName());
            userLookupPort.findById(userId)
                .map(p -> p.orgId())
                .filter(org -> org != null && !org.isBlank())
                .map(UUID::fromString)
                .ifPresent(OrgRoutingContext::set);
        } catch (Exception ignored) {
            // Missing/invalid org_id → primary shard only
        }
    }
}
