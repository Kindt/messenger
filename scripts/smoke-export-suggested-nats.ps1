# E2E: publish msg.export.suggested via NATS CLI, verify core-api audit export.suggested.
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$NatsUrl = "",
    [int]$PollSeconds = 30,
    [int]$PollIntervalSec = 1
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "lib\Ensure-NatsQemuTunnel.ps1")

if (-not $ChatId) {
    $prepOut = & "$scriptDir\smoke-admin-export-compliance-prep.ps1" -BaseUrl $BaseUrl
    $line = @($prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1)
    if ($line) { $ChatId = ($line -replace '^CHAT_ID=', '').Trim() }
    if (-not $ChatId) { throw "Could not resolve ChatId (pass -ChatId or run compliance prep)" }
}

if (-not $NatsUrl) {
    if ($BaseUrl -match ':18080|127\.0\.0\.1') {
        $NatsUrl = Ensure-NatsQemuTunnel
    } else {
        $NatsUrl = if ($env:NATS_URL) { $env:NATS_URL } else { "nats://127.0.0.1:4222" }
    }
}

& "$scriptDir\publish-export-suggested.ps1" -ChatId $ChatId -NatsUrl $NatsUrl

$deadline = (Get-Date).AddSeconds($PollSeconds)
while ((Get-Date) -lt $deadline) {
    try {
        & "$scriptDir\smoke-export-suggested.ps1" -BaseUrl $BaseUrl -ChatId $ChatId -Limit 5
        if ($? -and ($null -eq $LASTEXITCODE -or $LASTEXITCODE -eq 0)) {
            Write-Host "[OK] NATS export.suggested -> audit" -ForegroundColor Green
            exit 0
        }
    } catch {
        # keep polling until deadline
    }
    Start-Sleep -Seconds $PollIntervalSec
}
throw "Timed out waiting for export.suggested audit (chat $ChatId)"
