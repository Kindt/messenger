param(
    [string]$RepoRoot = ".",
    [string]$NatsUrl = "nats://localhost:4222",
    [int]$ServicePort = 19090,
    [string]$ServiceId = "indexer-smoke",
    [int]$HeartbeatIntervalMs = 2000,
    [int]$DrainTimeoutMs = 5000,
    [int]$StartTimeoutSec = 90
)

$ErrorActionPreference = "Stop"

function Stop-ProcessTree {
    param([int]$RootPid)
    if ($RootPid -le 0) { return }
    & taskkill.exe /PID $RootPid /T /F 2>$null | Out-Null
}

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Wait-Until([scriptblock]$Condition, [int]$TimeoutSec, [string]$Description) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 500
    }
    Fail "Timeout waiting for: $Description"
}

function Get-UrlHostPort([string]$Url) {
    try {
        $uri = [System.Uri]$Url
        return @{ Host = $uri.Host; Port = $uri.Port }
    } catch {
        Fail "Invalid NATS URL: $Url"
    }
}

function Test-TcpPort([string]$HostName, [int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(1500, $false)
        if (-not $ok) { return $false }
        $client.EndConnect($iar) | Out-Null
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-Http([string]$Url) {
    try {
        return Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec 3
    } catch {
        return $null
    }
}

$root = Resolve-Path $RepoRoot
$nats = Get-UrlHostPort $NatsUrl

Write-Host "Checking NATS connectivity: $($nats.Host):$($nats.Port)" -ForegroundColor Cyan
if (-not (Test-TcpPort -HostName $nats.Host -Port $nats.Port)) {
    if ($NatsUrl -eq "nats://localhost:4222") {
        $tunnelScript = Join-Path $PSScriptRoot "lib\Ensure-NatsQemuTunnel.ps1"
        if (Test-Path $tunnelScript) {
            . $tunnelScript
            $NatsUrl = Ensure-NatsQemuTunnel
            $nats = Get-UrlHostPort $NatsUrl
        }
    }
}
if (-not (Test-TcpPort -HostName $nats.Host -Port $nats.Port)) {
    Fail @"
NATS is not reachable at $NatsUrl.
For QEMU: start SSH tunnel first, e.g.
  plink -N -batch -pw korus -P 12221 -L 14222:127.0.0.1:4222 korus@127.0.0.1
Then run with -NatsUrl nats://127.0.0.1:14222
"@
}

Push-Location $root
$proc = $null
try {
    $env:NATS_URL = $NatsUrl
    $env:INDEXER_METRICS_PORT = "$ServicePort"
    $env:SERVICE_ID = $ServiceId
    $env:SERVICE_HEARTBEAT_INTERVAL_MS = "$HeartbeatIntervalMs"
    $env:SERVICE_DRAIN_TIMEOUT_MS = "$DrainTimeoutMs"

    Write-Host "Starting services:indexer run..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath ".\gradlew.bat" -ArgumentList ":services:indexer:run" -PassThru
    if (-not $proc) { Fail "Unable to start indexer service process" }

    Wait-Until -TimeoutSec $StartTimeoutSec -Description "indexer /health = 200" -Condition {
        $r = Get-Http "http://127.0.0.1:$ServicePort/health"
        return $r -and $r.StatusCode -eq 200
    }

    Wait-Until -TimeoutSec $StartTimeoutSec -Description "hotplug heartbeat metric present" -Condition {
        $r = Get-Http "http://127.0.0.1:$ServicePort/metrics"
        if (-not $r -or $r.StatusCode -ne 200) { return $false }
        $body = $r.Content
        return $body -match "hotplug_heartbeat_publish_total" -and $body -match "service_id=`"$ServiceId`""
    }

    Write-Host "Stopping indexer service..." -ForegroundColor Cyan
    Stop-ProcessTree -RootPid $proc.Id
    Wait-Until -TimeoutSec 15 -Description "indexer process exit" -Condition {
        $proc.HasExited -or -not (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue)
    }

    Write-Host "[OK] hot-plug indexer smoke passed" -ForegroundColor Green
} finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-ProcessTree -RootPid $proc.Id
    }
    Pop-Location
}
