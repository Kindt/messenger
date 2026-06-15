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
$Root = Split-Path -Parent $scriptDir
$RunDir = Join-Path $Root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
$serverSerial = Join-Path $RunDir "server-serial.log"

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $plink)) {
    Fail "plink not found: $plink"
}

$hostKey = Get-KorusEd25519HostKey -SerialPath $serverSerial -Role server -SshPort $ServerSshPort
if (-not $hostKey) {
    Fail "Could not resolve server SSH host key (serial log or ssh-hostkeys.ps1 cache)."
}

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
