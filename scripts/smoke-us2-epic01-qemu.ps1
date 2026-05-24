param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [int]$ServerSshPort = 12221,
    [int]$LocalRetentionPort = 19192,
    [int]$LocalExportReplayPort = 19193,
    [int]$LocalNatsPort = 14222,
    [switch]$LenientObservability,
    [switch]$SkipHotPlug
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
$serverSerial = Join-Path (Join-Path $scriptDir "..\deploy\qemu\run") "server-serial.log"

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $plink)) {
    Fail "plink not found: $plink"
}
if (-not (Test-Path $serverSerial)) {
    Fail "server serial log not found: $serverSerial"
}

$m = Select-String -Path $serverSerial -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" | Select-Object -Last 1
if (-not $m) {
    Fail "Could not extract server ED25519 host key fingerprint from serial log."
}
$hostKey = "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)"

$retMetrics = "http://127.0.0.1:$LocalRetentionPort/metrics"
$expMetrics = "http://127.0.0.1:$LocalExportReplayPort/metrics"
$coreMetrics = "$($ApiBaseUrl.TrimEnd('/'))/api/v1/metrics/prometheus"

Write-Host "Starting SSH tunnels for QEMU metrics..." -ForegroundColor Cyan
$argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $ServerSshPort " +
    "-L ${LocalRetentionPort}:127.0.0.1:9192 -L ${LocalExportReplayPort}:127.0.0.1:9193 -L ${LocalNatsPort}:127.0.0.1:4222 korus@127.0.0.1"
$proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden

try {
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        Fail "SSH tunnel process exited early (code=$($proc.ExitCode))."
    }

    Write-Host "Running US2 smoke against QEMU..." -ForegroundColor Cyan
    $smokeArgs = @{
        BaseUrl             = $ApiBaseUrl
        CoreMetricsUrl      = $coreMetrics
        WorkerMetricsUrl    = $expMetrics
        RetentionMetricsUrl = $retMetrics
        NatsUrl             = "nats://127.0.0.1:$LocalNatsPort"
    }
    if ($SkipHotPlug) { $smokeArgs["SkipHotPlug"] = $true }
    if ($LenientObservability) { $smokeArgs["LenientObservability"] = $true }
    & (Join-Path $scriptDir "smoke-us2-epic01.ps1") @smokeArgs
    if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
        Fail "smoke-us2-epic01.ps1 failed with exit code $LASTEXITCODE"
    }
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "[OK] US2 QEMU smoke completed." -ForegroundColor Green
