package com.avandocmsg.messenger.api.admin.ui;

import com.avandocmsg.messenger.api.admin.ui.dto.AdminExportComplianceGuideResponse;

import java.util.List;

/** Copy-paste smoke commands for operators (admin UI export compliance panel). */
final class AdminExportSmokeHints {

    private AdminExportSmokeHints() {
    }

    static List<AdminExportComplianceGuideResponse.SmokeCommandHint> commands() {
        return List.of(
            hint(
                "Stack + export overlays",
                ".\\scripts\\full-stack-up.ps1 -ExportSmoke -ExportAutoQueue",
                "./scripts/full-stack-up.sh --export-smoke --export-auto-queue"),
            hint(
                "Admin UI (no CLI)",
                "Admin -> Export compliance: seed+file / compliance flow / seed+prepare",
                "Admin -> Export compliance: seed+file / compliance flow / seed+prepare"),
            hint(
                "Compliance pack (auto chat)",
                ".\\scripts\\smoke-export-compliance-pack.ps1",
                "./scripts/smoke-export-compliance-pack.sh"),
            hint(
                "Stack up + pack + down",
                ".\\scripts\\smoke-export-compliance-stack.ps1 -AutoQueue -Down",
                "./scripts/smoke-export-compliance-stack.sh"),
            hint(
                "Admin export-compliance-prep",
                ".\\scripts\\smoke-admin-export-compliance-prep.ps1",
                "./scripts/smoke-admin-export-compliance-prep.sh"),
            hint(
                "OpenAPI lists export-compliance-prep",
                ".\\scripts\\smoke-openapi-export-compliance.ps1",
                "./scripts/smoke-openapi-export-compliance.sh"),
            hint(
                "Compliance flow (prep+suggest+poll+download+inspect)",
                ".\\scripts\\smoke-export-compliance-flow.ps1",
                "./scripts/smoke-export-compliance-flow.sh"),
            hint(
                "Compliance flow + file (prep include_file)",
                ".\\scripts\\smoke-export-compliance-flow.ps1 -IncludeFile",
                "./scripts/smoke-export-compliance-flow.sh --include-file"),
            hint(
                "Admin export inspect (attachments/manifest/json)",
                ".\\scripts\\smoke-admin-export-inspect.ps1 -ChatId <uuid> -JobId <uuid> -RequireSuccess",
                "./scripts/smoke-admin-export-inspect.sh --chat-id UUID --job-id UUID --require-success"),
            hint(
                "Compliance flow + file attachment",
                ".\\scripts\\smoke-export-compliance-with-file-flow.ps1",
                "./scripts/smoke-export-compliance-with-file-flow.sh"),
            hint(
                "Admin export download (latest job)",
                ".\\scripts\\smoke-admin-export-download.ps1 -ChatId <uuid> -RequireSuccess",
                "./scripts/smoke-admin-export-download.sh --chat-id UUID --require-success"),
            hint(
                "Retention NATS path (seed + pass)",
                ".\\scripts\\smoke-retention-export-suggested.ps1 -Seed -CreateGroup -Prepare -IncludeFile",
                "./scripts/smoke-retention-export-suggested.sh --seed --create-group --prepare --include-file"),
            hint(
                "Prometheus metrics smoke",
                ".\\scripts\\smoke-export-observability.ps1",
                "./scripts/smoke-export-observability.sh"),
            hint(
                "Observability (Prometheus/Grafana)",
                "docker compose -f deploy/observability/docker-compose.observability.yml up -d",
                "docker compose -f deploy/observability/docker-compose.observability.yml up -d"),
            hint(
                "Teardown (same overlays as up)",
                ".\\scripts\\full-stack-down.ps1 -ExportSmoke -ExportAutoQueue",
                "./scripts/full-stack-down.sh --export-smoke --export-auto-queue"));
    }

    private static AdminExportComplianceGuideResponse.SmokeCommandHint hint(
        String title,
        String commandPs,
        String commandSh
    ) {
        return new AdminExportComplianceGuideResponse.SmokeCommandHint(title, commandPs, commandSh);
    }
}
