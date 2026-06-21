package com.avandocmsg.messenger.api.users;

import com.avandocmsg.messenger.api.params.CurrentUserId;
import com.avandocmsg.messenger.api.plugins.PluginRepository;
import com.avandocmsg.messenger.api.users.dto.MeIntegrationsMarketplaceResponse;
import com.avandocmsg.messenger.common.dto.ApiError;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import javax.sql.DataSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Path("/v1/me/integrations/marketplace")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Integrations", description = "User marketplace catalog (spec 022 US24 T02311)")
public class MeIntegrationsMarketplaceResource {

    private final UserLookupPort userLookupPort;
    private final PluginRepository pluginRepository;
    private final UserIntegrationConnectRepository connectRepository;
    private final UserMessageSource messages;

    @Inject
    public MeIntegrationsMarketplaceResource(
        UserLookupPort userLookupPort,
        PluginRepository pluginRepository,
        DataSource dataSource,
        UserMessageSource messages
    ) {
        this.userLookupPort = userLookupPort;
        this.pluginRepository = pluginRepository;
        this.connectRepository = new UserIntegrationConnectRepository(dataSource);
        this.messages = messages;
    }

    @GET
    @Operation(summary = "Org-allowed integration marketplace for users")
    public Response list(@Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userLookupPort.findById(userId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return Response.ok(new MeIntegrationsMarketplaceResponse(List.of(), List.of())).build();
        }
        var orgId = UUID.fromString(profile.orgId());
        var connected = connectRepository.listConnectedInstanceIds(userId);
        var items = buildItems(orgId, connected);
        items.sort(Comparator.comparing(MeIntegrationsMarketplaceResponse.MarketplaceItem::category)
            .thenComparing(MeIntegrationsMarketplaceResponse.MarketplaceItem::label));
        var categories = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        for (var item : items) {
            if (seen.add(item.category())) {
                categories.add(item.category());
            }
        }
        return Response.ok(new MeIntegrationsMarketplaceResponse(categories, items)).build();
    }

    @POST
    @Path("{instanceId}/connect")
    @Operation(summary = "Connect marketplace integration for current user")
    public Response connect(@PathParam("instanceId") String instanceId,
                            @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var profile = userLookupPort.findById(userId).orElse(null);
        if (profile == null || profile.orgId() == null || profile.orgId().isBlank()) {
            return notFound();
        }
        var instId = parseUuid(instanceId);
        if (instId == null || !isOrgInstance(UUID.fromString(profile.orgId()), instId)) {
            return notFound();
        }
        connectRepository.connect(userId, instId);
        return Response.status(Response.Status.CREATED).build();
    }

    @DELETE
    @Path("{instanceId}/connect")
    @Operation(summary = "Disconnect marketplace integration for current user")
    public Response disconnect(@PathParam("instanceId") String instanceId,
                               @Context SecurityContext securityContext) {
        var userId = CurrentUserId.uuid(securityContext);
        var instId = parseUuid(instanceId);
        if (instId == null) {
            return notFound();
        }
        if (!connectRepository.disconnect(userId, instId)) {
            return notFound();
        }
        return Response.noContent().build();
    }

    private ArrayList<MeIntegrationsMarketplaceResponse.MarketplaceItem> buildItems(UUID orgId, java.util.Set<UUID> connected) {
        var items = new ArrayList<MeIntegrationsMarketplaceResponse.MarketplaceItem>();
        for (var instance : pluginRepository.listInstances(orgId)) {
            if (!instance.enabled()) {
                continue;
            }
            var market = marketplaceFromConfig(instance.configJson());
            if (market != null && !market.visible()) {
                continue;
            }
            var launcher = launcherFromConfig(instance.configJson());
            var label = market != null && market.label() != null && !market.label().isBlank()
                ? market.label()
                : (launcher != null && launcher.label() != null ? launcher.label() : instance.displayName());
            var launchUrl = launcher != null ? launcher.launchUrl() : null;
            if (launchUrl == null || launchUrl.isBlank()) {
                launchUrl = firstMenuUrl(instance.configJson());
            }
            if (launchUrl == null || launchUrl.isBlank()) {
                continue;
            }
            var category = market != null && market.category() != null ? market.category() : "general";
            var description = market != null ? market.description() : "";
            var openMode = launcher != null && launcher.openMode() != null ? launcher.openMode() : "iframe";
            items.add(new MeIntegrationsMarketplaceResponse.MarketplaceItem(
                instance.id().toString(),
                instance.presetId(),
                instance.pluginClass(),
                label,
                description,
                category,
                instance.botName(),
                launcher != null ? launcher.iconUrl() : null,
                launchUrl,
                openMode,
                connected.contains(instance.id())));
        }
        return items;
    }

    private boolean isOrgInstance(UUID orgId, UUID instanceId) {
        return pluginRepository.listInstances(orgId).stream()
            .anyMatch(i -> i.enabled() && instanceId.equals(i.id()));
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ApiError(404, messages.get("error.not_found")))
            .build();
    }

    private static MarketplaceConfig marketplaceFromConfig(JsonNode config) {
        if (config == null || !config.has("marketplace")) {
            return new MarketplaceConfig(true, null, null, null);
        }
        var node = config.get("marketplace");
        if (node == null || node.isNull()) {
            return null;
        }
        var visible = !node.has("visible") || node.get("visible").asBoolean(true);
        return new MarketplaceConfig(
            visible,
            textOrNull(node, "label"),
            textOrNull(node, "description"),
            textOrNull(node, "category"));
    }

    private static LauncherConfig launcherFromConfig(JsonNode config) {
        if (config == null || !config.has("launcher")) {
            return null;
        }
        var node = config.get("launcher");
        if (node == null || node.isNull()) {
            return null;
        }
        return new LauncherConfig(
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
        if (menu == null || !menu.has("buttons") || !menu.get("buttons").isArray()) {
            return null;
        }
        for (var btn : menu.get("buttons")) {
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

    private record MarketplaceConfig(boolean visible, String label, String description, String category) {}

    private record LauncherConfig(String label, String iconUrl, String launchUrl, String openMode) {}
}
