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

import java.util.List;
import java.util.Map;

/**
 * Rejects API calls when required product add-on is not effective (spec 021 degradation).
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 100)
public class PlatformAddonGateFilter implements ContainerRequestFilter {

    private static final Map<String, List<String>> PATH_PREFIX_TO_ADDONS = Map.of(
        "v1/bot/", List.of("addon-bots", "addon-integrations"),
        "v1/export", List.of("addon-export"),
        "v1/chats/", List.of("addon-live"),
        "v1/admin/export", List.of("addon-export"),
        "v1/plugins", List.of("addon-integrations")
    );

    private final PlatformModuleRegistry registry;
    private final UserMessageSource messages;

    @Inject
    public PlatformAddonGateFilter(PlatformModuleRegistry registry, UserMessageSource messages) {
        this.registry = registry;
        this.messages = messages;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        var path = normalizePath(request.getUriInfo().getPath());
        for (var entry : PATH_PREFIX_TO_ADDONS.entrySet()) {
            if (!path.startsWith(entry.getKey())) {
                continue;
            }
            if (entry.getKey().equals("v1/chats/") && !path.contains("/live-sessions")
                && !path.contains("/calls/livekit")) {
                continue;
            }
            if (anyAddonEffective(entry.getValue())) {
                return;
            }
            var messageKey = messageKeyFor(entry.getValue());
            request.abortWith(Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ApiError(503, messages.get(messageKey)))
                .build());
            return;
        }
    }

    private boolean anyAddonEffective(List<String> addonIds) {
        for (var id : addonIds) {
            if (registry.isAddonEffective(id)) {
                return true;
            }
        }
        return false;
    }

    private static String messageKeyFor(List<String> addonIds) {
        if (addonIds.contains("addon-export")) {
            return "module.export.disabled";
        }
        if (addonIds.contains("addon-bots")) {
            return "module.bots.disabled";
        }
        if (addonIds.contains("addon-integrations")) {
            return "module.integrations.disabled";
        }
        if (addonIds.contains("addon-live")) {
            return "module.live.disabled";
        }
        return "module.disabled.generic";
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        var p = path.startsWith("/") ? path.substring(1) : path;
        if (p.startsWith("api/")) {
            p = p.substring(4);
        }
        return p;
    }
}
