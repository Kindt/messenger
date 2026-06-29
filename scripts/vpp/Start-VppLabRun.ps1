#Requires -Version 5.1
# Start VPP full until-green + monitored ticks (spec 030). Prefer Start-VppMonitoredLabRun.ps1.
param(
    [ValidateSet('full', 'standard')]
    [string]$Level = 'full',
    [int]$MaxAttempts = 10,
    [int]$TickSec = 0,
    [switch]$SkipStackPrep,
    [switch]$NoStop,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$monitored = Join-Path $Root 'scripts\vpp\Start-VppMonitoredLabRun.ps1'

if ($Help) {
    & $monitored -Help
    exit 0
}

& $monitored -Level $Level -MaxAttempts $MaxAttempts -TickSec $TickSec -SkipStackPrep:$SkipStackPrep -NoStop:$NoStop
exit $LASTEXITCODE
