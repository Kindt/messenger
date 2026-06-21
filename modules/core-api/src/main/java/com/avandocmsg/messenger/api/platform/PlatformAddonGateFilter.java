package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Applies declarative catalog API gates by feature key (spec 024).
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 100)
public class PlatformAddonGateFilter implements ContainerRequestFilter {

    private final PlatformModuleRegistry registry;
    private final UserMessageSource messages;

    @Inject
    public PlatformAddonGateFilter(PlatformModuleRegistry registry, UserMessageSource messages) {
        this.registry = registry;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var gate = registry.apiGateFor(request.getUriInfo().getPath(), request.getMethod());
        if (gate == null) {
            return;
        }
        var feature = registry.resolveFeature(gate.feature());
        if (feature.state() == PlatformModuleState.enabled) {
            return;
        }
        if (feature.state() == PlatformModuleState.degraded
            && "fallback".equalsIgnoreCase(feature.apiBehavior())) {
            return;
        }
        var status = gate.httpCode() != null ? gate.httpCode() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
        var messageKey = gate.messageKey() != null ? gate.messageKey() : "module.disabled.generic";
        request.abortWith(Response.status(status)
            .entity(new ApiError(status, messages.get(messageKey)))
            .build());
    }
}
