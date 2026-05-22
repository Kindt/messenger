# E2E: publish msg.export.suggested via NATS CLI, verify core-api audit export.suggested.
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$NatsUrl = "",
    [int]$PollSeconds = 30,
    [int]$PollIntervalSec = 1
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

& "$scriptDir\publish-export-suggested.ps1" -ChatId $ChatId -NatsUrl $NatsUrl

$deadline = (Get-Date).AddSeconds($PollSeconds)
while ((Get-Date) -lt $deadline) {
    & "$scriptDir\smoke-export-suggested.ps1" -BaseUrl $BaseUrl -ChatId $ChatId -Limit 5
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] NATS export.suggested -> audit" -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Seconds $PollIntervalSec
}
throw "Timed out waiting for export.suggested audit (chat $ChatId)"
