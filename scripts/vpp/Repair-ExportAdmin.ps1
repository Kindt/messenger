# Enable addon-export + export admin APIs on QEMU server guest (VPP addon-export smoke).
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host 'Repair: full regression addons + export admin flags on server guest...' -ForegroundColor Cyan
& (Join-Path $Root 'scripts\qemu-enable-regression-addons.ps1')
if ($LASTEXITCODE -ne 0) { throw 'qemu-enable-regression-addons failed' }

& (Join-Path $Root 'deploy\qemu\run\wait-api-health.ps1') -MaxMinutes 5
if ($LASTEXITCODE -ne 0) { throw 'API not ready after export repair' }

$login = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/auth/login' -Method Post `
    -Body (@{ username = 'csadmin'; password = 'csadmin' } | ConvertTo-Json) `
    -ContentType 'application/json; charset=utf-8'
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
$hdr = @{ Authorization = "Bearer $token" }

$cap = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/platform/capabilities'
if (-not $cap.modules.'addon-export'.selected) {
    throw 'addon-export still not selected after regression addons enable'
}

try {
    Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/admin/export-compliance-prep' -Method Post `
        -Headers $hdr -ContentType 'application/json; charset=utf-8' `
        -Body (@{ message_count = 1; create_group = $true } | ConvertTo-Json) | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        throw 'export-compliance-prep still 404 (EXPORT_ADMIN_SUGGEST_ENABLED?)'
    }
    throw
}
Write-Host '[OK] addon-export selected, export-compliance-prep reachable on :18080' -ForegroundColor Green
