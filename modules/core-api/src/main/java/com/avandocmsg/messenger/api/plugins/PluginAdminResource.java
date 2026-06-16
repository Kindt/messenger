package com.avandocmsg.messenger.api.plugins;

import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.ConfigureOutboundRequest;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.CreateL0InstanceRequest;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.InstanceJson;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.InstanceListResponse;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.InvokePluginRequest;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.PresetJson;
import com.avandocmsg.messenger.api.plugins.PluginAdminDtos.PresetListResponse;
import com.avandocmsg.messenger.common.plugin.PluginEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/v1/admin/plugins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin Plugins", description = "Spec 014: plugin presets and instances")
@RolesAllowed("admin")
public class PluginAdminResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PluginRepository repository;
    private final PluginPlatformService platformService;
    private final PluginPolicyService policyService;

    @Inject
    public PluginAdminResource(
        PluginRepository repository,
        PluginPlatformService platformService,
        PluginPolicyService policyService
    ) {
        this.repository = repository;
        this.platformService = platformService;
        this.policyService = policyService;
    }

    @GET
    @Path("presets")
    @Operation(summary = "List plugin presets")
    public PresetListResponse presets() {
        var presets = repository.listPresets().stream()
            .map(p -> new PresetJson(
                p.id(),
                p.pluginClass(),
                p.runtimeKind(),
                p.configSchemaVersion(),
                parseJson(p.capabilitiesJson())
            ))
            .toList();
        return new PresetListResponse(presets);
    }

    @GET
    @Path("instances")
    @Operation(summary = "List plugin instances for organization")
    public InstanceListResponse instances(@QueryParam("org_id") UUID orgId) {
        if (orgId == null) {
            return new InstanceListResponse(List.of());
        }
        var rows = repository.listInstances(orgId).stream().map(PluginAdminResource::toJson).toList();
        return new InstanceListResponse(rows);
    }

    @GET
    @Path("policies")
    @Operation(summary = "Get org plugin policy (LLM mode, OCR, allowlist)")
    public PluginPolicyService.PolicyJson getPolicy(@QueryParam("org_id") UUID orgId) {
        if (orgId == null) {
            return null;
        }
        return PluginPolicyService.toJson(policyService.getOrDefault(orgId));
    }

    @POST
    @Path("policies")
    @Operation(summary = "Update org plugin policy")
    public Response updatePolicy(@QueryParam("org_id") UUID orgId, PluginPolicyService.UpdatePolicyRequest request) {
        if (orgId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var updated = policyService.update(orgId, request);
        if (updated.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(error("error.plugin.policy_invalid")).build();
        }
        return Response.ok(PluginPolicyService.toJson(updated.get())).build();
    }

    @POST
    @Path("instances/l0")
    @Operation(summary = "Create L0 FAQ/menu plugin instance")
    public Response createL0(CreateL0InstanceRequest request) {
        if (request == null || request.orgId() == null || request.botName() == null || request.botName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var config = request.configJson() != null
            ? (com.fasterxml.jackson.databind.node.ObjectNode) request.configJson()
            : MAPPER.createObjectNode();
        var created = platformService.createL0Instance(
            request.orgId(),
            request.botName().trim(),
            request.displayName() != null ? request.displayName().trim() : request.botName().trim(),
            config
        );
        if (created.isEmpty()) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        return Response.status(Response.Status.CREATED).entity(toJson(created.get())).build();
    }

    @POST
    @Path("instances/{instanceId}/outbound")
    @Operation(summary = "Configure outbound webhook target for plugin instance")
    public Response configureOutbound(
        @PathParam("instanceId") UUID instanceId,
        ConfigureOutboundRequest request
    ) {
        if (request == null || request.targetChatId() == null || request.actorUserId() == null
            || request.outboundToken() == null || request.outboundToken().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var updated = platformService.configureOutbound(
            instanceId,
            request.targetChatId(),
            request.actorUserId(),
            request.outboundToken()
        );
        if (updated.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toJson(updated.get())).build();
    }

    @POST
    @Path("instances/{instanceId}/invoke")
    @Operation(summary = "Test-invoke plugin instance (admin)")
    public Response invoke(@PathParam("instanceId") UUID instanceId, InvokePluginRequest request) {
        var type = request != null && request.type() != null ? request.type() : "mention";
        var text = request != null ? request.text() : null;
        var payload = request != null && request.payload() != null
            ? MAPPER.convertValue(request.payload(), java.util.Map.class)
            : java.util.Map.<String, Object>of();
        var event = new PluginEvent(
            UUID.randomUUID().toString(),
            instanceId,
            null,
            type,
            null,
            null,
            text,
            payload,
            null
        );
        var result = platformService.invoke(instanceId, event);
        return switch (result.outcome()) {
            case SUCCESS -> Response.ok(result.response()).build();
            case NOT_FOUND -> Response.status(Response.Status.NOT_FOUND)
                .entity(error(result.errorKey())).build();
            case DISABLED -> Response.status(Response.Status.CONFLICT)
                .entity(error(result.errorKey())).build();
            case POLICY_DENIED -> Response.status(Response.Status.FORBIDDEN)
                .entity(error(result.errorKey())).build();
            case RUNTIME_ERROR -> Response.status(Response.Status.BAD_GATEWAY)
                .entity(error(result.errorKey())).build();
        };
    }

    private static InstanceJson toJson(PluginRepository.InstanceRow row) {
        return new InstanceJson(
            row.id(),
            row.orgId(),
            row.presetId(),
            row.botName(),
            row.displayName(),
            row.enabled(),
            row.pluginClass(),
            row.runtimeEndpoint(),
            row.configJson(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    private static com.fasterxml.jackson.databind.JsonNode parseJson(String text) {
        try {
            return MAPPER.readTree(text != null ? text : "[]");
        } catch (Exception e) {
            return MAPPER.createArrayNode();
        }
    }

    private java.util.Map<String, String> error(String key) {
        return java.util.Map.of("error", platformService.localizedError(key));
    }
}
