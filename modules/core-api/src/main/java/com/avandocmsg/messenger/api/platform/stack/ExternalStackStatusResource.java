package com.avandocmsg.messenger.api.platform.stack;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.config.RedisProbe;
import com.avandocmsg.messenger.core.port.NatsConnectionStatus;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/v1/platform/external-stack")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform", description = "External stack profile manifests and validation status")
public class ExternalStackStatusResource {

    private final ExternalStackStatusService statusService;
    private final ExternalStackRuntimeManifestProvider manifestProvider;

    public ExternalStackStatusResource() {
        this(new ExternalStackStatusService(), null);
    }

    @Inject
    public ExternalStackStatusResource(
        AppConfig appConfig,
        DataSource dataSource,
        RedisProbe redisProbe,
        MinioClient minioClient,
        NatsConnectionStatus natsConnectionStatus
    ) {
        this(new ExternalStackStatusService(), new ExternalStackRuntimeManifestProvider(
            appConfig,
            ExternalStackActiveProbeService.bounded(
                appConfig,
                dataSource,
                redisProbe::ping,
                () -> minioBucketExists(minioClient, appConfig.minioBucket()),
                natsConnectionStatus::natsClientConnected
            )
        ));
    }

    ExternalStackStatusResource(ExternalStackRuntimeManifestProvider manifestProvider) {
        this(new ExternalStackStatusService(), manifestProvider);
    }

    ExternalStackStatusResource(ExternalStackStatusService statusService, ExternalStackRuntimeManifestProvider manifestProvider) {
        this.statusService = statusService;
        this.manifestProvider = manifestProvider;
    }

    @GET
    @Path("status")
    @Operation(summary = "External stack manifest status")
    public ExternalStackStatusService.ExternalStackStatusResponse status() {
        return statusService.status(manifestProvider != null ? manifestProvider.observations() : List.of());
    }

    @GET
    @Path("profiles")
    @Operation(summary = "External stack connector profile status")
    public ExternalStackStatusService.ExternalStackProfileStatusResponse profiles() {
        return statusService.profileStatus(manifestProvider != null ? manifestProvider.profiles() : List.of());
    }

    @GET
    @Path("compatibility-packs")
    @Operation(summary = "External stack connector compatibility pack catalog")
    public ConnectorCompatibilityPackCatalogResponse compatibilityPacks() {
        var packs = new LinkedHashMap<String, ConnectorCompatibilityPack>();
        for (var pack : ConnectorCompatibilityPacks.catalog()) {
            packs.put(pack.profileId(), pack);
        }
        return new ConnectorCompatibilityPackCatalogResponse(packs);
    }

    @GET
    @Path("compatibility-packs/{profileId}")
    @Operation(summary = "External stack connector compatibility pack by profile id")
    public ConnectorCompatibilityPack compatibilityPack(@PathParam("profileId") String profileId) {
        try {
            return ConnectorCompatibilityPacks.packFor(profileId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown external stack profile: " + profileId);
        }
    }

    @GET
    @Path("status/{component}")
    @Operation(summary = "External stack component status by component id")
    public ExternalStackStatusService.ComponentStatus componentStatus(@PathParam("component") String component) {
        var status = status().components().get(component);
        if (status == null) {
            throw new NotFoundException("Unknown external stack component: " + component);
        }
        return status;
    }

    @GET
    @Path("component-contracts")
    @Operation(summary = "External stack component validation contract catalog")
    public ComponentValidationContractCatalogResponse componentContracts() {
        return new ComponentValidationContractCatalogResponse(ExternalStackComponentContracts.catalog());
    }

    @GET
    @Path("component-contracts/{component}")
    @Operation(summary = "External stack component validation contract")
    public ComponentValidationContract componentContract(@PathParam("component") String component) {
        try {
            return ExternalStackComponentContracts.contractFor(component);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown external stack component contract: " + component);
        }
    }

    @POST
    @Path("preflight/checkpoint")
    @Operation(summary = "Validate external stack migration checkpoint")
    public MigrationCheckpointReport preflightCheckpoint(MigrationCheckpoint checkpoint) {
        return MigrationCheckpointValidator.report(checkpoint);
    }

    @POST
    @Path("preflight/manifests")
    @Operation(summary = "Validate external stack desired manifests")
    public ValidationResult preflightManifests(ExternalStackManifestPreflightRequest request) {
        return ExternalStackManifestValidator.validateDesiredManifests(
            request != null ? request.manifests() : List.of()
        );
    }

    @POST
    @Path("preflight/profile")
    @Operation(summary = "Validate one external stack connector profile for production use")
    public ValidationResult preflightProfile(ExternalStackProfilePreflightRequest request) {
        var profileId = request != null ? request.profileId() : null;
        if (profileId == null || profileId.isBlank()) {
            return new ValidationResult(false, List.of("profile_id is required"), List.of(), true, Map.of());
        }
        var pack = compatibilityPack(profileId);
        var failures = pack.supported()
            ? List.<String>of()
            : List.of("profile " + profileId + " is not production-supported");
        return new ValidationResult(
            failures.isEmpty(),
            failures,
            pack.unsupportedModes().stream()
                .map(mode -> "unsupported mode: " + mode)
                .toList(),
            true,
            Map.of(
                "component", pack.component(),
                "lifecycle_status", pack.lifecycleStatus().name()
            )
        );
    }

    public record ConnectorCompatibilityPackCatalogResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("packs") Map<String, ConnectorCompatibilityPack> packs
    ) {}

    public record ComponentValidationContractCatalogResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("contracts") Map<String, ComponentValidationContract> contracts
    ) {}

    private static boolean minioBucketExists(MinioClient minioClient, String bucket) {
        try {
            return minioClient != null
                && minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            return false;
        }
    }
}
