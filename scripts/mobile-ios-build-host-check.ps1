#Requires -Version 5.1
param([switch]$Help)
$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
Usage: .\scripts\mobile-ios-build-host-check.ps1

iOS native builds require macOS + Xcode (not available inside Ubuntu QEMU on Windows).
Run this on a Mac dev machine to verify toolchain before mobile-client-ios scaffold.
"@
    exit 0
}

if ($IsWindows -or $env:OS -match 'Windows') {
    Write-Host "[INFO] Windows host: iOS builds are NOT supported in korus-mobile-build VM." -ForegroundColor Yellow
    Write-Host "  Android: .\scripts\qemu-mobile-build-up.ps1" -ForegroundColor Cyan
    Write-Host "  iOS: use a physical Mac with Xcode 15+ (see specs/032-mobile-native-client/design/ios-build-host.md)" -ForegroundColor Cyan
    exit 2
}

$missing = @()
if (-not (Get-Command xcodebuild -ErrorAction SilentlyContinue)) { $missing += "xcodebuild" }
if (-not (Get-Command xcrun -ErrorAction SilentlyContinue)) { $missing += "xcrun" }
if ($missing.Count -gt 0) {
    Write-Host "[FAIL] Missing: $($missing -join ', '). Install Xcode from App Store." -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Xcode toolchain present" -ForegroundColor Green
xcodebuild -version
Write-Host "When mobile-client-ios exists: xcodebuild -scheme KorusMobile -destination 'platform=iOS Simulator,name=iPhone 15'" -ForegroundColor DarkGray
