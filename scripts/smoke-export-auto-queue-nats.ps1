# E2E: msg.export.suggested -> EXPORT_AUTO_QUEUE_ON_SUGGESTED -> audit export.auto_queued + queued job.
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$NatsUrl = "",
    [int]$PollSeconds = 30,
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
    if (-not $t) { throw "No token" }
    return $t
}

$hdr = @{ Authorization = "Bearer $(Get-Token -User csadmin -Pass csadmin)" }

& "$scriptDir\publish-export-suggested.ps1" -ChatId $ChatId -NatsUrl $NatsUrl

$deadline = (Get-Date).AddSeconds($PollSeconds)
$jobId = $null
while ((Get-Date) -lt $deadline) {
    if (-not $SkipAudit) {
        try {
            $jobId = Test-ExportAutoQueuedAudit -BaseUrl $BaseUrl -Headers $hdr -ChatId $ChatId
            break
        } catch {
            if ($_.Exception.Message -notmatch "No export.auto_queued") { throw }
        }
    } else {
        $list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/jobs?limit=5&status=queued" -Headers $hdr
        if ($list.jobs -and @($list.jobs).Count -gt 0) {
            $jobId = $list.jobs[0].job_id
            if (-not $jobId) { $jobId = $list.jobs[0].jobId }
            break
        }
    }
    Start-Sleep -Seconds $PollIntervalSec
}
if (-not $jobId) {
    throw "Timed out waiting for auto-queue (run export-smoke-stack-up.ps1 -AutoQueue)"
}

$list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/chats/$ChatId/export/jobs?limit=5&status=queued" -Headers $hdr
if ($list.jobs -and @($list.jobs).Count -gt 0) {
    Write-Host "[OK] queued export job(s): $($list.job_count)" -ForegroundColor Green
} else {
    Write-Host "[WARN] no queued jobs in admin list (job may have finished quickly)" -ForegroundColor Yellow
}
Write-Host "[OK] NATS export.suggested -> auto-queue" -ForegroundColor Green
