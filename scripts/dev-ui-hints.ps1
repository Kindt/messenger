# Prints dev URLs and test logins (realm avandocmsg). Run: .\scripts\dev-ui-hints.ps1
# Help: .\scripts\dev-ui-hints.ps1 -Help
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$LanIp = "",
    [string]$ServerLanIp = "",
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\dev-ui-hints.ps1 [-RepoRoot <path>] [-LanIp <web-host-ip>] [-ServerLanIp <api-host-ip>] [-Help]"
    Write-Host "  Default repo root: parent of scripts/. Linux/macOS: ./scripts/dev-ui-hints.sh --help"
    exit 0
}

function Read-KorusWebLbPort([string]$Root) {
    $p = Join-Path $Root "korus-web\.env"
    if (-not (Test-Path $p)) { return 9088 }
    foreach ($line in Get-Content $p) {
        if ($line -match '^\s*KORUS_WEB_LB_PORT\s*=\s*(\d+)\s*$') { return [int]$Matches[1] }
    }
    return 9088
}

$lb = Read-KorusWebLbPort $RepoRoot
$webHost = if ($LanIp) { $LanIp } else { "localhost" }
$apiHost = if ($ServerLanIp) { $ServerLanIp } else { "localhost" }
$ui = "http://${webHost}:$lb/"

Write-Host ""
Write-Host "=== Korus Messenger (dev) - URLs and logins ===" -ForegroundColor Cyan
Write-Host "Web client (korus-web lb):           $ui"
if ($LanIp -or $ServerLanIp) {
    Write-Host "  (LAN: web=$webHost api/ws host=$apiHost)"
}
Write-Host "Built-in admin console:              http://${apiHost}:8080/admin/"
Write-Host "Core API health:                     http://${apiHost}:8080/api/v1/health"
Write-Host "Keycloak admin console (IdP):      http://${apiHost}:8081   admin / admin  (KEYCLOAK_ADMIN)"
Write-Host "ws-gateway with profile web (host): ws://${apiHost}:8082/ws"
Write-Host ""
Write-Host "Login for UI / API / admin console (realm avandocmsg):" -ForegroundColor Yellow
Write-Host "  admin   / admin"
Write-Host "  csadmin / csadmin"
Write-Host "  (Keycloak 24+ needs email on user; if login fails run: .\scripts\keycloak-ensure-dev-users.ps1)"
Write-Host ""
Write-Host "No regular non-admin user in import - use Register tab in UI or API register."
Write-Host ""
Write-Host "Export / compliance (full-server + overlays):" -ForegroundColor Yellow
Write-Host "  core-api metrics:    http://${apiHost}:8080/api/v1/metrics/prometheus"
Write-Host "  export-replay:       http://${apiHost}:9193/metrics  (health :9193/health)"
Write-Host "  retention:           http://${apiHost}:9192/metrics"
Write-Host "  Admin -> Export compliance: seed+file / compliance flow / guide"
Write-Host "  POST /api/v1/admin/export-compliance-prep  { include_file: true }"
Write-Host "  .\scripts\full-stack-up.ps1 -ExportSmoke -ExportAutoQueue"
Write-Host "  .\scripts\smoke-export-compliance-flow.ps1 -IncludeFile"
Write-Host "  .\scripts\smoke-openapi-export-compliance.ps1"
Write-Host "  .\scripts\smoke-export-compliance-pack.ps1"
Write-Host "  .\scripts\smoke-export-compliance-stack.ps1 -AutoQueue -Down"
Write-Host "  CI: Actions -> Export compliance smoke -> Run workflow"
Write-Host ""
