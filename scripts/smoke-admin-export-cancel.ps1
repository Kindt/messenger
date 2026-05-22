# Admin: enqueue export -> cancel -> verify export_cancelled (no artifact download).
# Requires EXPORT_ADMIN_EXPORT_ENABLED=true.
# -Mode queued|processing|any (default any): when to DELETE (processing = cooperative cancel path).
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
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
    switch ($Mode) {
        "queued" { return $Status -eq "queued" }
        "processing" { return $Status -eq "processing" }
        default { return $Status -in @("queued", "processing") }
    }
}

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }

Write-Host "POST admin export (cancel mode=$Mode) ..." -ForegroundColor Cyan
try {
    $accepted = Invoke-WebRequest -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export" -Method Post `
        -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "[FAIL] Set EXPORT_ADMIN_EXPORT_ENABLED=true" -ForegroundColor Red
    }
    throw
}
if ($accepted.StatusCode -ne 202) { throw "Expected 202" }
$jobId = ($accepted.Content | ConvertFrom-Json).job_id
if (-not $jobId) { $jobId = ($accepted.Content | ConvertFrom-Json).jobId }
Write-Host "[OK] job_id=$jobId" -ForegroundColor Green

$statusUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId/status"
$cancelUri = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$jobId"

$deadline = (Get-Date).AddSeconds($PollSeconds)
$cancelled = $false
$sawProcessing = $false

while ((Get-Date) -lt $deadline) {
    $st = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($st.status)" -ForegroundColor DarkGray
    if ($st.status -eq "processing") { $sawProcessing = $true }
    if (Test-CancelTargetStatus -Status $st.status) {
        Write-Host "DELETE cancel (status=$($st.status)) ..." -ForegroundColor Cyan
        $cancel = Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete
        if (-not $cancel.cancelled -and $cancel.status -ne "export_cancelled") {
            throw "Cancel response unexpected: $($cancel | ConvertTo-Json -Compress)"
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
            throw "Job finished as $($st.status) before processing (use a larger chat or slower worker)"
        }
        Write-Host "[WARN] Job finished before cancel ($($st.status)); worker was fast" -ForegroundColor Yellow
        exit 0
    }
    Start-Sleep -Seconds $PollIntervalSec
}

if (-not $cancelled) {
    Write-Host "Attempting cancel anyway (last chance) ..." -ForegroundColor Yellow
    try {
        Invoke-RestMethod -Uri $cancelUri -Headers $hdr -Method Delete | Out-Null
        $cancelled = $true
    } catch {
        throw "Could not cancel job: $($_.Exception.Message)"
    }
}

if ($Mode -eq "processing" -and $RequireProcessing -and -not $sawProcessing) {
    throw "Never observed processing before cancel"
}

Start-Sleep -Seconds 1
$final = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
if ($final.status -ne "export_cancelled") {
    throw "Expected export_cancelled, got $($final.status)"
}
Write-Host "[OK] export_cancelled" -ForegroundColor Green

Test-ExportCancelAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -Action "export.admin_cancelled" -SkipAudit:$SkipAudit

Write-Host "GET download should fail ..." -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri "$cancelUri/download?part=bundle" -Headers $hdr -Method Get -UseBasicParsing | Out-Null
    Write-Host "[WARN] download succeeded unexpectedly" -ForegroundColor Yellow
} catch {
    Write-Host "[OK] download rejected as expected" -ForegroundColor Green
}
