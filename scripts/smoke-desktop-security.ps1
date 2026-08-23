# Smoke: desktop security controls (offline)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host '=== Desktop security smoke ===' -ForegroundColor Cyan

$env:KORUS_DESKTOP_TEST_MASTER_KEY = 'smoke-test-key-32bytes-padding!!'

.\gradlew.bat :modules:desktop-client-sdk:test --tests "com.avandocmsg.messenger.desktop.sdk.UpdateServiceTest.aesGcmRoundTrip" --tests "com.avandocmsg.messenger.desktop.sdk.UpdateServiceTest.platformSecureTokenStorePersistsEncrypted" --tests "com.avandocmsg.messenger.desktop.sdk.UpdateServiceTest.securitySelfCheckMaximumGrade" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'security unit tests failed' }

$matrix = Join-Path $PWD 'docs\desktop\FSTEC_SECURITY_MATRIX.md'
if (-not (Test-Path $matrix)) { throw "missing $matrix" }

Write-Host "PASS smoke-desktop-security (matrix + SDK security tests)" -ForegroundColor Green
