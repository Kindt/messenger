#Requires -Version 5.1
# VPP media wave — turn + voice (spec 030, mandatory in full run).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-vpp-media-wave.ps1"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$steps = @(
    @{ name = "turn-qemu"; script = "scripts\smoke-turn-qemu.ps1" },
    @{ name = "turn-relay"; script = "scripts\smoke-turn-relay.ps1" },
    @{ name = "voice-message"; script = "scripts\smoke-voice-message.ps1"; args = @{ BaseUrl = $ApiBaseUrl } }
)

foreach ($s in $steps) {
    Write-Host ""
    Write-Host "=== media: $($s.name) ===" -ForegroundColor Cyan
    $path = Join-Path $Root $s.script
    if (-not (Test-Path $path)) { throw "missing $($s.script)" }
    if ($s.args) { & $path @s.args } else { & $path }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "[OK] $($s.name)" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] media wave" -ForegroundColor Green
