# Smoke: retention PATCH writes organization.retention.set audit row (FSTEC-04).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-admin-audit-retention.ps1 [-BaseUrl url]

PATCH /admin/organizations/{orgId}/retention and verify audit-events row.
"@
    exit 0
}

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

$api = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Uri "$api/auth/login" -Method Post `
    -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = if ($login.access_token) { $login.access_token } else { $login.accessToken }
$headers = @{ Authorization = "Bearer $token" }

$me = Invoke-RestMethod -Uri "$api/users/me" -Headers $headers
$orgId = $me.org_id
if (-not $orgId) { $orgId = $me.organization_id }
if (-not $orgId) {
    $orgs = Invoke-RestMethod -Uri "$api/admin/organizations" -Headers $headers
    if ($orgs -and $orgs.Count -gt 0) {
        $orgId = $orgs[0].id
        if (-not $orgId) { $orgId = $orgs[0].org_id }
    }
}
if (-not $orgId) { Fail "could not resolve org id" }

$before = Invoke-RestMethod -Uri "$api/admin/organizations/$orgId/retention" -Headers $headers
$targetHold = -not [bool]$before.legal_hold

$patchBody = @{
    hot_message_body_max_age_days = $before.hot_message_body_max_age_days
    hot_metadata_min_age_days     = $before.hot_metadata_min_age_days
    archive_metadata_enabled      = $before.archive_metadata_enabled
    deep_archive_enabled          = $before.deep_archive_enabled
    legal_hold                    = $targetHold
} | ConvertTo-Json

Invoke-RestMethod -Uri "$api/admin/organizations/$orgId/retention" -Method Patch -Headers $headers `
    -ContentType "application/json; charset=utf-8" -Body $patchBody | Out-Null

$events = Invoke-RestMethod -Uri "$api/admin/audit-events?limit=20&action=organization.retention.set&resource_type=organization&resource_id=$orgId" `
    -Headers $headers
if (-not $events -or ($events -is [System.Array] -and $events.Count -eq 0)) {
    Fail "no organization.retention.set audit row for org=$orgId"
}

Write-Host "[OK] audit organization.retention.set org=$orgId legal_hold=$targetHold" -ForegroundColor Green
