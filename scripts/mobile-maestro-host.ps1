# Maestro W0 on host Android Studio AVD (WHPX) — fast path when guest TCG is too slow.
param(
    [switch]$SkipBuild,
    [switch]$NoLaunchEmulator,
    [switch]$NoStartStack,
    [string]$Avd = 'korus_host_api28',
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

if ($Help) {
    Write-Host @"
Usage: .\scripts\mobile-maestro-host.ps1

Runs mobile/maestro/w0-login.yaml on host AVD (WHPX). Requires lab API :18080.
Installs Maestro CLI to %USERPROFILE%\.maestro if missing.
"@
    exit 0
}

$splat = @{ RunMaestro = $true }
if ($SkipBuild) { $splat['SkipBuild'] = $true }
if ($NoLaunchEmulator) { $splat['NoLaunchEmulator'] = $true }
if ($NoStartStack) { $splat['NoStartStack'] = $true }
if ($Avd) { $splat['Avd'] = $Avd }
& (Join-Path $Root 'scripts\mobile-android-studio.ps1') @splat
exit $LASTEXITCODE
