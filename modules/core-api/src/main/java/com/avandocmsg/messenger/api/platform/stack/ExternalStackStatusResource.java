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

    private static final String SEVERITY_BLOCKED = "blocked";
    private static final String SEVERITY_WARNING = "warning";
    private static final String SEVERITY_OK = "ok";

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

    @GET
    @Path("catalog-health")
    @Operation(summary = "External stack catalog health and drift report")
    public ExternalStackCatalogHealthReport catalogHealth() {
        return ConnectorCompatibilityPacks.healthReport();
    }

    @GET
    @Path("component-profile-summary")
    @Operation(summary = "External stack component profile readiness summary")
    public ExternalStackComponentProfileSummaryCatalog componentProfileSummary() {
        return ConnectorCompatibilityPacks.componentSummaries();
    }

    @GET
    @Path("component-profile-summary/{component}")
    @Operation(summary = "External stack component profile readiness summary by component")
    public ExternalStackComponentProfileSummary componentProfileSummary(@PathParam("component") String component) {
        try {
            return ConnectorCompatibilityPacks.componentSummary(component);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown external stack component profile summary: " + component);
        }
    }

    @GET
    @Path("cutover/readiness")
    @Operation(summary = "Repo-local external stack lab cutover readiness")
    public ExternalStackCutoverReadinessReport cutoverReadiness() {
        var catalog = catalogHealth();
        var componentSummaries = componentProfileSummary().components().values();
        var blockers = new java.util.ArrayList<String>();
        blockers.addAll(catalog.failures());
        componentSummaries.stream()
            .filter(summary -> SEVERITY_BLOCKED.equals(summary.readinessSeverity()))
            .map(summary -> "component " + summary.component() + " has no production-supported profile")
            .forEach(blockers::add);

        var warnings = new java.util.ArrayList<String>();
        warnings.addAll(catalog.warnings());
        componentSummaries.stream()
            .filter(summary -> SEVERITY_WARNING.equals(summary.readinessSeverity()))
            .map(summary -> "component " + summary.component() + ": " + summary.readinessWarning())
            .forEach(warnings::add);

        var remediation = new java.util.ArrayList<String>();
        remediation.addAll(catalog.remediationActions());
        componentSummaries.forEach(summary -> remediation.addAll(summary.remediationActions()));
        var severity = cutoverSeverity(blockers, warnings);
        return new ExternalStackCutoverReadinessReport(
            blockers.isEmpty(),
            severity,
            "repo-local-lab",
            ".\\scripts\\smoke-external-stack-lab-cutover.ps1 -ApiBase http://127.0.0.1:18080/api",
            blockers.size(),
            warnings.size(),
            blockers,
            warnings,
            remediation.stream().distinct().toList()
        );
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
    @Path("preflight/manifests/report")
    @Operation(summary = "Explain external stack desired manifest validation by component")
    public ExternalStackManifestPreflightReport preflightManifestReport(ExternalStackManifestPreflightRequest request) {
        return ExternalStackManifestValidator.report(request != null ? request.manifests() : List.of());
    }

    @POST
    @Path("preflight/profile")
    @Operation(summary = "Validate one external stack connector profile for production use")
    public ValidationResult preflightProfile(ExternalStackProfilePreflightRequest request) {
        var report = preflightProfileReport(request);
        return new ValidationResult(
            report.passed(),
            report.failures(),
            report.missingPromotionEvidence().stream()
                .map(evidence -> "missing promotion evidence: " + evidence)
                .toList(),
            true,
            Map.of(
                "component", report.component() != null ? report.component() : "",
                "lifecycle_status", report.lifecycleStatus() != null ? report.lifecycleStatus() : "",
                "severity", report.severity()
            )
        );
    }

    @POST
    @Path("preflight/profile/report")
    @Operation(summary = "Explain external stack connector profile evidence readiness")
    public ExternalStackProfilePreflightReport preflightProfileReport(ExternalStackProfilePreflightRequest request) {
        var profileId = request != null ? request.profileId() : null;
        if (profileId == null || profileId.isBlank()) {
            return new ExternalStackProfilePreflightReport(
                false,
                SEVERITY_BLOCKED,
                null,
                null,
                null,
                0,
                0,
                List.of("profile_id is required"),
                List.of(),
                List.of(),
                List.of()
            );
        }
        var pack = compatibilityPack(profileId);
        var failures = pack.supported()
            ? List.<String>of()
            : List.of("profile " + profileId + " is not production-supported");
        var evidence = request.evidence() != null ? request.evidence() : List.<String>of();
        var missingEvidence = pack.promotionEvidence().stream()
            .filter(required -> !evidence.contains(required))
            .toList();
        return new ExternalStackProfilePreflightReport(
            failures.isEmpty(),
            severityForProfile(failures, missingEvidence, pack.unsupportedModes()),
            profileId,
            pack.component(),
            pack.lifecycleStatus().code(),
            missingEvidence.size(),
            pack.unsupportedModes().size(),
            failures,
            missingEvidence,
            pack.unsupportedModes(),
            profileRemediationActions(profileId, failures, missingEvidence, pack.unsupportedModes())
        );
    }

    private static List<String> profileRemediationActions(
        String profileId,
        List<String> failures,
        List<String> missingEvidence,
        List<String> unsupportedModes
    ) {
        var actions = new java.util.ArrayList<String>();
        if (!failures.isEmpty()) {
            actions.add(profileId + ": use a supported production profile");
        }
        missingEvidence.forEach(evidence ->
            actions.add(profileId + ": attach promotion evidence " + evidence));
        unsupportedModes.forEach(mode ->
            actions.add(profileId + ": remove unsupported mode " + mode));
        return actions.stream().distinct().toList();
    }

    private static String severityForProfile(
        List<String> failures,
        List<String> missingEvidence,
        List<String> unsupportedModes
    ) {
        if (!failures.isEmpty()) {
            return SEVERITY_BLOCKED;
        }
        if (!missingEvidence.isEmpty() || !unsupportedModes.isEmpty()) {
            return SEVERITY_WARNING;
        }
        return SEVERITY_OK;
    }

    private static String cutoverSeverity(List<String> blockers, List<String> warnings) {
        if (!blockers.isEmpty()) {
            return SEVERITY_BLOCKED;
        }
        if (!warnings.isEmpty()) {
            return SEVERITY_WARNING;
        }
        return SEVERITY_OK;
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
