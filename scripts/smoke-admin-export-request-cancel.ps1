# Admin: POST export -> audit export.requested -> cancel -> audit export.admin_cancelled.
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

Write-Host "POST admin export ..." -ForegroundColor Cyan
$accepted = Invoke-WebRequest -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export" -Method Post `
    -Headers $hdr -ContentType "application/json" -Body "{}" -UseBasicParsing
if ($accepted.StatusCode -ne 202) { throw "Expected 202" }
$jobId = ($accepted.Content | ConvertFrom-Json).job_id
if (-not $jobId) { $jobId = ($accepted.Content | ConvertFrom-Json).jobId }
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
        Write-Host "DELETE cancel ..." -ForegroundColor Cyan
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

Write-Host "[OK] request -> cancel flow complete" -ForegroundColor Green
