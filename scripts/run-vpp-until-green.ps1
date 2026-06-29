#Requires -Version 5.1
# Canonical entry: VPP until full GREEN (spec 030).
param(
    [ValidateSet('quick', 'standard', 'full')]
    [string]$Level = 'full',
    [int]$MaxAttempts = 10,
    [switch]$PauseOnFail,
    [switch]$SkipBuild,
    [switch]$SkipIntegrations,
    [switch]$SkipPlaywright,
    [switch]$SkipLoad,
    [string]$ResumeCheckpoint = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $Root "scripts\run-vpp-full.ps1"

if ($Help) {
    & $runner -Help
    exit 0
}

$runnerArgs = @{
    Level = $Level; UntilGreen = $true; MaxAttempts = $MaxAttempts
    PauseOnFail = $PauseOnFail; SkipBuild = $SkipBuild; SkipIntegrations = $SkipIntegrations
    SkipPlaywright = $SkipPlaywright; SkipLoad = $SkipLoad
}
if ($ResumeCheckpoint) { $runnerArgs['ResumeCheckpoint'] = $ResumeCheckpoint }
& $runner @runnerArgs
exit $LASTEXITCODE
