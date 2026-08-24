#Requires -Version 5.1
param([switch] $Strict)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    & .\gradlew.bat :modules:desktop-client-sdk:test --tests "*ProfileStoreTest" --tests "*ProfileSettingsStoreTest" --no-configuration-cache -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'PASS smoke-desktop-profiles (ProfileStore + settings offline)'
    exit 0
}
finally {
    Pop-Location
}
