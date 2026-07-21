param(
    [switch]$AutoUp,
    [switch]$AutoDown,
    [switch]$SkipHotPlug,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$CoreMetricsUrl = "",
    [string]$WorkerMetricsUrl = "http://localhost:9193/metrics",
    [string]$RetentionMetricsUrl = "http://localhost:9192/metrics",
    [string]$NatsUrl = "nats://localhost:4222",
    [switch]$LenientObservability
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\Resolve-QemuLabWorkerMetrics.ps1")

if ($BaseUrl -match ':18080') {
    $resolved = Resolve-QemuLabWorkerMetrics -ApiBaseUrl $BaseUrl `
        -CoreMetricsUrl $CoreMetricsUrl -WorkerMetricsUrl $WorkerMetricsUrl -RetentionMetricsUrl $RetentionMetricsUrl
    $CoreMetricsUrl = $resolved.CoreMetricsUrl
    $WorkerMetricsUrl = $resolved.WorkerMetricsUrl
    $RetentionMetricsUrl = $resolved.RetentionMetricsUrl
    . (Join-Path $scriptDir "lib\Ensure-NatsQemuTunnel.ps1")
    $NatsUrl = Ensure-NatsQemuTunnel
} elseif (-not $CoreMetricsUrl) {
    $CoreMetricsUrl = "$($BaseUrl.TrimEnd('/'))/api/v1/metrics/prometheus"
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Step([string]$title, [scriptblock]$action) {
    Write-Host ""
    Write-Host "== $title ==" -ForegroundColor Cyan
    & $action
    Write-Host "[OK] $title" -ForegroundColor Green
}

function Has-Command([string]$name) {
    try {
        $null = Get-Command $name -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

function Run-ScriptChecked([string]$path, [hashtable]$params = @{}) {
    if (-not (Test-Path $path)) {
        Fail "Script not found: $path"
    }
    & $path @params
    if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
        Fail "Script failed with exit code ${LASTEXITCODE}: $path"
    }
}

if ($AutoUp -and -not (Has-Command "docker")) {
    Fail "docker CLI is not available. Install/start Docker Desktop or run without -AutoUp on an already started stack."
}

if ($AutoUp) {
    Step "Bring up export/retention smoke stack" {
        Run-ScriptChecked (Join-Path $scriptDir "full-stack-up.ps1") @{
            ExportSmoke = $true
            WaitReady = $true
            SkipEnsure = $true
        }
    }
}

try {
    if ($LenientObservability) {
        try {
            Step "T024: Prometheus metrics verification" {
                Run-ScriptChecked (Join-Path $scriptDir "smoke-export-observability.ps1") @{
                    CoreMetricsUrl      = $CoreMetricsUrl
                    WorkerMetricsUrl    = $WorkerMetricsUrl
                    RetentionMetricsUrl = $RetentionMetricsUrl
                }
            }
        } catch {
            Write-Host "[WARN] T024 observability smoke failed in lenient mode: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    } else {
        Step "T024: Prometheus metrics verification" {
            Run-ScriptChecked (Join-Path $scriptDir "smoke-export-observability.ps1") @{
                CoreMetricsUrl      = $CoreMetricsUrl
                WorkerMetricsUrl    = $WorkerMetricsUrl
                RetentionMetricsUrl = $RetentionMetricsUrl
            }
        }
    }

    Step "Retention worker readiness / metrics" {
        $retBase = $RetentionMetricsUrl -replace '/metrics$', ''
        Run-ScriptChecked (Join-Path $scriptDir "smoke-retention-worker.ps1") @{
            BaseUrl = $retBase
        }
    }

    if (-not $SkipHotPlug) {
        Step "Hot-plug indexer lifecycle smoke" {
            Run-ScriptChecked (Join-Path $scriptDir "smoke-hotplug-indexer.ps1") @{
                NatsUrl = $NatsUrl
            }
        }
    } else {
        Write-Host ""
        Write-Host "[SKIP] Hot-plug smoke skipped by flag" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "US2 smoke runner completed." -ForegroundColor Green
    Write-Host "Manual checks still required for strict T023/T025/T026 assertions (message-level Solr/MinIO/file-ref verification)." -ForegroundColor DarkGray
}
finally {
    if ($AutoDown) {
        if (-not (Has-Command "docker")) {
            Write-Host "[WARN] docker CLI missing, skip AutoDown" -ForegroundColor Yellow
        } else {
            Step "Bring down export/retention smoke stack" {
                Run-ScriptChecked (Join-Path $scriptDir "full-stack-down.ps1") @{
                    ExportSmoke = $true
                }
            }
        }
    }
}
