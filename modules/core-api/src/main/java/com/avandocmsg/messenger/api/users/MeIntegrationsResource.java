package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.avandocmsg.messenger.api.repository.UserRepository;
import com.avandocmsg.messenger.api.users.dto.MeIntegrationsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/v1/me/integrations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Integrations", description = "User-visible L0 SmartApps launcher")
public class MeIntegrationsResource {

    private final UserRepository userRepository;
    private final PluginRepository pluginRepository;

    @Inject
    public MeIntegrationsResource(UserRepository userRepository, PluginRepository pluginRepository) {
        this.userRepository = userRepository;
        this.pluginRepository = pluginRepository;
    }

    @GET
    @Operation(summary = "List integrations", description = "Enabled L0 plugin instances visible in web launcher")
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userRepository.findById(userId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Response.ok(new MeIntegrationsResponse(List.of(), MeIntegrationsVitrine.tiles())).build();
        }
        var orgId = UUID.fromString(profile.orgId());
        var items = new ArrayList<MeIntegrationsResponse.IntegrationItem>();
        for (var instance : pluginRepository.listInstances(orgId)) {
            if (!instance.enabled() || !"L0".equalsIgnoreCase(instance.pluginClass())) {
                continue;
            }
            var launcher = launcherFromConfig(instance.configJson());
            if (launcher != null && !launcher.visible()) {
                continue;
            }
            var label = launcher != null && launcher.label() != null && !launcher.label().isBlank()
                ? launcher.label()
                : instance.displayName();
            var launchUrl = launcher != null ? launcher.launchUrl() : null;
            if (launchUrl == null || launchUrl.isBlank()) {
                launchUrl = firstMenuUrl(instance.configJson());
            }
            if (launchUrl == null || launchUrl.isBlank()) {
                continue;
            }
            var openMode = launcher != null && launcher.openMode() != null ? launcher.openMode() : "iframe";
            items.add(new MeIntegrationsResponse.IntegrationItem(
                instance.id().toString(),
                label,
                instance.botName(),
                launcher != null ? launcher.iconUrl() : null,
                launchUrl,
                openMode));
        }
        return Response.ok(new MeIntegrationsResponse(items, MeIntegrationsVitrine.tiles())).build();
    }

    private static LauncherConfig launcherFromConfig(JsonNode config) {
        if (config == null || !config.has("launcher")) {
            return null;
        }
        var node = config.get("launcher");
        if (node == null || node.isNull()) {
            return null;
        }
        var visible = !node.has("visible") || node.get("visible").asBoolean(true);
        return new LauncherConfig(
            visible,
            textOrNull(node, "label"),
            textOrNull(node, "icon_url"),
            textOrNull(node, "launch_url"),
            textOrNull(node, "open_mode"));
    }

    private static String firstMenuUrl(JsonNode config) {
        if (config == null || !config.has("menu")) {
            return null;
        }
        var menu = config.get("menu");
        if (menu == null || !menu.has("buttons")) {
            return null;
        }
        var buttons = menu.get("buttons");
        if (!buttons.isArray()) {
            return null;
        }
        for (var btn : buttons) {
            var url = textOrNull(btn, "url");
            if (url != null && !url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private record LauncherConfig(boolean visible, String label, String iconUrl, String launchUrl, String openMode) {}
}
