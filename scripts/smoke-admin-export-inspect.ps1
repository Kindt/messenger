# GET admin export attachments + manifest + json parts (finished job).
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$JobId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [switch]$RequireSuccess
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeApi.ps1")
. (Join-Path $scriptDir "lib\SmokeExportInspect.ps1")

$ok = @("export_v1", "stub_written")
$hdr = New-KorusAuthHeaders -Token (Get-KorusApiToken -BaseUrl $BaseUrl -User $AdminUser -Pass $AdminPass)

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
    if ($RequireSuccess) { throw "Job $JobId status=$status" }
    Write-Host "[SKIP] job $JobId status=$status" -ForegroundColor Yellow
    exit 0
}

Invoke-ExportArtifactsInspect -BaseUrl $BaseUrl -Headers $hdr -ChatId $ChatId -JobId $JobId | Out-Null
Write-Host "[OK] export inspect complete (job $JobId)" -ForegroundColor Green
