# Prints dev URLs and test logins (realm avandocmsg). Run: .\scripts\dev-ui-hints.ps1
# Help: .\scripts\dev-ui-hints.ps1 -Help
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\dev-ui-hints.ps1 [-RepoRoot <path>] [-Help]"
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
$ui = "http://localhost:$lb/"

Write-Host ""
Write-Host "=== Korus Messenger (dev) - URLs and logins ===" -ForegroundColor Cyan
Write-Host "Web client (korus-web lb):           $ui"
Write-Host "Built-in admin console:              http://localhost:8080/admin/"
Write-Host "Core API health:                     http://localhost:8080/api/v1/health"
Write-Host "Keycloak admin console (IdP):      http://localhost:8081   admin / admin  (KEYCLOAK_ADMIN)"
Write-Host "ws-gateway with profile web (host): ws://localhost:8082/ws"
Write-Host ""
Write-Host "Login for UI / API (realm avandocmsg, keycloak/avandocmsg-realm.json):" -ForegroundColor Yellow
Write-Host "  admin   / admin"
Write-Host "  csadmin / csadmin"
Write-Host ""
Write-Host "No regular non-admin user in import - use Register tab in UI or API register."
Write-Host ""
