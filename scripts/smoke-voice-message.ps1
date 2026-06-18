# Smoke voice message API (spec 022). QEMU guest or forwarded :18080.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$sh = Join-Path $scriptDir "smoke-voice-message.sh"
if (-not (Test-Path $sh)) {
    Write-Host "[SKIP] smoke-voice-message.sh not found"
    exit 0
}
$env:BASE_URL = $BaseUrl
& bash $sh
exit $LASTEXITCODE
