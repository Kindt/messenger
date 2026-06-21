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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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

    public record ConnectorCompatibilityPackCatalogResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("packs") Map<String, ConnectorCompatibilityPack> packs
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
