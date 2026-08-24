#Requires -Version 5.1
param(
    [string] $ManifestPath = '',
    [switch] $SkipUiTest
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    Write-Host '=== Desktop OFFLINE gate (no live server) ===' -ForegroundColor Cyan

    & "$PSScriptRoot\verify-desktop-web-parity.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-profiles.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-multi-server.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-vpn.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-branding.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  $fixture = if ($ManifestPath) { $ManifestPath } else {
        Join-Path $repo 'specs\031-desktop-java-client\fixtures\update-manifest-stable.json'
    }
    & "$PSScriptRoot\smoke-desktop-update-manifest.ps1" -ManifestPath $fixture
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-security.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & "$PSScriptRoot\smoke-desktop-full-parity.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if (-not $SkipUiTest) {
        & "$PSScriptRoot\run-desktop-ui-tests.ps1"
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    Write-Host ''
    Write-Host 'PASS smoke-desktop-offline (full offline gate)' -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
