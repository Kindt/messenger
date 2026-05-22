# User API: POST export -> cancel (queued/processing) -> export_cancelled.
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [ValidateSet("queued", "processing", "any")]
    [string]$Mode = "any",
    [int]$PollSeconds = 120,
    [int]$PollIntervalSec = 1,
    [switch]$RequireProcessing,
    [switch]$SkipAudit
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\SmokeExportAudit.ps1")

function Get-Token {
    param($U, $P)
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $U; password = $P } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No token for $U" }
    return $t
}

function Test-CancelTargetStatus {
    param([string]$Status)
    switch ($Mode) {
        "queued" { return $Status -eq "queued" }
        "processing" { return $Status -eq "processing" }
        default { return $Status -in @("queued", "processing") }
    }
}

$hdr = @{ Authorization = "Bearer $(Get-Token -U $User -P $Pass)" }
$exportUri = "$BaseUrl/api/v1/chats/$ChatId/export"

Write-Host "POST user export (mode=$Mode) ..." -ForegroundColor Cyan
try {
    $accepted = Invoke-WebRequest -Uri $exportUri -Method Post -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "[FAIL] Need owner/admin on chat" -ForegroundColor Red
    }
    throw
}
if ($accepted.StatusCode -ne 202) { throw "Expected 202" }
$jobId = ($accepted.Content | ConvertFrom-Json).job_id
if (-not $jobId) { $jobId = ($accepted.Content | ConvertFrom-Json).jobId }
Write-Host "[OK] job_id=$jobId" -ForegroundColor Green

$statusUri = "$exportUri/$jobId"
$cancelUri = $statusUri
$deadline = (Get-Date).AddSeconds($PollSeconds)
$cancelled = $false
$sawProcessing = $false

while ((Get-Date) -lt $deadline) {
    $st = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($st.status)" -ForegroundColor DarkGray
    if ($st.status -eq "processing") { $sawProcessing = $true }
    if (Test-CancelTargetStatus -Status $st.status) {
        Write-Host "DELETE cancel ..." -ForegroundColor Cyan
        $cancel = Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete
        if ($cancel.status -ne "export_cancelled" -and -not $cancel.cancelled) {
            throw "Unexpected cancel: $($cancel | ConvertTo-Json -Compress)"
        }
        $cancelled = $true
        break
    }
    if ($st.status -eq "export_cancelled") {
        $cancelled = $true
        break
    }
    if ($st.status -in @("export_v1", "stub_written")) {
        if ($Mode -eq "processing" -and $RequireProcessing) {
            throw "Finished as $($st.status) before processing"
        }
        Write-Host "[WARN] Job finished before cancel" -ForegroundColor Yellow
        exit 0
    }
    Start-Sleep -Seconds $PollIntervalSec
}

if (-not $cancelled) {
    Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete | Out-Null
}

if ($Mode -eq "processing" -and $RequireProcessing -and -not $sawProcessing) {
    throw "Never observed processing"
}

$final = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
if ($final.status -ne "export_cancelled") {
    throw "Expected export_cancelled, got $($final.status)"
}
Write-Host "[OK] export_cancelled (user API)" -ForegroundColor Green

Test-ExportCancelAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -Action "export.cancelled" -SkipAudit:$SkipAudit
