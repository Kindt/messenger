# Up full stack with export overlays, run compliance pack, optional down.
# QEMU lab (no host Docker): use smoke-export-compliance-stack-qemu.ps1 instead.
param(
    [string]$ChatId = "",
    [switch]$Build,
    [switch]$AutoQueue,
    [switch]$Down,
    [switch]$SkipEnsure
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$upArgs = @{ ExportSmoke = $true; WaitReady = $true }
if ($Build) { $upArgs["Build"] = $true }
if ($AutoQueue) { $upArgs["ExportAutoQueue"] = $true }
if ($SkipEnsure) { $upArgs["SkipEnsure"] = $true }
& "$scriptDir\full-stack-up.ps1" @upArgs

$packArgs = @{}
if ($ChatId) { $packArgs["ChatId"] = $ChatId }
& "$scriptDir\smoke-export-compliance-pack.ps1" @packArgs

if ($Down) {
    $downArgs = @{ ExportSmoke = $true }
    if ($AutoQueue) { $downArgs["ExportAutoQueue"] = $true }
    & "$scriptDir\full-stack-down.ps1" @downArgs
}
