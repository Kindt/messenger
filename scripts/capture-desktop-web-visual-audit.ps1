#Requires -Version 5.1
<#
.SYNOPSIS
  Capture web + desktop screenshots for visual parity audit (spec 031).
.EXAMPLE
  .\scripts\capture-desktop-web-visual-audit.ps1
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$outRoot = Join-Path $repo 'deploy\desktop\run\visual-audit'
$webOut = Join-Path $outRoot 'web'
$deskOut = Join-Path $outRoot 'desktop'
New-Item -ItemType Directory -Path $webOut, $deskOut -Force | Out-Null

Push-Location $repo
try {
    try {
        $api = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/v1/health' -UseBasicParsing -TimeoutSec 5
        $web = Invoke-WebRequest -Uri 'http://127.0.0.1:19088/' -UseBasicParsing -TimeoutSec 5
        Write-Host "Stack OK: API $($api.StatusCode) WEB $($web.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host 'Stack not ready — starting QEMU (KeepDisks)...' -ForegroundColor Yellow
        & .\scripts\qemu-up.ps1 -KeepDisks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & .\scripts\Wait-KorusLabStackReady.ps1
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    Write-Host '=== Web screenshots (Playwright) ===' -ForegroundColor Cyan
    Push-Location (Join-Path $repo 'tests\e2e-web')
    $env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:19088'
    $env:KORUS_API_URL = 'http://127.0.0.1:18080'
    npx playwright test specs/desktop-parity-visual-audit.spec.ts --reporter=line
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Pop-Location

    Write-Host '=== Desktop screenshots (TestFX) ===' -ForegroundColor Cyan
    $env:KORUS_DESKTOP_VISUAL_OUT = $deskOut
    & .\gradlew.bat :modules:desktop-client:visualCapture -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "PASS visual audit -> $outRoot" -ForegroundColor Green
    Get-ChildItem $outRoot -Recurse -Filter '*.png' | ForEach-Object { Write-Host "  $($_.FullName)" }
    exit 0
}
finally {
    Pop-Location
}
