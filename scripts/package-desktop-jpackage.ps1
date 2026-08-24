#Requires -Version 5.1
<#
.SYNOPSIS
  Stage desktop distribution (offline). Uses Gradle installDist + desktop-dist marker.
#>
param(
    [ValidateSet('windows-x64', 'linux-x64', 'macos-x64', 'macos-aarch64')]
    [string] $Platform = 'windows-x64'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    Write-Host "package-desktop-jpackage: Platform=$Platform" -ForegroundColor Cyan
    & .\gradlew.bat :modules:desktop-client:jpackage --no-configuration-cache -q
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "PASS package-desktop-jpackage -> build/desktop-dist/" -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
