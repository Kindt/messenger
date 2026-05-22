# Smoke: verify export.suggested / export.auto_queued audit trail (after retention batch or manual NATS publish).
# Does not publish NATS itself — run retention with RETENTION_PUBLISH_EXPORT_SUGGESTED=true first.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$ChatId = "",
    [int]$Limit = 20
)
$ErrorActionPreference = "Stop"

$loginUri = "$BaseUrl/api/v1/auth/login"
Write-Host "POST $loginUri (admin)..." -ForegroundColor Cyan
$loginBody = @{ username = $User; password = $Pass } | ConvertTo-Json
$login = Invoke-RestMethod -Uri $loginUri -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { throw "No access token" }
$headers = @{ Authorization = "Bearer $token" }

function Get-AuditEvents {
    param([string]$Action, [string]$ResourceId)
    $q = "limit=$Limit&action=$([uri]::EscapeDataString($Action))"
    if ($ResourceId) {
        $q += "&resource_id=$([uri]::EscapeDataString($ResourceId))"
        $q += "&resource_type=chat"
    }
    $uri = "$BaseUrl/api/v1/admin/audit-events?$q"
    Write-Host "GET $uri" -ForegroundColor DarkGray
    return Invoke-RestMethod -Uri $uri -Headers $headers -Method Get
}

$actions = @("export.suggested", "export.auto_queued", "export.auto_queue_skipped")
$any = $false
foreach ($a in $actions) {
    $rows = Get-AuditEvents -Action $a -ResourceId $ChatId
    $count = if ($rows) { @($rows).Count } else { 0 }
    Write-Host "  $a : $count row(s)" -ForegroundColor $(if ($count -gt 0) { "Green" } else { "DarkGray" })
    if ($count -gt 0) { $any = $true }
}

if (-not $any) {
    Write-Host "[WARN] No export suggestion audit rows. Ensure:" -ForegroundColor Yellow
    Write-Host "  - retention worker: RETENTION_PUBLISH_EXPORT_SUGGESTED=true"
    Write-Host "  - core-api: EXPORT_SUGGESTED_SUBSCRIBER_ENABLED=true (default)"
    Write-Host "  - optional auto-queue: EXPORT_AUTO_QUEUE_ON_SUGGESTED=true"
    if ($ChatId) { Write-Host "  - ChatId filter: $ChatId" }
    exit 1
}
Write-Host "[OK] export suggestion audit trail present" -ForegroundColor Green
