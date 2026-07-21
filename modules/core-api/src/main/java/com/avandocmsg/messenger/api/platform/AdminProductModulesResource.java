package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.dto.AdminProductModulesResponse;
import com.avandocmsg.messenger.api.platform.dto.PlatformModuleOverrideRequest;
import com.avandocmsg.messenger.core.port.AuditPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.ArrayList;
import java.util.UUID;

@Path("/v1/admin/ui/product-modules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Admin UI", description = "Product module composition and admin overrides")
@RolesAllowed("admin")
public class AdminProductModulesResource {

    private final PlatformModuleRegistry registry;
    private final PlatformModuleOverrideRepository overrideRepository;
    private final AuditPort auditPort;

    @Inject
    public AdminProductModulesResource(
        PlatformModuleRegistry registry,
        PlatformModuleOverrideRepository overrideRepository,
        AuditPort auditPort
    ) {
        this.registry = registry;
        this.overrideRepository = overrideRepository;
        this.auditPort = auditPort;
    }

    @GET
    @Operation(summary = "Product module grid", security = @SecurityRequirement(name = "bearerAuth"))
    public AdminProductModulesResponse list() {
        var catalog = registry.catalog();
        var resolved = registry.resolveAllAddons();
        var overrides = overrideRepository.findAll();
        var rows = new ArrayList<AdminProductModulesResponse.AddonRow>();
        for (var addon : catalog.addons()) {
            rows.add(toAddonRow(addon, resolved.get(addon.id()), overrides.get(addon.id())));
        }
        return new AdminProductModulesResponse(
            new AdminProductModulesResponse.BaseRow(
                catalog.base().id(),
                catalog.base().label(),
                "required"
            ),
            rows
        );
    }

    private static AdminProductModulesResponse.AddonRow toAddonRow(
        ProductModulesCatalog.AddonEntry addon,
        PlatformModuleRegistry.ResolvedAddonState state,
        PlatformModuleOverrideRow override
    ) {
        var runtime = addon.runtime();
        var gates = addon.gates();
        var acceptance = addon.acceptance();
        var migration = addon.migrationBundle();
        return new AdminProductModulesResponse.AddonRow(
            addon.id(),
            addon.label(),
            state.selected(),
            state.installed(),
            state.schemaInstalled(),
            state.runtimeReady(),
            state.adminEnabled(),
            state.state().code(),
            state.reason() != null ? state.reason().code() : null,
            override != null && override.disabled(),
            override != null && override.forceEnabled(),
            addon.internalInfra(),
            runtime != null ? runtime.services() : java.util.List.of(),
            runtime != null ? runtime.workers() : java.util.List.of(),
            runtime != null ? runtime.requiredSecrets() : java.util.List.of(),
            migration != null ? migration.id() : null,
            migration != null ? migration.historyTable() : null,
            gateSize(gates == null ? null : gates.api()),
            gateSize(gates == null ? null : gates.ui()),
            gateSize(gates == null ? null : gates.jobs()),
            gateSize(gates == null ? null : gates.hooks()),
            acceptance != null ? acceptance.positive() : java.util.List.of(),
            acceptance != null ? acceptance.disabled() : java.util.List.of(),
            acceptance != null ? acceptance.degraded() : java.util.List.of()
        );
    }

    private static int gateSize(java.util.List<?> gates) {
        return gates != null ? gates.size() : 0;
    }

    @PUT
    @Path("{addonId}/override")
    @Operation(summary = "Admin soft-disable add-on", security = @SecurityRequirement(name = "bearerAuth"))
    public Response override(
        @PathParam("addonId") String addonId,
        PlatformModuleOverrideRequest body,
        @Context SecurityContext securityContext
    ) {
        registry.resolveAddon(addonId);
        PlatformModuleReason reason = PlatformModuleReason.ADMIN_OVERRIDE;
        if (body.overrideReason() != null && !body.overrideReason().isBlank()) {
            try {
                reason = PlatformModuleReason.fromCode(body.overrideReason());
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
        }
        var force = body.forceEnabled() != null && body.forceEnabled();
        UUID actor = null;
        if (securityContext.getUserPrincipal() != null) {
            try {
                actor = UUID.fromString(securityContext.getUserPrincipal().getName());
            } catch (IllegalArgumentException ignored) {
                // keep null
            }
        }
        if (body.disabled()) {
            overrideRepository.upsert(addonId, true, reason, force, actor);
        } else {
            overrideRepository.delete(addonId);
        }
        auditPort.record(
            actor,
            "platform_module_override",
            "platform_addon",
            addonId,
            body.disabled() ? "{\"disabled\":true}" : null
        );
        return Response.ok(list()).build();
    }
}
