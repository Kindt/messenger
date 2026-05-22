# GET admin export bundle for latest finished job (or explicit job_id).
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$JobId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [ValidateSet("bundle", "json", "manifest")]
    [string]$Part = "bundle",
    [switch]$RequireSuccess
)
$ErrorActionPreference = "Stop"

function Get-Token {
    param($User, $Pass)
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No token for $User" }
    return $t
}

$ok = @("export_v1", "stub_written")
$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }

if (-not $JobId) {
    Write-Host "GET latest export status ..." -ForegroundColor Cyan
    $latest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/latest/status" -Headers $hdr
    $JobId = $latest.job_id
    if (-not $JobId) { $JobId = $latest.jobId }
    $status = $latest.status
} else {
    $st = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/$JobId/status" -Headers $hdr
    $status = $st.status
}

if (-not $JobId) {
    if ($RequireSuccess) { throw "No export job for chat $ChatId" }
    Write-Host "[SKIP] no export job" -ForegroundColor Yellow
    exit 0
}

if ($ok -notcontains $status) {
    if ($RequireSuccess) { throw "Job $JobId status=$status (need export_v1 or stub_written)" }
    Write-Host "[SKIP] job $JobId status=$status" -ForegroundColor Yellow
    exit 0
}

$dl = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$JobId/download?part=$Part"
Write-Host "GET download part=$Part job=$JobId ..." -ForegroundColor Cyan
$dlRes = Invoke-WebRequest -Uri $dl -Headers $hdr -Method Get -UseBasicParsing
if ($dlRes.StatusCode -ne 200) {
    throw "Download failed: $($dlRes.StatusCode)"
}
$bytes = $dlRes.RawContentLength
if (-not $bytes -and $dlRes.Content) { $bytes = $dlRes.Content.Length }
Write-Host "[OK] download: $bytes bytes (status=$status)" -ForegroundColor Green
