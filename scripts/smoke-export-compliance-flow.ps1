# Admin export compliance flow (Windows). CI canonical: smoke-export-compliance-flow.sh.
# Requires EXPORT_ADMIN_SUGGEST_ENABLED (+ EXPORT_ADMIN_EXPORT_ENABLED if no auto-queue).
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [ValidateSet("local", "nats", "both")]
    [string]$Dispatch = "local",
    [int]$PollSeconds = 120,
    [int]$PollIntervalSec = 2,
    [switch]$SkipPrep,
    [switch]$IncludeFile,
    [string]$FileName = "compliance-smoke.txt",
    [switch]$SkipDownload,
    [switch]$SkipInspect
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeExportInspect.ps1")
. (Join-Path $scriptDir "lib\SmokeMessaging.ps1")

function Get-Token {
    param($User, $Pass)
    return Get-SmokeApiToken -BaseUrl $BaseUrl -User $User -Pass $Pass -WaitForRateLimit -RateLimitWaitSec 180
}

$terminal = @("export_v1", "stub_written", "export_failed", "export_cancelled")
$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }

if (-not $ChatId -and -not $SkipPrep) {
    Write-Host "POST export-compliance-prep ..." -ForegroundColor Cyan
    $prepArgs = @{ BaseUrl = $BaseUrl }
    if ($IncludeFile) {
        $prepArgs["IncludeFile"] = $true
        $prepArgs["FileName"] = $FileName
    }
    $prepOut = & "$scriptDir\smoke-admin-export-compliance-prep.ps1" @prepArgs
    $line = $prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1
    if ($line) { $ChatId = ($line -replace '^CHAT_ID=', '').Trim() }
    if (-not $ChatId) { throw "prep did not return chat id" }
    Start-Sleep -Seconds 2
}

if (-not $ChatId) { throw "ChatId required (or omit for auto prep)" }
Write-Host "Using chat $ChatId" -ForegroundColor Green

Write-Host "POST export-suggest ($Dispatch) ..." -ForegroundColor Cyan
$suggest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export-suggest" -Method Post `
    -Headers $hdr -ContentType "application/json" `
    -Body (@{
        dispatch = $Dispatch
        candidate_message_count = 3
        reason = "hot_body_candidates"
    } | ConvertTo-Json)

$jobId = $suggest.auto_queued_job_id
if (-not $jobId) { $jobId = $suggest.autoQueuedJobId }

if (-not $jobId) {
    Write-Host "POST admin export ..." -ForegroundColor Cyan
    $accepted = Invoke-WebRequest -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export" -Method Post `
        -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
    if ($accepted.StatusCode -ne 202) { throw "admin export failed" }
    $body = $accepted.Content | ConvertFrom-Json
    $jobId = $body.job_id
    if (-not $jobId) { $jobId = $body.jobId }
}

if (-not $jobId) { throw "No job_id" }
Write-Host "[OK] job_id=$jobId" -ForegroundColor Green

$statusUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId/status"
$deadline = (Get-Date).AddSeconds($PollSeconds)
$final = $null

while ((Get-Date) -lt $deadline) {
    $final = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($final.status)" -ForegroundColor DarkGray
    if ($terminal -contains $final.status) { break }
    Start-Sleep -Seconds $PollIntervalSec
}

if (-not $final -or $terminal -notcontains $final.status) {
    throw "Poll timeout (last=$($final.status))"
}

if ($final.status -eq "export_failed") {
    throw "Export failed"
}

if ($final.status -in @("export_v1", "stub_written")) {
    if (-not $SkipDownload) {
        & "$scriptDir\smoke-admin-export-download.ps1" -ChatId $ChatId -JobId $jobId -BaseUrl $BaseUrl `
            -AdminUser $AdminUser -AdminPass $AdminPass -RequireSuccess
    }
    if (-not $SkipInspect) {
        $inspectArgs = @{
            BaseUrl = $BaseUrl
            Headers = $hdr
            ChatId  = $ChatId
            JobId   = $jobId
        }
        if ($IncludeFile) { $inspectArgs["VerifyBinary"] = $true }
        Invoke-ExportArtifactsInspect @inspectArgs | Out-Null
    }
}

Write-Host "[OK] compliance flow finished: $($final.status)" -ForegroundColor Green
