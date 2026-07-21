# Smoke: hot-row purge status (admin API). QEMU lab: pass -BaseUrl http://127.0.0.1:18080
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [string]$AdminToken = ""
)

$ErrorActionPreference = "Stop"
$root = $BaseUrl.TrimEnd('/')

if (-not $AdminToken) {
    $login = Invoke-RestMethod -Uri "$root/api/v1/auth/login" -Method Post `
        -Body (@{ username = $AdminUser; password = $AdminPass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $AdminToken = $login.access_token
    if (-not $AdminToken) { $AdminToken = $login.accessToken }
}
if (-not $AdminToken) { throw "No admin token (login failed)" }

$headers = @{ Authorization = "Bearer $AdminToken" }
$status = Invoke-RestMethod -Uri "$root/api/v1/admin/purge/status" -Headers $headers -Method GET
$total = $status.total_purged
if ($null -eq $total) { $total = $status.totalPurged }
if ($null -eq $total) { throw "purge/status missing total_purged" }
$pending = $status.pending_count
if ($null -eq $pending) { $pending = $status.pendingCount }
Write-Host "purge/status total_purged=$total pending=$pending" -ForegroundColor Green
Write-Host "[OK] smoke-retention-purge (purge status endpoint)" -ForegroundColor Green
