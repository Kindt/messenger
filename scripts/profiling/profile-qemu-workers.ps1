# Profile retention + export-replay workers on QEMU via SSH tunnels and Prometheus heap metrics.
# Example: .\scripts\profiling\profile-qemu-workers.ps1 -ApiBaseUrl http://127.0.0.1:18080
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [int]$ServerSshPort = 12221,
    [int]$LocalRetentionPort = 19192,
    [int]$LocalExportPort = 19193,
    [int]$ArchiveMessageCount = 3,
    [int]$ExportJobCount = 2
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent (Split-Path -Parent $scriptDir)
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
$serverSerial = Join-Path $rootDir "deploy\qemu\run\server-serial.log"
$measure = Join-Path $scriptDir "measure-prometheus-heap.ps1"

function Resolve-HostKey {
    $m = Select-String -Path $serverSerial -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" | Select-Object -Last 1
    if (-not $m) { throw "Could not extract server host key from $serverSerial" }
    return "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)"
}

if (-not (Test-Path $plink)) { throw "plink not found: $plink" }
$hostKey = Resolve-HostKey
$argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $ServerSshPort " +
    "-L ${LocalRetentionPort}:127.0.0.1:9192 -L ${LocalExportPort}:127.0.0.1:9193 korus@127.0.0.1"
$tunnel = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 2
if ($tunnel.HasExited) { throw "SSH tunnel failed (exit $($tunnel.ExitCode))" }

try {
    $retMetrics = "http://127.0.0.1:$LocalRetentionPort/metrics"
    $expMetrics = "http://127.0.0.1:$LocalExportPort/metrics"

    Write-Host "=== Worker baseline heap ===" -ForegroundColor Cyan
    $retIdle = & $measure -MetricsUrl $retMetrics -Label "retention-idle" -Samples 2 -IntervalSec 1
    $expIdle = & $measure -MetricsUrl $expMetrics -Label "export-replay-idle" -Samples 2 -IntervalSec 1

    Write-Host "=== Load: archive TTL messages (retention + deep-archiver) ===" -ForegroundColor Cyan
    $login = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) -ContentType "application/json"
    $token = $login.access_token
    if (-not $token) { $token = $login.accessToken }
    $headers = @{ Authorization = "Bearer $token" }
    $chats = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/chats" -Headers $headers
    $chatId = $chats[0].id
    for ($i = 0; $i -lt $ArchiveMessageCount; $i++) {
        $payload = ("y" * 8192) + " worker-profile-$i"
        $body = @{ type = "text"; content = $payload; archive_ttl_seconds = 2 } | ConvertTo-Json
        Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/chats/$chatId/messages" -Method Post -Headers $headers `
            -Body $body -ContentType "application/json" | Out-Null
    }
    Write-Host "Sent $ArchiveMessageCount archive messages; waiting 20s for workers..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 20

    Write-Host "=== Load: export jobs (export-replay) ===" -ForegroundColor Cyan
    $exportOk = $false
    try {
        for ($i = 0; $i -lt $ExportJobCount; $i++) {
            $accepted = Invoke-WebRequest -Uri "$ApiBaseUrl/api/v1/chats/$chatId/export" -Method Post -Headers $headers `
                -ContentType "application/json" -UseBasicParsing
            $job = $accepted.Content | ConvertFrom-Json
            $jobId = $job.job_id
            if (-not $jobId) { $jobId = $job.id }
            $deadline = (Get-Date).AddSeconds(90)
            $status = "unknown"
            while ((Get-Date) -lt $deadline) {
                $st = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/chats/$chatId/export/$jobId" -Headers $headers
                $status = $st.status
                if ($status -in @("export_v1", "stub_written", "export_failed", "export_cancelled")) { break }
                Start-Sleep -Seconds 2
            }
            Write-Host "  export job $jobId -> $status" -ForegroundColor DarkGray
        }
        $exportOk = $true
    } catch {
        Write-Host "[WARN] export load skipped: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    Write-Host "=== Worker post-load heap ===" -ForegroundColor Cyan
    $retLoad = & $measure -MetricsUrl $retMetrics -Label "retention-post-load" -Samples 3 -IntervalSec 2
    $expLoad = & $measure -MetricsUrl $expMetrics -Label "export-replay-post-load" -Samples 3 -IntervalSec 2

    Write-Host "[OK] retention idle=$([math]::Round($retIdle,1))MB post-load=$([math]::Round($retLoad,1))MB" -ForegroundColor Green
    Write-Host "[OK] export-replay idle=$([math]::Round($expIdle,1))MB post-load=$([math]::Round($expLoad,1))MB" -ForegroundColor Green
}
finally {
    if ($tunnel -and -not $tunnel.HasExited) {
        Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
    }
}
