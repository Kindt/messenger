# E2E: export-suggest (local) -> enqueue export -> cancel -> verify audits.
# Needs: EXPORT_ADMIN_SUGGEST_ENABLED, EXPORT_ADMIN_EXPORT_ENABLED (if no auto-queue).
# Optional: EXPORT_AUTO_QUEUE_ON_SUGGESTED=true (otherwise script POSTs admin export).
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [ValidateSet("queued", "processing", "any")]
    [string]$CancelMode = "any",
    [int]$PollSeconds = 120,
    [int]$PollIntervalSec = 1,
    [switch]$SkipAudit
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeExportAudit.ps1")

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

function Test-CancelTargetStatus {
    param([string]$Status)
    switch ($CancelMode) {
        "queued" { return $Status -eq "queued" }
        "processing" { return $Status -eq "processing" }
        default { return $Status -in @("queued", "processing") }
    }
}

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }

Write-Host "POST export-suggest (local) ..." -ForegroundColor Cyan
try {
    $suggest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export-suggest" -Method Post `
        -Headers $hdr -ContentType "application/json" `
        -Body (@{
            dispatch = "local"
            candidate_message_count = 2
            reason = "hot_body_candidates"
        } | ConvertTo-Json)
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "[FAIL] EXPORT_ADMIN_SUGGEST_ENABLED?" -ForegroundColor Red
    }
    throw
}
Write-Host "[OK] suggest dispatch=$($suggest.dispatch)" -ForegroundColor Green

$auditSuggest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/audit-events?action=export.suggested&resource_id=$ChatId&limit=5" -Headers $hdr
if (-not $auditSuggest -or @($auditSuggest).Count -lt 1) {
    throw "No export.suggested audit for chat $ChatId"
}
Write-Host "[OK] audit export.suggested" -ForegroundColor Green

$jobId = $suggest.auto_queued_job_id
if (-not $jobId) { $jobId = $suggest.autoQueuedJobId }

if (-not $jobId) {
    Write-Host "No auto_queued_job_id — POST admin export ..." -ForegroundColor Yellow
    $accepted = Invoke-WebRequest -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export" -Method Post `
        -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
    if ($accepted.StatusCode -ne 202) { throw "admin export POST failed" }
    $jobId = ($accepted.Content | ConvertFrom-Json).job_id
    if (-not $jobId) { $jobId = ($accepted.Content | ConvertFrom-Json).jobId }
}

if (-not $jobId) { throw "No job_id after suggest/export" }
Write-Host "[OK] job_id=$jobId" -ForegroundColor Green

Test-ExportRequestedAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -SkipAudit:$SkipAudit

$statusUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId/status"
$cancelUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId"
$deadline = (Get-Date).AddSeconds($PollSeconds)
$cancelled = $false

while ((Get-Date) -lt $deadline) {
    $st = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($st.status)" -ForegroundColor DarkGray
    if (Test-CancelTargetStatus -Status $st.status) {
        Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete | Out-Null
        $cancelled = $true
        break
    }
    if ($st.status -eq "export_cancelled") {
        $cancelled = $true
        break
    }
    if ($st.status -in @("export_v1", "stub_written")) {
        Write-Host "[WARN] Job finished before cancel" -ForegroundColor Yellow
        exit 0
    }
    Start-Sleep -Seconds $PollIntervalSec
}

if (-not $cancelled) {
    Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete | Out-Null
}

$final = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
if ($final.status -ne "export_cancelled") {
    throw "Expected export_cancelled, got $($final.status)"
}
Write-Host "[OK] export_cancelled" -ForegroundColor Green

Test-ExportCancelAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -Action "export.admin_cancelled" -SkipAudit:$SkipAudit
Write-Host "[OK] suggest -> export -> cancel complete" -ForegroundColor Green
