# Desktop JavaFX UI tests (TestFX, headed click-through)
# Requires display (Windows desktop session). Demo mode — no QEMU server.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "=== Desktop UI tests (TestFX, demo mode) ===" -ForegroundColor Cyan

& .\gradlew.bat :modules:desktop-client:uiTest --no-daemon --no-configuration-cache
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "PASS desktop-ui-tests (headed TestFX)" -ForegroundColor Green
Write-Host "Report: modules\desktop-client\build\reports\tests\uiTest\index.html"
