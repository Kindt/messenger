package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminExportComplianceGuideResponse;
import com.avandocmsg.messenger.api.admin.ui.dto.AdminManifestResponse;
import com.avandocmsg.messenger.api.admin.ui.dto.AdminServerStatsResponse;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.common.export.ExportGdprDisclosures;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/v1/admin/ui")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin UI", description = "Встроенная админ-консоль: manifest разделов и данные панелей")
@RolesAllowed("admin")
public class AdminUiResource {

    private final AdminUiManifest manifest;
    private final AdminStatsPort stats;
    private final AppConfig appConfig;

    @Inject
    public AdminUiResource(AdminUiManifest manifest, AdminStatsPort stats, AppConfig appConfig) {
        this.manifest = manifest;
        this.stats = stats;
        this.appConfig = appConfig;
    }

    @GET
    @Path("manifest")
    @Operation(summary = "Разделы встроенной админ-консоли",
        description = "Список собран из подключённых модулей (SPI). При отключении модуля с classpath его разделы исчезают после перезапуска.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = AdminManifestResponse.class)))
    public Response manifest() {
        return Response.ok(new AdminManifestResponse(manifest.sections(), appConfig.version())).build();
    }

    @GET
    @Path("stats")
    @Operation(summary = "Статистика сервера (ядро)",
        description = "JVM, зависимости и приблизительные счётчики строк в Hot DB.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = AdminServerStatsResponse.class)))
    public AdminServerStatsResponse stats() {
        return stats.snapshot();
    }

    @GET
    @Path("export-compliance-guide")
    @Operation(summary = "Справочник export / GDPR для операторов",
        description = "Шаблон gdprDisclosures (как в export.json), чеклист env и текущие счётчики export_jobs / аудита.",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = AdminExportComplianceGuideResponse.class)))
    public AdminExportComplianceGuideResponse exportComplianceGuide() {
        var policy = new AdminExportComplianceGuideResponse.CompletenessPolicy(
            List.copyOf(appConfig.exportRequiredFields()),
            appConfig.exportCompletenessStrict());
        return new AdminExportComplianceGuideResponse(
            ExportGdprDisclosures.referenceTemplate(),
            AdminExportEnvChecklist.items(),
            AdminExportSmokeHints.commands(),
            stats.snapshot().exportCompliance(),
            policy);
    }
}
