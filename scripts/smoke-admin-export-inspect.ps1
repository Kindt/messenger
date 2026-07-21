# GET admin export attachments + manifest + json parts (finished job).
param(
    [string]$ChatId = "",
    [string]$JobId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [switch]$RequireSuccess
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\Resolve-SmokeExportChatId.ps1")
. (Join-Path $scriptDir "lib\SmokeApi.ps1")
. (Join-Path $scriptDir "lib\SmokeExportInspect.ps1")
$ChatId = Resolve-SmokeExportChatId -ChatId $ChatId -BaseUrl $BaseUrl -ScriptDir $scriptDir

$ok = @("export_v1", "stub_written")
$hdr = New-KorusAuthHeaders -Token (Get-KorusApiToken -BaseUrl $BaseUrl -User $AdminUser -Pass $AdminPass)

if (-not $JobId) {
    Write-Host "GET latest export status ..." -ForegroundColor Cyan
    try {
        $latest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/latest/status" -Headers $hdr
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($code -ne 404) { throw }
        Write-Host "No finished export yet - enqueue admin export ..." -ForegroundColor Yellow
        & (Join-Path $scriptDir "smoke-admin-export.ps1") -ChatId $ChatId -BaseUrl $BaseUrl -SkipDownload | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "admin export failed before inspect (exit $LASTEXITCODE)" }
        $latest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/latest/status" -Headers $hdr
    }
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
