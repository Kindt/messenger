# Admin: POST export -> poll status -> optional download (all /admin/... endpoints).
# Requires EXPORT_ADMIN_EXPORT_ENABLED=true on core-api.
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [int]$PollSeconds = 120,
    [int]$PollIntervalSec = 2,
    [switch]$SkipDownload
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\Resolve-SmokeExportChatId.ps1")
$ChatId = Resolve-SmokeExportChatId -ChatId $ChatId -BaseUrl $BaseUrl -ScriptDir $scriptDir

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

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }

Write-Host "POST admin export chat=$ChatId ..." -ForegroundColor Cyan
try {
    $accepted = Invoke-WebRequest -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export" -Method Post `
        -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "[FAIL] admin export disabled? Set EXPORT_ADMIN_EXPORT_ENABLED=true" -ForegroundColor Red
    }
    throw
}
if ($accepted.StatusCode -ne 202) {
    throw "Expected 202, got $($accepted.StatusCode)"
}
$body = $accepted.Content | ConvertFrom-Json
$jobId = $body.job_id
if (-not $jobId) { $jobId = $body.jobId }
if (-not $jobId) { throw "No job_id" }
Write-Host "[OK] job_id=$jobId" -ForegroundColor Green

$statusUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId/status"
$terminal = @("export_v1", "stub_written", "export_failed", "export_cancelled")
$deadline = (Get-Date).AddSeconds($PollSeconds)

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $PollIntervalSec
    $st = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($st.status)" -ForegroundColor DarkGray
    if ($terminal -contains $st.status) {
        Write-Host "[OK] finished: $($st.status)" -ForegroundColor Green
        if (-not $SkipDownload) {
            $dl = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId/download?part=bundle"
            Write-Host "GET download bundle ..." -ForegroundColor Cyan
            $dlRes = Invoke-WebRequest -Uri $dl -Headers $hdr -Method Get -UseBasicParsing
            if ($dlRes.StatusCode -eq 200) {
                $bytes = $dlRes.RawContentLength
                if (-not $bytes -and $dlRes.Content) { $bytes = $dlRes.Content.Length }
                Write-Host "[OK] download: $bytes bytes" -ForegroundColor Green
            }
        }
        exit 0
    }
}

throw "Timed out waiting for job $jobId"
