# Runs export compliance smokes in sequence. ChatId optional with -CreateGroup (seeds a new chat).
param(
    [string]$ChatId = "",
    [switch]$CreateGroup,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$WorkerMetricsUrl = "http://localhost:9193/metrics",
    [switch]$SkipSuggestCancel,
    [switch]$SkipRequestCancel,
    [switch]$SkipWorkerMetrics,
    [switch]$SkipObservability,
    [switch]$SkipGlobalJobs,
    [switch]$SkipSuggestedNats,
    [switch]$SkipRetentionSuggested,
    [switch]$SkipAutoQueueNats,
    [string]$RetentionMetricsUrl = "http://localhost:9192/metrics",
    [switch]$SkipAudit,
    [switch]$SkipDownload,
    [switch]$SkipOpenApi
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$auditArg = @{}
if ($SkipAudit) { $auditArg["SkipAudit"] = $true }

function Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
        throw "Step failed: $Name (exit $LASTEXITCODE)"
    }
}

if (-not $SkipOpenApi) {
    Step "OpenAPI export-compliance-prep" {
        & "$scriptDir\smoke-openapi-export-compliance.ps1" -BaseUrl $BaseUrl
    }
}

if (-not $ChatId) {
    Step "seed compliance chat (export-compliance-prep)" {
        $prepOut = & "$scriptDir\smoke-admin-export-compliance-prep.ps1" -BaseUrl $BaseUrl
        $line = $prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1
        if ($line) {
            $ChatId = ($line -replace '^CHAT_ID=', '').Trim()
        }
        if (-not $ChatId) {
            $ChatId = ($prepOut | Select-Object -Last 1).ToString().Trim()
        }
        if (-not $ChatId) { throw "prep did not return chat id" }
        Write-Host "Waiting 2s (retention SELECT age buffer) ..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 2
        Write-Host "Using chat $ChatId" -ForegroundColor Green
    }
}

if (-not $SkipSuggestCancel) {
    Step "suggest -> export -> cancel" {
        & "$scriptDir\smoke-export-suggest-cancel-flow.ps1" -ChatId $ChatId -BaseUrl $BaseUrl @auditArg
    }
}

if (-not $SkipRequestCancel) {
    Step "admin request -> cancel" {
        & "$scriptDir\smoke-admin-export-request-cancel.ps1" -ChatId $ChatId -BaseUrl $BaseUrl @auditArg
    }
}

if (-not $SkipSuggestedNats) {
    Step "NATS export.suggested -> audit" {
        & "$scriptDir\smoke-export-suggested-nats.ps1" -ChatId $ChatId -BaseUrl $BaseUrl
    }
}

if (-not $SkipRetentionSuggested) {
    Step "retention export.suggested -> audit" {
        & "$scriptDir\smoke-retention-export-suggested.ps1" -ChatId $ChatId -BaseUrl $BaseUrl `
            -RetentionMetricsUrl $RetentionMetricsUrl
    }
}

if (-not $SkipAutoQueueNats) {
    Step "NATS export.suggested -> auto-queue" {
        & "$scriptDir\smoke-export-auto-queue-nats.ps1" -ChatId $ChatId -BaseUrl $BaseUrl -SkipAudit:$SkipAudit
    }
}

if (-not $SkipGlobalJobs) {
    Step "admin global export jobs" {
        & "$scriptDir\smoke-admin-export-global-jobs.ps1" -ChatId $ChatId -BaseUrl $BaseUrl -Limit 20
    }
}

if (-not $SkipObservability) {
    Step "prometheus export metrics" {
        & "$scriptDir\smoke-export-observability.ps1"
    }
}

if (-not $SkipDownload) {
    Step "compliance flow + bundle download (with file)" {
        & "$scriptDir\smoke-export-compliance-flow.ps1" -ChatId $ChatId -BaseUrl $BaseUrl -SkipPrep -IncludeFile
    }
}

if (-not $SkipWorkerMetrics) {
    Step "worker metrics" {
        $args = @{
            WorkerMetricsUrl = $WorkerMetricsUrl
            BaseUrl          = $BaseUrl
            ChatId           = $ChatId
        }
        if ($SkipAudit) { $args["SkipCancelFlow"] = $true }
        & "$scriptDir\smoke-export-worker-metrics.ps1" @args
    }
}

Write-Host ""
Write-Host "[OK] export compliance pack finished (chat $ChatId)" -ForegroundColor Green
