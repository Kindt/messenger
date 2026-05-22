# User API: POST export -> audit export.requested -> cancel -> audit export.cancelled.
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

Write-Host "POST user export ..." -ForegroundColor Cyan
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

Test-ExportRequestedAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -SkipAudit:$SkipAudit

$statusUri = "$exportUri/$jobId"
$deadline = (Get-Date).AddSeconds($PollSeconds)
$cancelled = $false

while ((Get-Date) -lt $deadline) {
    $st = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
    Write-Host "  status=$($st.status)" -ForegroundColor DarkGray
    if (Test-CancelTargetStatus -Status $st.status) {
        Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Delete | Out-Null
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
    Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Delete | Out-Null
}

$final = Invoke-RestMethod -Uri $statusUri -Headers $hdr -Method Get
if ($final.status -ne "export_cancelled") {
    throw "Expected export_cancelled, got $($final.status)"
}
Write-Host "[OK] export_cancelled" -ForegroundColor Green

Test-ExportCancelAudit -BaseUrl $BaseUrl -Headers $hdr -JobId $jobId -Action "export.cancelled" -SkipAudit:$SkipAudit
Write-Host "[OK] user request -> cancel flow complete" -ForegroundColor Green
